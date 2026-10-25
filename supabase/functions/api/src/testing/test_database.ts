import { PGlite, type Transaction } from "@electric-sql/pglite";
import type { Database } from "../database/database.ts";

/**
 * What a hosted Supabase database provides before the migrations run, reduced to what the migrations of Jauge use:
 * the roles, the `auth` schema (users and `auth.uid()`), Vault, the Realtime publication and a recording `pg_net`.
 * pg_cron does not exist here: the migrations skip their schedules when it is missing.
 */
const SUPABASE_BOOTSTRAP = `
create role anon nologin;
create role authenticated nologin;
create role service_role nologin bypassrls;
create schema extensions;
create schema auth;
create table auth.users (
    id uuid primary key,
    email text,
    is_anonymous boolean not null default false,
    raw_app_meta_data jsonb not null default '{}',
    raw_user_meta_data jsonb not null default '{}',
    created_at timestamptz not null default now()
);
create function auth.uid() returns uuid language sql stable as $$
    select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;
grant usage on schema auth to anon, authenticated, service_role;
grant execute on function auth.uid() to anon, authenticated, service_role;
grant usage on schema public to anon, authenticated, service_role;
create schema vault;
create table vault.secrets (name text primary key, decrypted_secret text not null);
create view vault.decrypted_secrets as select name, decrypted_secret from vault.secrets;
create schema net;
create table net.recorded_requests (id bigserial primary key, url text, body jsonb, headers jsonb);
create function net.http_post(url text, body jsonb default '{}', params jsonb default '{}',
    headers jsonb default '{}', timeout_milliseconds integer default 5000) returns bigint language sql as $$
    insert into net.recorded_requests (url, body, headers) values (url, body, headers) returning id
$$;
create publication supabase_realtime;
`;

/** Directory of the migrations, relative to this file. */
const MIGRATIONS_DIRECTORY = new URL("../../../../migrations/", import.meta.url);

/**
 * Reads the migrations of the repository in order. `create extension pg_net` is removed: the bootstrap above
 * provides a recording `net.http_post` instead (PGlite cannot load pg_net).
 */
export async function readMigrations(): Promise<{ name: string; sql: string }[]> {
  const names: string[] = [];
  for await (const entry of Deno.readDir(MIGRATIONS_DIRECTORY)) {
    if (entry.isFile && entry.name.endsWith(".sql")) names.push(entry.name);
  }
  names.sort();
  const migrations = [];
  for (const name of names) {
    const sql = await Deno.readTextFile(new URL(name, MIGRATIONS_DIRECTORY));
    migrations.push({ name, sql: sql.replace(/create extension if not exists pg_net[^;]*;/gi, "") });
  }
  return migrations;
}

/** PGlite seen through the [Database] interface. */
class PgliteDatabase implements Database {
  constructor(private readonly client: PGlite | Transaction) {}

  async query<Row = Record<string, unknown>>(text: string, parameters: readonly unknown[] = []): Promise<Row[]> {
    const result = await this.client.query<Row>(text, parameters as unknown[]);
    return result.rows;
  }

  async transaction<Result>(work: (transaction: Database) => Promise<Result>): Promise<Result> {
    if (!(this.client instanceof PGlite)) return await work(this);
    return await this.client.transaction((transaction) => work(new PgliteDatabase(transaction)));
  }
}

/** A migrated in-memory database for one test. */
export interface TestDatabase {
  /** The database seen by the API (owner of the tables, like the Edge Function). */
  readonly database: Database;
  /** The PGlite instance, to switch roles in RLS tests. */
  readonly client: PGlite;
  /** Creates a user in `auth.users` and returns its identifier. */
  createUser(options?: { email?: string; provider?: string; displayName?: string }): Promise<string>;
  /** Closes the database. */
  close(): Promise<void>;
}

let cachedDump: File | Blob | null = null;

/**
 * Creates an in-memory Postgres 17 (PGlite) with the Supabase bootstrap and every migration of the repository.
 * The migrated database is dumped once and reloaded for each test, which keeps the tests fast.
 */
export async function createTestDatabase(): Promise<TestDatabase> {
  let client: PGlite;
  if (cachedDump) {
    client = new PGlite({ loadDataDir: cachedDump });
  } else {
    client = new PGlite();
    await client.exec(SUPABASE_BOOTSTRAP);
    for (const migration of await readMigrations()) {
      try {
        await client.exec(migration.sql);
      } catch (error) {
        throw new Error(`Migration ${migration.name} failed: ${(error as Error).message}`);
      }
    }
    cachedDump = await client.dumpDataDir("none");
  }
  await client.waitReady;
  const database = new PgliteDatabase(client);
  return {
    database,
    client,
    async createUser(options = {}) {
      const identifier = crypto.randomUUID();
      await client.query(
        `insert into auth.users (id, email, raw_app_meta_data, raw_user_meta_data) values ($1, $2, $3::jsonb, $4::jsonb)`,
        [
          identifier,
          options.email ?? null,
          JSON.stringify({ provider: options.provider ?? "email" }),
          JSON.stringify(options.displayName ? { full_name: options.displayName } : {}),
        ],
      );
      return identifier;
    },
    async close() {
      await client.close();
    },
  };
}
