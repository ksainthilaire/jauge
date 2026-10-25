import { type Database, queryOne, toIsoTimestampOrNull } from "../database/database.ts";
import type { Clock } from "../operations/clock.ts";
import { parisWeekStart } from "../operations/clock.ts";

/** Plan of a user at a given moment. */
export interface Plan {
  /** True while the entitlement `plus` is active (trial or paid). */
  readonly plus: boolean;
  readonly status: "free" | "trial" | "active" | "expired";
  readonly expiresAt: string | null;
  readonly trialEndsAt: string | null;
  readonly willRenew: boolean;
}

/**
 * Jauge Plus as known by the server (mirror of RevenueCat written by the webhook) and the free weekly quota.
 */
export class SubscriptionService {
  /**
   * @param database database.
   * @param clock clock.
   * @param freeAnalysesPerWeek free complete analyses per week.
   */
  constructor(
    private readonly database: Database,
    private readonly clock: Clock,
    private readonly freeAnalysesPerWeek: number,
  ) {}

  /** Plan of a user. A trial or a subscription past its expiry counts as expired. */
  async plan(userId: string, database: Database = this.database): Promise<Plan> {
    const row = await queryOne<Record<string, unknown>>(
      database,
      `select status, expires_at, trial_ends_at, will_renew from public.subscriptions where user_id = $1::uuid`,
      [userId],
    );
    if (!row) return { plus: false, status: "free", expiresAt: null, trialEndsAt: null, willRenew: false };
    const expiresAt = toIsoTimestampOrNull(row.expires_at);
    const stillValid = expiresAt === null || Date.parse(expiresAt) > this.clock.now().getTime();
    const storedStatus = row.status as Plan["status"];
    const plus = (storedStatus === "trial" || storedStatus === "active") && stillValid;
    const status = plus ? storedStatus : storedStatus === "free" ? "free" : "expired";
    return {
      plus,
      status,
      expiresAt,
      trialEndsAt: toIsoTimestampOrNull(row.trial_ends_at),
      willRenew: row.will_renew === true,
    };
  }

  /** Monday of the current quota week (Paris time). */
  currentWeek(): string {
    return parisWeekStart(this.clock.now());
  }

  /** Free analyses left this week. */
  async freeAnalysesLeft(userId: string): Promise<number> {
    const row = await queryOne<{ used: number }>(
      this.database,
      `select used from public.weekly_quotas where user_id = $1::uuid and week_start = $2::date`,
      [userId, this.currentWeek()],
    );
    return Math.max(0, this.freeAnalysesPerWeek - (row?.used ?? 0));
  }

  /**
   * Uses one free analysis of the week. Returns the week used, or null when none is left.
   *
   * @param database database or transaction.
   * @param userId user.
   */
  async consumeFreeAnalysis(database: Database, userId: string): Promise<string | null> {
    const week = this.currentWeek();
    const rows = await database.query<{ allowed: boolean }>(
      `select public.api_consume_weekly_quota($1::uuid, $2::date, $3) as allowed`,
      [userId, week, this.freeAnalysesPerWeek],
    );
    return rows[0]?.allowed ? week : null;
  }

  /** Gives back a free analysis (unreadable offer, unsupported link, failure of the server). */
  async refundFreeAnalysis(database: Database, userId: string, week: string): Promise<void> {
    await database.query(`select public.api_refund_weekly_quota($1::uuid, $2::date)`, [userId, week]);
  }

  /** Contract representation (`Subscription`). */
  async present(userId: string): Promise<Record<string, unknown>> {
    const plan = await this.plan(userId);
    return {
      status: plan.status,
      trialEndsOn: plan.status === "trial"
        ? plan.trialEndsAt?.slice(0, 10) ?? plan.expiresAt?.slice(0, 10) ?? null
        : null,
      renewsOn: plan.status === "active" && plan.willRenew ? plan.expiresAt?.slice(0, 10) ?? null : null,
      freeAnalysesLeftThisWeek: await this.freeAnalysesLeft(userId),
    };
  }
}
