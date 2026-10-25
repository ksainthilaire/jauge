import postgres from "postgres";
import type { Database } from "./database.ts";

type SqlClient = postgres.Sql | postgres.TransactionSql;

/**
 * Database of production: postgres.js on the connection string given by Supabase (`SUPABASE_DB_URL`). Prepared
 * statements are off so that it also works through the transaction pooler (Supavisor, port 6543).
 */
export class PostgresDatabase implements Database {
  private constructor(private readonly client: SqlClient) {}

  /**
   * Opens a small pool (an Edge Function isolate serves one request at a time most of the time).
   *
   * @param connectionString Postgres connection string.
   */
  static connect(connectionString: string): PostgresDatabase {
    return new PostgresDatabase(
      postgres(connectionString, { prepare: false, max: 3, idle_timeout: 20, connect_timeout: 10 }),
    );
  }

  /** @inheritdoc */
  async query<Row = Record<string, unknown>>(text: string, parameters: readonly unknown[] = []): Promise<Row[]> {
    const rows = await this.client.unsafe(text, parameters as postgres.ParameterOrJSON<never>[]);
    return rows as unknown as Row[];
  }

  /** @inheritdoc */
  async transaction<Result>(work: (transaction: Database) => Promise<Result>): Promise<Result> {
    if (!("begin" in this.client)) return await work(this);
    const result = await (this.client as postgres.Sql).begin((transaction) => work(new PostgresDatabase(transaction)));
    return result as Result;
  }
}
