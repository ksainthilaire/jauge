import type { Configuration } from "../configuration.ts";
import { type Database, queryOne, toIsoTimestamp } from "../database/database.ts";
import { ApiError, notFound, validationFailed } from "../http/api_error.ts";
import { canonicalizeLink } from "../offers/offer_key.ts";
import type { Clock } from "../operations/clock.ts";
import type { Language } from "../scoring/methodology_texts.ts";
import { escapeLikePattern, searchWords } from "../text/search_text.ts";
import { type AnalysisRecord, findAnalysis, initialSteps, listAnalyses } from "./analysis_records.ts";
import type { SearchArea } from "./area.ts";
import type { SubscriptionService } from "./subscription_service.ts";

/** What the user asked to analyze. */
export type AnalysisInput =
  | { readonly kind: "link"; readonly url: string; readonly area: SearchArea | null }
  | { readonly kind: "manual"; readonly jobTitle: string; readonly city: string; readonly area: SearchArea | null }
  | {
    readonly kind: "text";
    readonly text: string;
    readonly origin: "screenshot" | "photo" | null;
    readonly area: SearchArea | null;
  }
  | {
    readonly kind: "image";
    readonly image: Uint8Array;
    readonly mimeType: string;
    readonly origin: "screenshot" | "photo";
    readonly area: SearchArea | null;
  };

/** Analyses started a second time within this delay with the same input give back the first one (double tap). */
const DUPLICATE_WINDOW_MILLISECONDS = 10 * 60 * 1000;
/** Idempotency keys are remembered this long. */
const IDEMPOTENCY_WINDOW_MILLISECONDS = 24 * 60 * 60 * 1000;
/** Shortest offer text worth reading. */
export const MINIMUM_OFFER_TEXT_LENGTH = 40;

/** Cursor of the list of analyses (opaque for clients). */
interface ListCursor {
  readonly createdAt: string;
  readonly id: string;
}

