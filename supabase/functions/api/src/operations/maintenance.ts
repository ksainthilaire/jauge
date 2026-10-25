import type { Configuration } from "../configuration.ts";
import { type Database, toIsoTimestampOrNull } from "../database/database.ts";
import type { Providers } from "../providers/provider_types.ts";
import type { AlertService } from "../services/alert_service.ts";
import type { Clock } from "./clock.ts";
import type { Logger } from "./logger.ts";
import type { SpendGuard } from "./spend_guard.ts";

/** A queue whose oldest due job waits longer than this is reported as late. */
const LATE_QUEUE_SECONDS = 600;

/** State of the service for the health check and the daily maintenance. */
export interface HealthReport {
  readonly status: "ok" | "degraded" | "down";
  readonly version: string;
  readonly providers: "simulated" | "live";
  readonly database: "ok" | "unreachable";
  readonly queue?: { readonly due: number; readonly oldestDueSeconds: number | null };
  readonly spend?: { readonly todayUsd: number; readonly limitUsd: number };
  readonly maintenance: boolean;
}

/**
 * Health check and daily maintenance (purge, calm alerts, warnings about a late queue).
 */
export class Operations {
  /**
   * @param database database.
   * @param configuration configuration.
   * @param providers outside services (alerts of the owner).
   * @param spendGuard daily spending limit.
   * @param alerts calm alerts.
   * @param clock clock.
   * @param logger structured logger.
   */
  constructor(
    private readonly database: Database,
    private readonly configuration: Configuration,
    private readonly providers: Providers,
    private readonly spendGuard: SpendGuard,
    private readonly alerts: AlertService,
    private readonly clock: Clock,
    private readonly logger: Logger,
  ) {}

  /**
   * Health of the service. The public report only says whether the API answers; the detailed one (internal secret)
   * adds the queue and the spending of the day.
   *
   * @param detailed true for the detailed report.
   */
  async health(detailed: boolean): Promise<HealthReport> {
    const base = {
      version: this.configuration.version,
      providers: this.providers.mode,
      maintenance: this.configuration.maintenance,
    };
    try {
      await this.database.query(`select 1`);
    } catch {
      return { ...base, status: "down", database: "unreachable" };
    }
    if (!detailed) return { ...base, status: "ok", database: "ok" };
    const queue = await this.database.query<{ due: number; oldest: unknown }>(
      `select count(*)::integer as due, min(run_after) as oldest from public.jobs
       where status = 'queued' and run_after <= now()`,
    );
    const oldest = toIsoTimestampOrNull(queue[0]?.oldest);
    const oldestDueSeconds = oldest ? Math.round((this.clock.now().getTime() - Date.parse(oldest)) / 1000) : null;
    const spent = await this.spendGuard.spentTodayMicroUsd();
    const late = oldestDueSeconds !== null && oldestDueSeconds > LATE_QUEUE_SECONDS;
    return {
      ...base,
      status: late || spent >= this.spendGuard.dailyLimitMicroUsd ? "degraded" : "ok",
      database: "ok",
      queue: { due: queue[0]?.due ?? 0, oldestDueSeconds },
      spend: { todayUsd: Math.round(spent / 10_000) / 100, limitUsd: this.configuration.dailySpendLimitUsd },
    };
  }

  /** Daily maintenance: purge, calm alerts, warning when the queue is late. */
  async runMaintenance(): Promise<Record<string, unknown>> {
    const purged = await this.database.query<{ purged: Record<string, number> }>(
      `select public.api_purge_expired() as purged`,
    );
    const alerts = await this.alerts.evaluate();
    const report = await this.health(true);
    if (report.queue && report.queue.oldestDueSeconds !== null && report.queue.oldestDueSeconds > LATE_QUEUE_SECONDS) {
      try {
        await this.providers.operationsAlerter.send(
          "Jauge: file de travail en retard",
          `${report.queue.due} tâches attendent depuis ${
            Math.round(report.queue.oldestDueSeconds / 60)
          } min. Vérifier pg_cron et la fonction api.`,
        );
      } catch {
        this.logger.log("warning", "alert_delivery_failed", { channel: "webhook" });
      }
    }
    this.logger.log("info", "maintenance", { alertsEvaluated: alerts.evaluated, alertsNotified: alerts.notified });
    return { purged: purged[0]?.purged ?? {}, alerts };
  }
}
