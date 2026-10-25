/** Level of a log line. */
export type LogLevel = "debug" | "info" | "warning" | "error";

/** Fields of a log line: identifiers of requests and jobs, codes, counts and durations. Never personal data. */
export type LogFields = Record<string, string | number | boolean | null>;

/**
 * Structured logger: one JSON object per line, read by the Supabase logs explorer. By design it only takes
 * scalar fields chosen by the code (route templates, codes, durations): no user identifier, no IP address, no
 * offer text, no URL query.
 */
export interface Logger {
  /**
   * Writes one line.
   *
   * @param level level.
   * @param event short machine readable name (`request`, `job_failed`).
   * @param fields extra fields.
   */
  log(level: LogLevel, event: string, fields?: LogFields): void;
}

/** Logger that writes JSON lines on the standard output. */
export class JsonLogger implements Logger {
  /** @param write writes one line (console in production, an array in tests). */
  constructor(private readonly write: (line: string) => void) {}

  /** @inheritdoc */
  log(level: LogLevel, event: string, fields: LogFields = {}): void {
    this.write(JSON.stringify({ time: new Date().toISOString(), level, event, ...fields }));
  }
}

/** Logger that keeps the lines in memory (tests). */
export class MemoryLogger implements Logger {
  /** Lines written so far. */
  readonly lines: { level: LogLevel; event: string; fields: LogFields }[] = [];

  /** @inheritdoc */
  log(level: LogLevel, event: string, fields: LogFields = {}): void {
    this.lines.push({ level, event, fields });
  }
}
