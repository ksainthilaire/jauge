import { type Database, queryOne, toIsoTimestampOrNull } from "../database/database.ts";
import { validationFailed } from "../http/api_error.ts";
import { isObject } from "../http/validation.ts";
import type { Logger } from "../operations/logger.ts";

/** Entitlement of Jauge Plus in RevenueCat. */
export const PLUS_ENTITLEMENT = "plus";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Events that start or extend the entitlement. */
const GRANTING_EVENTS = new Set([
  "INITIAL_PURCHASE",
  "RENEWAL",
  "PRODUCT_CHANGE",
  "UNCANCELLATION",
  "NON_RENEWING_PURCHASE",
  "TEMPORARY_ENTITLEMENT_GRANT",
]);

/** Fields of a RevenueCat event used here. */
interface RevenueCatEvent {
  readonly id: string;
  readonly type: string;
  readonly appUserIds: string[];
  readonly transferredFrom: string[];
  readonly transferredTo: string[];
  readonly entitlementIds: string[] | null;
  readonly productId: string | null;
  readonly periodType: string | null;
  readonly expirationAt: string | null;
  readonly eventAt: string;
  readonly environment: string | null;
  readonly store: string | null;
}

function textList(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((entry): entry is string => typeof entry === "string") : [];
}

function millisecondsToIso(value: unknown): string | null {
  return typeof value === "number" && Number.isFinite(value) ? new Date(value).toISOString() : null;
}

/** Reads the event of a webhook body. */
export function parseRevenueCatEvent(body: unknown): RevenueCatEvent {
  if (!isObject(body) || !isObject(body.event)) throw validationFailed("Missing event");
  const event = body.event;
  if (typeof event.id !== "string" || typeof event.type !== "string") {
    throw validationFailed("Event without id or type");
  }
  const entitlements = event.entitlement_ids === null || event.entitlement_ids === undefined
    ? (typeof event.entitlement_id === "string" ? [event.entitlement_id] : null)
    : textList(event.entitlement_ids);
  return {
    id: event.id.slice(0, 200),
    type: event.type.slice(0, 60),
    appUserIds: [
      ...textList([event.app_user_id]),
      ...textList([event.original_app_user_id]),
      ...textList(event.aliases),
    ],
    transferredFrom: textList(event.transferred_from),
    transferredTo: textList(event.transferred_to),
    entitlementIds: entitlements,
    productId: typeof event.product_id === "string" ? event.product_id.slice(0, 200) : null,
    periodType: typeof event.period_type === "string" ? event.period_type : null,
    expirationAt: millisecondsToIso(event.expiration_at_ms),
    eventAt: millisecondsToIso(event.event_timestamp_ms) ?? new Date().toISOString(),
    environment: event.environment === "SANDBOX" || event.environment === "PRODUCTION" ? event.environment : null,
    store: typeof event.store === "string" ? event.store.slice(0, 40) : null,
  };
}

/**
 * Applies the RevenueCat webhooks to `subscriptions` (mirror of the entitlement `plus`). The app uses the Supabase
 * user identifier as RevenueCat app user id, so the user is the first identifier of the event that is a known
 * Supabase user. Events are applied once (`revenuecat_events`) and an event older than the last applied one never
 * overwrites it.
 */
export class RevenueCatWebhookService {
  /**
   * @param database database.
   * @param logger structured logger.
   */
  constructor(private readonly database: Database, private readonly logger: Logger) {}

