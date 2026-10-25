import type { Database } from "../database/database.ts";
import { toIsoTimestampOrNull } from "../database/database.ts";
import type { Clock } from "../operations/clock.ts";
import type { Logger } from "../operations/logger.ts";
import { ProviderError } from "../providers/provider_types.ts";
import type { Job, JobHandler, JobKind, StageOutcome } from "./job_types.ts";

/** Settings of the worker. */
export interface WorkerSettings {
  /** Jobs claimed at once. */
  readonly batchSize: number;
  /** Seconds a claimed job stays reserved (a crashed worker frees it after that). */
  readonly leaseSeconds: number;
  /** Wall clock budget of one run (Edge Functions stop after 150 s on the free plan). */
  readonly budgetMilliseconds: number;
  /** Stages run in a row for one job before it goes back to the queue (fairness). */
  readonly maximumStagesPerClaim: number;
  /** Longest wait the worker does itself instead of returning (the scheduler wakes it for longer waits). */
  readonly maximumIdleMilliseconds: number;
}

/** Default settings. */
export const DEFAULT_WORKER_SETTINGS: WorkerSettings = {
  batchSize: 4,
  leaseSeconds: 90,
  budgetMilliseconds: 50_000,
  maximumStagesPerClaim: 12,
  maximumIdleMilliseconds: 6_000,
};

const MAXIMUM_BACKOFF_SECONDS = 60;

function errorCode(error: unknown): string {
  if (error instanceof ProviderError) return `${error.provider}:${error.code}`.slice(0, 200);
  if (error && typeof error === "object" && "code" in error && typeof error.code === "string") {
    return `database:${error.code}`;
  }
  return error instanceof Error ? error.name.slice(0, 200) : "unknown";
}

function isRetryable(error: unknown): boolean {
  return !(error instanceof ProviderError) || error.retryable;
}

/**
 * Worker of the queue: claims due jobs (`api_claim_jobs`, SKIP LOCKED, leases), runs their stages and saves the
 * stage after each step, so that a crash or a timeout resumes where it stopped. Errors are retried with an
 * exponential backoff; after the last attempt (or after the deadline) the handler gives up cleanly.
 */
export class Worker {
  private readonly name = `worker-${crypto.randomUUID().slice(0, 8)}`;

  /**
   * @param database database.
   * @param handlers handler of each kind of job.
   * @param clock clock.
   * @param logger structured logger.
   * @param settings settings.
   * @param sleep waits (injected so that tests do not wait).
   */
  constructor(
    private readonly database: Database,
    private readonly handlers: Readonly<Record<JobKind, JobHandler>>,
    private readonly clock: Clock,
    private readonly logger: Logger,
    private readonly settings: WorkerSettings = DEFAULT_WORKER_SETTINGS,
    private readonly sleep: (milliseconds: number) => Promise<void> = (milliseconds) =>
      new Promise((resolve) => setTimeout(resolve, milliseconds)),
  ) {}

  /** Runs due jobs until the queue is empty (or only has later jobs) or the budget is spent. */
  async run(): Promise<{ processed: number }> {
    const started = Date.now();
    let processed = 0;
    while (Date.now() - started < this.settings.budgetMilliseconds) {
      const jobs = await this.claim();
      if (jobs.length === 0) {
        const waitMilliseconds = await this.millisecondsUntilNextJob();
        const remaining = this.settings.budgetMilliseconds - (Date.now() - started);
        if (
          waitMilliseconds === null || waitMilliseconds > Math.min(this.settings.maximumIdleMilliseconds, remaining)
        ) break;
        await this.sleep(Math.max(waitMilliseconds, 50));
        continue;
      }
      for (const job of jobs) {
        await this.process(job);
        processed += 1;
      }
    }
    return { processed };
  }

  private async claim(): Promise<Job[]> {
    const rows = await this.database.query<Record<string, unknown>>(
      `select id::text as id, kind, analysis_id::text as analysis_id, subject_user_id::text as subject_user_id, payload,
              stage, attempts, max_attempts, deadline_at
       from public.api_claim_jobs($1, $2, $3)`,
      [this.name, this.settings.batchSize, this.settings.leaseSeconds],
    );
    return rows.map((row) => ({
      id: String(row.id),
      kind: row.kind as JobKind,
      analysisId: (row.analysis_id as string | null) ?? null,
      subjectUserId: (row.subject_user_id as string | null) ?? null,
      payload: (row.payload as Record<string, unknown> | null) ?? {},
      stage: String(row.stage),
      attempts: Number(row.attempts),
      maxAttempts: Number(row.max_attempts),
      deadlineAt: toIsoTimestampOrNull(row.deadline_at),
    }));
  }

