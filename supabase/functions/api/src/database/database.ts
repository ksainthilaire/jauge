/**
 * Access to Postgres. Parameters use `$1`, `$2`... Arrays and JSON are sent as JSON text and cast in SQL
 * (`$1::jsonb`), dates as ISO text, so that every driver behaves the same.
 */
export interface Database {
  /**
   * Runs one statement and returns its rows.
   *
   * @param text SQL text.
   * @param parameters values of `$1`, `$2`...
   */
  query<Row = Record<string, unknown>>(text: string, parameters?: readonly unknown[]): Promise<Row[]>;

  /**
   * Runs work in one transaction (committed when it returns, rolled back when it throws).
   *
   * @param work statements of the transaction.
   */
  transaction<Result>(work: (transaction: Database) => Promise<Result>): Promise<Result>;
}

/** Returns the only row or null. */
export async function queryOne<Row = Record<string, unknown>>(
  database: Database,
  text: string,
  parameters: readonly unknown[] = [],
): Promise<Row | null> {
  const rows = await database.query<Row>(text, parameters);
  return rows[0] ?? null;
}

/** ISO 8601 text of a timestamp read from the database (Date or text, depending on the driver). */
export function toIsoTimestamp(value: unknown): string {
  if (value instanceof Date) return value.toISOString();
  return new Date(String(value)).toISOString();
}

/** Same as [toIsoTimestamp] but keeps null. */
export function toIsoTimestampOrNull(value: unknown): string | null {
  return value === null || value === undefined ? null : toIsoTimestamp(value);
}