  /** Applies one webhook body. Returns what happened, for the logs and the tests. */
  async handle(body: unknown): Promise<"applied" | "duplicate" | "ignored"> {
    const event = parseRevenueCatEvent(body);
    if (event.type === "TEST") return "ignored";
    if (
      event.entitlementIds !== null && event.entitlementIds.length > 0 &&
      !event.entitlementIds.includes(PLUS_ENTITLEMENT) &&
      event.type !== "TRANSFER"
    ) {
      return "ignored";
    }
    const userId = await this.knownUser(event.type === "TRANSFER" ? event.transferredTo : event.appUserIds);
    const inserted = await this.database.query(
      `insert into public.revenuecat_events (event_id, user_id, event_type, event_at) values ($1, $2::uuid, $3, $4::timestamptz)
       on conflict (event_id) do nothing returning event_id`,
      [event.id, userId, event.type, event.eventAt],
    );
    if (inserted.length === 0) return "duplicate";

    if (event.type === "TRANSFER") {
      const previousOwner = await this.knownUser(event.transferredFrom);
      if (previousOwner) {
        await this.write(previousOwner, event, { status: "expired", willRenew: false, expiresAt: event.eventAt });
      }
      if (userId) await this.write(userId, event, { status: "active", willRenew: true, expiresAt: null });
      this.logger.log("info", "revenuecat_event", { type: event.type, applied: true });
      return userId || previousOwner ? "applied" : "ignored";
    }
    if (!userId) {
      this.logger.log("info", "revenuecat_event", { type: event.type, applied: false, reason: "unknown_user" });
      return "ignored";
    }
    const current = await queryOne<{ status: string; last_event_at: unknown }>(
      this.database,
      `select status, last_event_at from public.subscriptions where user_id = $1::uuid`,
      [userId],
    );
    const lastEventAt = toIsoTimestampOrNull(current?.last_event_at);
    if (lastEventAt && Date.parse(lastEventAt) > Date.parse(event.eventAt)) {
      this.logger.log("info", "revenuecat_event", { type: event.type, applied: false, reason: "older_event" });
      return "ignored";
    }
    const trial = event.periodType === "TRIAL";
    if (GRANTING_EVENTS.has(event.type)) {
      await this.write(userId, event, {
        status: trial ? "trial" : "active",
        willRenew: event.type !== "NON_RENEWING_PURCHASE",
        expiresAt: event.expirationAt,
      });
    } else if (event.type === "CANCELLATION" || event.type === "SUBSCRIPTION_PAUSED") {
      // Still Jauge Plus until the end of the paid period.
      await this.write(userId, event, {
        status: current?.status === "trial" ? "trial" : "active",
        willRenew: false,
        expiresAt: event.expirationAt,
      });
    } else if (event.type === "BILLING_ISSUE") {
      await this.write(userId, event, {
        status: current?.status === "trial" ? "trial" : "active",
        willRenew: true,
        expiresAt: event.expirationAt,
      });
    } else if (event.type === "EXPIRATION") {
      await this.write(userId, event, {
        status: "expired",
        willRenew: false,
        expiresAt: event.expirationAt ?? event.eventAt,
      });
    } else {
      this.logger.log("info", "revenuecat_event", { type: event.type, applied: false, reason: "not_handled" });
      return "ignored";
    }
    this.logger.log("info", "revenuecat_event", { type: event.type, applied: true });
    return "applied";
  }

  private async knownUser(candidates: readonly string[]): Promise<string | null> {
    const identifiers = candidates.filter((candidate) => UUID_PATTERN.test(candidate)).map((candidate) =>
      candidate.toLowerCase()
    );
    if (identifiers.length === 0) return null;
    const row = await queryOne<{ id: string }>(
      this.database,
      `select id::text as id from auth.users where id = any (array(select jsonb_array_elements_text($1::jsonb))::uuid[]) limit 1`,
      [JSON.stringify(identifiers)],
    );
    return row?.id ?? null;
  }

  private async write(
    userId: string,
    event: RevenueCatEvent,
    values: { status: "trial" | "active" | "expired"; willRenew: boolean; expiresAt: string | null },
  ): Promise<void> {
    await this.database.query(
      `insert into public.subscriptions (user_id, entitlement, status, product_id, store, environment, expires_at,
         trial_ends_at, will_renew, last_event_id, last_event_at, updated_at)
       values ($1::uuid, 'plus', $2, $3, $4, $5, $6::timestamptz, $7::timestamptz, $8, $9, $10::timestamptz, now())
       on conflict (user_id) do update set
         status = excluded.status, product_id = coalesce(excluded.product_id, subscriptions.product_id),
         store = coalesce(excluded.store, subscriptions.store), environment = coalesce(excluded.environment, subscriptions.environment),
         expires_at = excluded.expires_at, trial_ends_at = excluded.trial_ends_at, will_renew = excluded.will_renew,
         last_event_id = excluded.last_event_id, last_event_at = excluded.last_event_at, updated_at = now()`,
      [
        userId,
        values.status,
        event.productId,
        event.store,
        event.environment,
        values.expiresAt,
        values.status === "trial" ? values.expiresAt : null,
        values.willRenew,
        event.id,
        event.eventAt,
      ],
    );
  }
}