  private async millisecondsUntilNextJob(): Promise<number | null> {
    const rows = await this.database.query<{ next_run: unknown }>(
      `select min(run_after) as next_run from public.jobs where status = 'queued'`,
    );
    const next = toIsoTimestampOrNull(rows[0]?.next_run);
    return next === null ? null : Math.max(0, Date.parse(next) - this.clock.now().getTime());
  }

  /** Runs the stages of one claimed job. */
  async process(claimed: Job): Promise<void> {
    const handler = this.handlers[claimed.kind];
    let job = claimed;
    const startedAt = Date.now();
    try {
      for (let step = 0; step < this.settings.maximumStagesPerClaim; step++) {
        const outcome: StageOutcome = await handler.runStage(job);
        if (outcome.kind === "next") {
          job = { ...job, stage: outcome.stage, payload: outcome.payload ?? job.payload };
          await this.saveStage(job);
          continue;
        }
        if (outcome.kind === "wait") {
          await this.requeue(job.id, outcome.stage, outcome.payload ?? job.payload, outcome.seconds, true, null);
        } else if (outcome.kind === "done") {
          await this.finish(job.id, "succeeded", null);
        } else {
          await this.finish(job.id, "failed", outcome.reason);
        }
        this.logger.log("info", "job_step", {
          jobId: job.id,
          kind: job.kind,
          stage: outcome.kind === "wait" ? outcome.stage : job.stage,
          outcome: outcome.kind,
          durationMs: Date.now() - startedAt,
        });
        return;
      }
      await this.requeue(job.id, job.stage, job.payload, 0, true, null);
    } catch (error) {
      const code = errorCode(error);
      const pastDeadline = job.deadlineAt !== null && Date.parse(job.deadlineAt) <= this.clock.now().getTime();
      if (isRetryable(error) && job.attempts < job.maxAttempts && !pastDeadline) {
        const backoff = Math.min(MAXIMUM_BACKOFF_SECONDS, 2 ** job.attempts);
        await this.requeue(job.id, job.stage, job.payload, backoff, false, code);
        this.logger.log("warning", "job_retry", {
          jobId: job.id,
          kind: job.kind,
          stage: job.stage,
          error: code,
          backoffSeconds: backoff,
        });
        return;
      }
      this.logger.log("error", "job_failed", {
        jobId: job.id,
        kind: job.kind,
        stage: job.stage,
        error: code,
        attempts: job.attempts,
      });
      try {
        await handler.giveUp(job, code);
      } finally {
        await this.finish(job.id, "failed", code);
      }
    }
  }

  private async saveStage(job: Job): Promise<void> {
    await this.database.query(
      `update public.jobs set stage = $2, payload = $3::jsonb, updated_at = now() where id = $1::uuid`,
      [job.id, job.stage, JSON.stringify(job.payload)],
    );
  }

  /** Puts a job back in the queue. A wait is not a failed attempt: the attempt counter goes back down. */
  private async requeue(
    jobId: string,
    stage: string,
    payload: Record<string, unknown>,
    delaySeconds: number,
    isWait: boolean,
    lastError: string | null,
  ): Promise<void> {
    await this.database.query(
      `update public.jobs
       set status = 'queued', stage = $2, payload = $3::jsonb, run_after = $4::timestamptz, lease_expires_at = null,
           locked_by = null, attempts = case when $5 then greatest(attempts - 1, 0) else attempts end,
           last_error = coalesce($6, last_error), updated_at = now()
       where id = $1::uuid`,
      [
        jobId,
        stage,
        JSON.stringify(payload),
        new Date(this.clock.now().getTime() + delaySeconds * 1000).toISOString(),
        isWait,
        lastError,
      ],
    );
  }

  private async finish(jobId: string, status: "succeeded" | "failed", lastError: string | null): Promise<void> {
    await this.database.query(
      `update public.jobs
       set status = $2, last_error = coalesce($3, last_error), lease_expires_at = null, locked_by = null,
           finished_at = now(), updated_at = now(),
           payload = case when kind = 'delete_account' then '{}'::jsonb else payload end
       where id = $1::uuid`,
      [jobId, status, lastError],
    );
  }
}