function encodeCursor(cursor: ListCursor): string {
  return btoa(JSON.stringify([cursor.createdAt, cursor.id])).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function decodeCursor(text: string | null): ListCursor | null {
  if (!text) return null;
  try {
    if (text.length > 200) throw new Error("too long");
    const decoded = JSON.parse(atob(text.replace(/-/g, "+").replace(/_/g, "/")));
    if (!Array.isArray(decoded) || decoded.length !== 2) throw new Error("shape");
    const [createdAt, id] = decoded;
    if (typeof createdAt !== "string" || Number.isNaN(Date.parse(createdAt))) throw new Error("date");
    if (typeof id !== "string" || !/^[0-9a-f-]{36}$/.test(id)) throw new Error("id");
    return { createdAt, id };
  } catch {
    throw validationFailed("cursor is not valid", 400);
  }
}

/**
 * Analyses of the users: creation (quota, idempotency, double taps, queue), reading, list, deletion, favorites.
 */
export class AnalysisService {
  /**
   * @param database database.
   * @param subscriptions plan and quota.
   * @param configuration configuration.
   * @param clock clock.
   */
  constructor(
    private readonly database: Database,
    private readonly subscriptions: SubscriptionService,
    private readonly configuration: Configuration,
    private readonly clock: Clock,
  ) {}

  /**
   * Starts an analysis. Answers the existing analysis for a repeated idempotency key or a double tap, without using
   * the quota again.
   *
   * @param userId user.
   * @param input what to analyze.
   * @param options idempotency key (header `Idempotency-Key`) and language of the user.
   */
  async create(
    userId: string,
    input: AnalysisInput,
    options: { idempotencyKey: string | null; language: Language },
  ): Promise<AnalysisRecord> {
    let canonicalUrl: string | null = null;
    if (input.kind === "link") {
      const link = canonicalizeLink(input.url);
      if (!link) throw new ApiError("invalid_link", "The text is not a link");
      canonicalUrl = link.url;
    }
    if (input.kind === "text" && input.text.trim().length < MINIMUM_OFFER_TEXT_LENGTH) {
      throw new ApiError("unreadable_offer", "The text is too short to be an offer");
    }
    const now = this.clock.now();

    if (options.idempotencyKey) {
      const existing = await queryOne<{ id: string }>(
        this.database,
        `select id::text as id from public.analyses
         where user_id = $1::uuid and idempotency_key = $2 and created_at > $3::timestamptz`,
        [userId, options.idempotencyKey, new Date(now.getTime() - IDEMPOTENCY_WINDOW_MILLISECONDS).toISOString()],
      );
      if (existing) return (await findAnalysis(this.database, existing.id, userId))!;
    }
    if (input.kind === "link" || input.kind === "manual") {
      const duplicate = await queryOne<{ id: string }>(
        this.database,
        `select id::text as id from public.analyses
         where user_id = $1::uuid and status <> 'failed' and created_at > $2::timestamptz
           and input_kind = $3
           and (($3 = 'link' and input_url = $4) or
                ($3 = 'manual' and lower(manual_job_title) = lower($5) and lower(manual_city) = lower($6)))
           and coalesce(area_city, '') = coalesce($7, '') and coalesce(area_radius_kilometers, 0) = coalesce($8, 0)
         order by created_at desc limit 1`,
        [
          userId,
          new Date(now.getTime() - DUPLICATE_WINDOW_MILLISECONDS).toISOString(),
          input.kind,
          canonicalUrl,
          input.kind === "manual" ? input.jobTitle : null,
          input.kind === "manual" ? input.city : null,
          input.area?.city ?? null,
          input.area?.radiusKilometers ?? null,
        ],
      );
      if (duplicate) return (await findAnalysis(this.database, duplicate.id, userId))!;
    }
    await this.refuseWhenDeleting(userId);

    let analysisId: string;
    try {
      analysisId = await this.insert(userId, input, canonicalUrl, options);
    } catch (error) {
      // Two requests with the same idempotency key at once: the second one gives back the first analysis.
      const code = (error as { code?: unknown }).code;
      if (code === "23505" && options.idempotencyKey) {
        const existing = await queryOne<{ id: string }>(
          this.database,
          `select id::text as id from public.analyses where user_id = $1::uuid and idempotency_key = $2`,
          [userId, options.idempotencyKey],
        );
        if (existing) return (await findAnalysis(this.database, existing.id, userId))!;
      }
      throw error;
    }
    return (await findAnalysis(this.database, analysisId, userId))!;
  }

  private async insert(
    userId: string,
    input: AnalysisInput,
    canonicalUrl: string | null,
    options: { idempotencyKey: string | null; language: Language },
  ): Promise<string> {
    const now = this.clock.now();
    return await this.database.transaction(async (transaction) => {
      const plan = await this.subscriptions.plan(userId, transaction);
      let quotaWeek: string | null = null;
      if (plan.plus) {
        const today = await queryOne<{ count: number }>(
          transaction,
          `select count(*)::integer as count from public.analyses where user_id = $1::uuid and created_at > $2::timestamptz`,
          [userId, new Date(now.getTime() - 86_400_000).toISOString()],
        );
        if ((today?.count ?? 0) >= this.configuration.plusAnalysesPerDay) {
          throw new ApiError("rate_limited", "Daily fair use limit of Jauge Plus reached", { retryAfterSeconds: 3600 });
        }
      } else {
        quotaWeek = await this.subscriptions.consumeFreeAnalysis(transaction, userId);
        if (!quotaWeek) throw new ApiError("quota_reached", "Weekly free analysis already used");
      }
      const inserted = await transaction.query<{ id: string }>(
        `insert into public.analyses (user_id, input_kind, input_origin, input_url, manual_job_title, manual_city,
           area_city, area_department_code, area_radius_kilometers, language, steps, progress_percent, quota_week,
           idempotency_key)
         values ($1::uuid, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11::jsonb, 5, $12::date, $13)
         returning id::text as id`,
        [
          userId,
          input.kind,
          input.kind === "image" || input.kind === "text" ? input.origin : null,
          canonicalUrl,
          input.kind === "manual" ? input.jobTitle : null,
          input.kind === "manual" ? input.city : null,
          input.area?.city ?? null,
          input.area?.departmentCode ?? null,
          input.area?.radiusKilometers ?? null,
          options.language,
          JSON.stringify(initialSteps()),
          quotaWeek,
          options.idempotencyKey,
        ],
      );
      const id = inserted[0].id;
      if (input.kind === "image" || input.kind === "text") {
        await transaction.query(
          `insert into public.analysis_inputs (analysis_id, image, mime_type, text_content, expires_at)
           values ($1::uuid, $2, $3, $4, $5::timestamptz)`,
          [
            id,
            input.kind === "image" ? input.image : null,
            input.kind === "image" ? input.mimeType : null,
            input.kind === "text" ? input.text : null,
            new Date(now.getTime() + 86_400_000).toISOString(),
          ],
        );
      }
      await transaction.query(
        `insert into public.jobs (kind, analysis_id, subject_user_id, deadline_at, idempotency_key, max_attempts)
         values ('analyze_offer', $1::uuid, $2::uuid, $3::timestamptz, $4, 6)`,
        [
          id,
          userId,
          new Date(now.getTime() + this.configuration.analysisDeadlineSeconds * 1000).toISOString(),
          `analysis:${id}`,
        ],
      );
      return id;
    });
  }

  /** Refuses writes while the account is being deleted, and after (its access token may live one more hour). */
  async refuseWhenDeleting(userId: string): Promise<void> {
    const deleting = await queryOne(
      this.database,
      `select 1 from public.jobs where kind = 'delete_account' and subject_user_id = $1::uuid`,
      [userId],
    );
    if (deleting) throw new ApiError("unauthorized", "The account is being deleted");
  }

  /** One analysis of the user, or `not_found`. */
  async get(userId: string, analysisId: string): Promise<AnalysisRecord> {
    const record = await findAnalysis(this.database, analysisId, userId);
    if (!record) throw notFound("Analysis");
    return record;
  }

  /** One completed analysis of the user, or `not_found` (insights need a result). */
  async getCompleted(userId: string, analysisId: string): Promise<AnalysisRecord> {
    const record = await this.get(userId, analysisId);
    if (record.status !== "completed" || !record.result || !record.offer) throw notFound("Completed analysis");
    return record;
  }

  /**
   * A page of the completed analyses of the user, newest first.
   *
   * @param userId user.
   * @param options cursor, page size and search query.
   */
  async list(userId: string, options: { cursor: string | null; limit: number; query: string | null }): Promise<{
    items: AnalysisRecord[];
    nextCursor: string | null;
  }> {
    const before = decodeCursor(options.cursor);
    const likePatterns = searchWords(options.query).map((word) => `%${escapeLikePattern(word)}%`);
    const records = await listAnalyses(this.database, userId, { before, limit: options.limit + 1, likePatterns });
    const items = records.slice(0, options.limit);
    const last = items[items.length - 1];
    const nextCursor = records.length > options.limit && last
      ? encodeCursor({ createdAt: toIsoTimestamp(last.createdAt), id: last.id })
      : null;
    return { items, nextCursor };
  }

  /** Deletes an analysis of the user (its alerts, shares and favorites go with it). */
  async delete(userId: string, analysisId: string): Promise<void> {
    const deleted = await this.database.query(
      `delete from public.analyses where id = $1::uuid and user_id = $2::uuid returning id`,
      [analysisId, userId],
    );
    if (deleted.length === 0) throw notFound("Analysis");
  }

  /** Stars or unstars an analysis. Idempotent. */
  async setFavorite(userId: string, analysisId: string, favorite: boolean): Promise<void> {
    await this.get(userId, analysisId);
    if (favorite) {
      await this.database.query(
        `insert into public.favorites (user_id, analysis_id) values ($1::uuid, $2::uuid) on conflict do nothing`,
        [userId, analysisId],
      );
    } else {
      await this.database.query(`delete from public.favorites where user_id = $1::uuid and analysis_id = $2::uuid`, [
        userId,
        analysisId,
      ]);
    }
  }
}
