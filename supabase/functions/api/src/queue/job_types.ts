/** Kinds of jobs of the work queue (`jobs.kind`). */
export type JobKind = "analyze_offer" | "delete_account" | "evaluate_alerts";

/** A claimed job. */
export interface Job {
  readonly id: string;
  readonly kind: JobKind;
  readonly analysisId: string | null;
  readonly subjectUserId: string | null;
  readonly payload: Record<string, unknown>;
  readonly stage: string;
  readonly attempts: number;
  readonly maxAttempts: number;
  readonly deadlineAt: string | null;
}

/** What a stage decided. */
export type StageOutcome =
  /** Go on with another stage at once. */
  | { readonly kind: "next"; readonly stage: string; readonly payload?: Record<string, unknown> }
  /** Wait for something outside (a running collection, another analysis) and come back later. */
  | {
    readonly kind: "wait";
    readonly stage: string;
    readonly seconds: number;
    readonly payload?: Record<string, unknown>;
  }
  /** The job is finished. */
  | { readonly kind: "done" }
  /** The job failed for good (the handler already recorded what the user sees). */
  | { readonly kind: "failed"; readonly reason: string };

/** Runs the stages of one kind of job. */
export interface JobHandler {
  /**
   * Runs one stage. Throwing means "retry later" (with a backoff) until the attempts or the deadline run out.
   *
   * @param job the job, with its current stage and payload.
   */
  runStage(job: Job): Promise<StageOutcome>;

  /**
   * Called once when the job gives up (attempts or deadline exhausted after errors), so that the user sees a
   * clean failure (and gets the free analysis back).
   */
  giveUp(job: Job, reason: string): Promise<void>;
}
