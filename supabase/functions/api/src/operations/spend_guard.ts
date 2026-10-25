import type { Database } from "../database/database.ts";
import type { AiUsage, OperationsAlerter } from "../providers/provider_types.ts";
import type { Clock } from "./clock.ts";
import { utcDay } from "./clock.ts";
import type { Logger } from "./logger.ts";

/** What a cost is about. */
export interface CostEntry {
  readonly provider: string;
  readonly operation: string;
  readonly costMicroUsd: number;
  readonly units?: number;
  readonly sliceId?: string | null;
  readonly snapshotId?: string | null;
  /** Model and tokens of an AI operation. */
  readonly usage?: AiUsage;
}

/**
 * Daily spending limit of the paid providers. Before a paid call, `allows` checks that the money spent today (UTC)
 * plus the estimate of the call stays under the limit; over it, the pipeline skips the paid providers and serves
 * the cache and the market data already in the database. Every cost is journaled in `provider_costs`, and the
 * owner is warned once a day at 80 % and once at 100 % of the limit.
 */
export class SpendGuard {
  /**
   * @param database database.
   * @param clock clock.
   * @param settings daily limit in US dollars, warning ratio, and whether the providers are simulated (simulated
   *   costs are journaled but never count).
   * @param alerter alert channel of the owner.
   * @param logger structured logger.
   */
  constructor(
    private readonly database: Database,
    private readonly clock: Clock,
    private readonly settings: { dailyLimitUsd: number; warningRatio: number; simulated: boolean },
    private readonly alerter: OperationsAlerter,
    private readonly logger: Logger,
  ) {}

  /** Money spent today (UTC), in micro US dollars. */
  async spentTodayMicroUsd(): Promise<number> {
    const rows = await this.database.query<{ spent: number }>(
      `select public.api_daily_spend_micro_usd($1::date)::float8 as spent`,
      [utcDay(this.clock.now())],
    );
    return Number(rows[0]?.spent ?? 0);
  }

  /** Daily limit in micro US dollars. */
  get dailyLimitMicroUsd(): number {
    return Math.round(this.settings.dailyLimitUsd * 1_000_000);
  }

  /**
   * True when a paid call of this estimated cost may run now.
   *
   * @param estimateMicroUsd estimated cost of the call.
   */
  async allows(estimateMicroUsd: number): Promise<boolean> {
    if (this.settings.simulated || estimateMicroUsd <= 0) return true;
    const spent = await this.spentTodayMicroUsd();
    const allowed = spent + estimateMicroUsd <= this.dailyLimitMicroUsd;
    if (!allowed) await this.alertOnce("limit_reached", spent);
    return allowed;
  }

  /** Journals a cost and sends the warning when the threshold is crossed. */
  async record(entry: CostEntry): Promise<void> {
    if (entry.costMicroUsd <= 0) return;
    await this.database.query(
      `insert into public.provider_costs (provider, operation, slice_id, snapshot_id, units, cost_micro_usd, simulated,
                                          model, input_tokens, output_tokens)
       values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`,
      [
        entry.provider,
        entry.operation,
        entry.sliceId ?? null,
        entry.snapshotId ?? null,
        entry.units ?? 1,
        Math.round(entry.costMicroUsd),
        this.settings.simulated,
        entry.usage?.model.slice(0, 80) ?? null,
        entry.usage ? Math.round(entry.usage.inputTokens) : null,
        entry.usage ? Math.round(entry.usage.outputTokens) : null,
      ],
    );
    if (this.settings.simulated) return;
    const spent = await this.spentTodayMicroUsd();
    if (spent >= this.dailyLimitMicroUsd * this.settings.warningRatio) await this.alertOnce("warning", spent);
  }

  private async alertOnce(level: "warning" | "limit_reached", spentMicroUsd: number): Promise<void> {
    const inserted = await this.database.query(
      `insert into public.spend_alerts (day, level) values ($1::date, $2) on conflict do nothing returning day`,
      [utcDay(this.clock.now()), level],
    );
    if (inserted.length === 0) return;
    const spentUsd = (spentMicroUsd / 1_000_000).toFixed(2);
    this.logger.log(level === "warning" ? "warning" : "error", "spend_alert", {
      level,
      spentUsd: Number(spentUsd),
      limitUsd: this.settings.dailyLimitUsd,
    });
    const title = level === "warning" ? "Jauge: 80 % du budget du jour" : "Jauge: budget du jour atteint";
    const message = `Dépensé aujourd'hui : ${spentUsd} $ sur ${this.settings.dailyLimitUsd} $. ` +
      (level === "warning"
        ? "Les sources payantes continuent jusqu'à la limite."
        : "Les sources payantes sont coupées jusqu'à minuit UTC : Jauge sert le cache et les données déjà collectées.");
    try {
      await this.alerter.send(title, message);
    } catch {
      this.logger.log("warning", "alert_delivery_failed", { channel: "webhook" });
    }
  }
}
