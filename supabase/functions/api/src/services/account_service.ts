import { type Database, queryOne } from "../database/database.ts";
import { ApiError } from "../http/api_error.ts";
import type { Logger } from "../operations/logger.ts";
import type { Providers } from "../providers/provider_types.ts";
import { ProviderError } from "../providers/provider_types.ts";
import type { Job, JobHandler, StageOutcome } from "../queue/job_types.ts";

/** Steps of the deletion of an account, after the data itself (deleted at once by the request). */
type DeletionStage = "start" | "revenuecat" | "apple" | "auth_user";

/**
 * The account: profile, consents, and its deletion (`DELETE /v1/account`, required by the stores).
 *
 * The deletion erases every row of the user at once (one transaction, `api_delete_user_data`), then a job deletes
 * the RevenueCat customer, revokes Sign in with Apple (when the app sent a fresh authorization code) and deletes the
 * Supabase auth user, with retries. Until the auth user is gone, write routes refuse the user.
 */
export class AccountService implements JobHandler {
  /**
   * @param database database.
   * @param providers RevenueCat, Apple and Supabase Auth services.
   * @param logger structured logger.
   */
  constructor(
    private readonly database: Database,
    private readonly providers: Providers,
    private readonly logger: Logger,
  ) {}

  /** The account (`Account` of the contract). */
  async get(userId: string): Promise<Record<string, unknown>> {
    const row = await queryOne<Record<string, unknown>>(
      this.database,
      `select u.email, u.is_anonymous, u.raw_app_meta_data->>'provider' as provider,
              coalesce(u.raw_user_meta_data->>'full_name', u.raw_user_meta_data->>'name') as display_name,
              coalesce(p.marketing_emails, false) as marketing_emails,
              (select count(*)::integer from public.analyses a where a.user_id = u.id and a.status = 'completed') as analysis_count
       from auth.users u left join public.profiles p on p.user_id = u.id
       where u.id = $1::uuid`,
      [userId],
    );
    if (!row) throw new ApiError("unauthorized", "The account does not exist any more");
    const provider = String(row.provider ?? "");
    const signInMethod = row.is_anonymous === true
      ? "none"
      : provider === "apple" || provider === "google" || provider === "email"
      ? provider
      : "none";
    return {
      id: userId,
      displayName: (row.display_name as string | null)?.slice(0, 120) ?? null,
      email: (row.email as string | null) ?? null,
      signInMethod,
      analysisCount: Number(row.analysis_count ?? 0),
      consents: { marketingEmails: row.marketing_emails === true },
    };
  }

  /** Updates the consents and returns the account. */
  async updateConsents(userId: string, marketingEmails: boolean): Promise<Record<string, unknown>> {
    await this.database.query(
      `insert into public.profiles (user_id, marketing_emails, marketing_emails_updated_at) values ($1::uuid, $2, now())
       on conflict (user_id) do update set marketing_emails = excluded.marketing_emails,
         marketing_emails_updated_at = now(), updated_at = now()`,
      [userId, marketingEmails],
    );
    return await this.get(userId);
  }

  /**
   * Deletes the data of the user at once and schedules the deletion of the outside accounts.
   *
   * @param userId user.
   * @param appleAuthorizationCode fresh Sign in with Apple authorization code, when the app has one.
   */
  async requestDeletion(userId: string, appleAuthorizationCode: string | null): Promise<void> {
    await this.database.transaction(async (transaction) => {
      await transaction.query(`select public.api_delete_user_data($1::uuid)`, [userId]);
      await transaction.query(
        `insert into public.jobs (kind, subject_user_id, payload, max_attempts, idempotency_key)
         values ('delete_account', $1::uuid, $2::jsonb, 12, $3)
         on conflict (idempotency_key) do update set payload = excluded.payload, status = 'queued', run_after = now()`,
        [userId, JSON.stringify({ appleAuthorizationCode }), `delete_account:${userId}`],
      );
    });
    this.logger.log("info", "account_deletion_requested", { apple: appleAuthorizationCode !== null });
  }

  /** @inheritdoc */
  async runStage(job: Job): Promise<StageOutcome> {
    const userId = job.subjectUserId;
    if (!userId) return { kind: "failed", reason: "no_user" };
    switch (job.stage as DeletionStage) {
      case "start":
      case "revenuecat":
        await this.providers.subscriptionStore.deleteCustomer(userId);
        return { kind: "next", stage: "apple" };
      case "apple": {
        const code = typeof job.payload.appleAuthorizationCode === "string" ? job.payload.appleAuthorizationCode : null;
        if (code) {
          try {
            await this.providers.appleTokenRevoker.revokeWithAuthorizationCode(code);
          } catch (error) {
            // An expired code cannot be used again: the deletion goes on, Apple drops unused tokens itself.
            if (error instanceof ProviderError && !error.retryable) {
              this.logger.log("warning", "apple_revocation_skipped", { error: error.code });
            } else {
              throw error;
            }
          }
        }
        return { kind: "next", stage: "auth_user", payload: {} };
      }
      case "auth_user":
        // The data may have come back between the request and now (an analysis finishing): delete it again.
        await this.database.query(`select public.api_delete_user_data($1::uuid)`, [userId]);
        await this.providers.authAdministration.deleteUser(userId);
        this.logger.log("info", "account_deleted", {});
        return { kind: "done" };
      default:
        return { kind: "failed", reason: `unknown_stage:${job.stage}` };
    }
  }

  /** @inheritdoc */
  async giveUp(job: Job, reason: string): Promise<void> {
    try {
      await this.providers.operationsAlerter.send(
        "Jauge: suppression de compte bloquée",
        `La suppression d'un compte n'a pas pu finir (étape ${job.stage}, ${reason}). Les données sont effacées ; à reprendre à la main.`,
      );
    } catch {
      this.logger.log("warning", "alert_delivery_failed", { channel: "webhook" });
    }
  }
}
