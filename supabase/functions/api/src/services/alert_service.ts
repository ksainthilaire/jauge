import { type Database, queryOne } from "../database/database.ts";
import { ApiError, notFound } from "../http/api_error.ts";
import type { Logger } from "../operations/logger.ts";
import { findAnalysis } from "./analysis_records.ts";
import type { InsightService } from "./insight_service.ts";
import type { SubscriptionService } from "./subscription_service.ts";

/** Calm alerts one user may keep. */
export const MAXIMUM_ALERTS_PER_USER = 20;

interface AlertRow {
  id: string;
  analysis_id: string;
  threshold: number;
  active: boolean;
}

function presentAlert(row: AlertRow): Record<string, unknown> {
  return { id: row.id, analysisId: row.analysis_id, threshold: row.threshold, active: row.active };
}

/**
 * Calm alerts (a Jauge Plus feature, screen 08): the user is told when a twin offer of an analysis goes under the
 * threshold. Evaluated every day by the maintenance; each twin is notified once (table `alert_matches`), the push
 * itself is limited to 3 per week by the send-notification function.
 */
export class AlertService {
  /**
   * @param database database.
   * @param subscriptions plan of the users.
   * @param insights twin offers.
   * @param logger structured logger.
   */
  constructor(
    private readonly database: Database,
    private readonly subscriptions: SubscriptionService,
    private readonly insights: InsightService,
    private readonly logger: Logger,
  ) {}

  /** Alerts of the user, newest first. */
  async list(userId: string): Promise<Record<string, unknown>> {
    const rows = await this.database.query<AlertRow>(
      `select id::text as id, analysis_id::text as analysis_id, threshold, active
       from public.alerts where user_id = $1::uuid order by created_at desc`,
      [userId],
    );
    return { items: rows.map(presentAlert) };
  }

  /** Creates (or updates the threshold of) the alert of an analysis. Needs Jauge Plus. */
  async create(userId: string, analysisId: string, threshold: number): Promise<Record<string, unknown>> {
    const analysis = await findAnalysis(this.database, analysisId, userId);
    if (!analysis || analysis.status !== "completed") throw notFound("Analysis");
    const plan = await this.subscriptions.plan(userId);
    if (!plan.plus) throw new ApiError("quota_reached", "Calm alerts are part of Jauge Plus");
    const count = await queryOne<{ count: number }>(
      this.database,
      `select count(*)::integer as count from public.alerts where user_id = $1::uuid and analysis_id <> $2::uuid`,
      [userId, analysisId],
    );
    if ((count?.count ?? 0) >= MAXIMUM_ALERTS_PER_USER) {
      throw new ApiError("validation_failed", "Too many alerts", { status: 422 });
    }
    const rows = await this.database.query<AlertRow>(
      `insert into public.alerts (user_id, analysis_id, threshold) values ($1::uuid, $2::uuid, $3)
       on conflict (user_id, analysis_id) do update set threshold = excluded.threshold, active = true
       returning id::text as id, analysis_id::text as analysis_id, threshold, active`,
      [userId, analysisId, threshold],
    );
    return presentAlert(rows[0]);
  }

  /** Deletes an alert of the user. */
  async delete(userId: string, alertId: string): Promise<void> {
    const deleted = await this.database.query(
      `delete from public.alerts where id = $1::uuid and user_id = $2::uuid returning id`,
      [alertId, userId],
    );
    if (deleted.length === 0) throw notFound("Alert");
  }

  /**
   * Evaluates the active alerts of Jauge Plus users: for each one, the calmest new twin under the threshold (not
   * already shown when the analysis was made, not already notified) becomes a notification.
   *
   * @param limit largest number of alerts evaluated in one run (the oldest evaluated first).
   */
  async evaluate(limit = 300): Promise<{ evaluated: number; notified: number }> {
    const alerts = await this.database.query<{ id: string; user_id: string; analysis_id: string; threshold: number }>(
      `select id::text as id, user_id::text as user_id, analysis_id::text as analysis_id, threshold
       from public.alerts where active order by last_evaluated_at nulls first limit $1`,
      [limit],
    );
    let notified = 0;
    for (const alert of alerts) {
      try {
        await this.database.query(`update public.alerts set last_evaluated_at = now() where id = $1::uuid`, [alert.id]);
        if (!(await this.subscriptions.plan(alert.user_id)).plus) continue;
        const analysis = await findAnalysis(this.database, alert.analysis_id, alert.user_id);
        if (!analysis || analysis.status !== "completed") continue;
        const twins = await this.insights.twins(analysis, {
          area: null,
          radius: null,
          sort: "least_disputed",
          query: null,
        });
        const items = twins.items as {
          id: string;
          jobTitle: string;
          place: string;
          score: number;
          estimatedApplicants: number;
        }[];
        const known = await this.database.query<{ id: string }>(
          `select collected_offer_id::text as id from public.alert_matches where alert_id = $1::uuid
           union select collected_offer_id::text from public.snapshot_twins where snapshot_id = $2::uuid`,
          [alert.id, analysis.snapshotId],
        );
        const knownIds = new Set(known.map((row) => row.id));
        const candidate = items.find((item) => item.score < alert.threshold && !knownIds.has(item.id));
        if (!candidate) continue;
        const french = analysis.language === "fr";
        await this.database.transaction(async (transaction) => {
          await transaction.query(
            `insert into public.alert_matches (alert_id, collected_offer_id, score) values ($1::uuid, $2::uuid, $3)
             on conflict do nothing`,
            [alert.id, candidate.id, candidate.score],
          );
          await transaction.query(
            `insert into public.notifications (user_id, kind, title, body, analysis_id, alert_id)
             values ($1::uuid, 'calm_alert', $2, $3, $4, $5)`,
            [
              alert.user_id,
              french ? "Une jumelle calme vient d'apparaître" : "A calm twin just appeared",
              `${candidate.jobTitle} · ${candidate.place} · ≈ ${candidate.estimatedApplicants} ${
                french ? "candidats estimés" : "estimated applicants"
              }`
                .slice(0, 500),
              alert.analysis_id,
              alert.id,
            ],
          );
        });
        notified += 1;
      } catch (error) {
        this.logger.log("warning", "alert_evaluation_failed", { error: (error as Error).name });
      }
    }
    return { evaluated: alerts.length, notified };
  }
}
