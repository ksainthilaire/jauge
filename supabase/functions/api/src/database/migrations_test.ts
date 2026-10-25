import { assert, assertEquals, assertRejects } from "@std/assert";
import { createTestDatabase } from "../testing/test_database.ts";

Deno.test("migrations apply on an empty Supabase-like database", async () => {
  const test = await createTestDatabase();
  try {
    const tables = await test.database.query<{ table_name: string; row_security: boolean }>(
      `select c.relname as table_name, c.relrowsecurity as row_security
       from pg_class c join pg_namespace n on n.oid = c.relnamespace
       where n.nspname = 'public' and c.relkind = 'r'`,
    );
    assert(tables.length >= 24, `only ${tables.length} tables`);
    for (const table of tables) assert(table.row_security, `RLS is off on ${table.table_name}`);
  } finally {
    await test.close();
  }
});

Deno.test("every function of the API is security definer with an empty search_path and closed to the app", async () => {
  const test = await createTestDatabase();
  try {
    const functions = await test.database.query<
      { name: string; security_definer: boolean; settings: string[] | null; app_can_execute: boolean }
    >(
      `select p.proname as name, p.prosecdef as security_definer, p.proconfig as settings,
              (has_function_privilege('anon', p.oid, 'execute') or has_function_privilege('authenticated', p.oid, 'execute'))
                as app_can_execute
       from pg_proc p join pg_namespace n on n.oid = p.pronamespace
       where n.nspname = 'public' and p.proname like 'api\\_%'`,
    );
    assert(functions.length >= 10);
    for (const entry of functions) {
      assert(entry.security_definer, `${entry.name} is not security definer`);
      assert((entry.settings ?? []).includes('search_path=""'), `${entry.name} has no fixed search_path`);
      assertEquals(entry.app_can_execute, false, `${entry.name} can be executed by the app`);
    }
  } finally {
    await test.close();
  }
});

Deno.test("row level security: a user reads only their own analyses and cannot write", async () => {
  const test = await createTestDatabase();
  try {
    const alice = await test.createUser();
    const bob = await test.createUser();
    for (const user of [alice, bob]) {
      await test.database.query(`insert into public.analyses (user_id, input_kind) values ($1, 'manual')`, [user]);
    }
    await test.client.exec(`set role authenticated`);
    await test.client.query(`select set_config('request.jwt.claim.sub', $1, false)`, [alice]);
    const visible = await test.client.query<{ user_id: string }>(`select user_id from public.analyses`);
    assertEquals(visible.rows.map((row) => row.user_id), [alice]);
    await assertRejects(() =>
      test.client.query(`insert into public.analyses (user_id, input_kind) values ($1, 'manual')`, [alice])
    );
    await assertRejects(() => test.client.query(`select * from public.offers`));
    await assertRejects(() => test.client.query(`select * from public.jobs`));
    await test.client.exec(`reset role`);
    await test.client.exec(`set role anon`);
    await assertRejects(() => test.client.query(`select * from public.analyses`));
  } finally {
    await test.client.exec(`reset role`);
    await test.close();
  }
});

Deno.test("a new job wakes the worker through pg_net when the Vault secrets exist", async () => {
  const test = await createTestDatabase();
  try {
    await test.database.query(`insert into public.jobs (kind) values ('evaluate_alerts')`);
    assertEquals((await test.database.query(`select * from net.recorded_requests`)).length, 0);
    await test.database.query(
      `insert into vault.secrets values ('jauge_functions_url', 'https://example.supabase.co/functions/v1'),
       ('jauge_webhook_secret', 'secret-secret-secret-secret-secret')`,
    );
    await test.database.query(`insert into public.jobs (kind) values ('evaluate_alerts')`);
    const calls = await test.database.query<{ url: string }>(`select url from net.recorded_requests`);
    assertEquals(calls.map((call) => call.url), ["https://example.supabase.co/functions/v1/api/internal/worker"]);
  } finally {
    await test.close();
  }
});

Deno.test("work queue: a claimed job is not claimed twice until its lease expires", async () => {
  const test = await createTestDatabase();
  try {
    await test.database.query(`insert into public.jobs (kind) values ('evaluate_alerts')`);
    const first = await test.database.query(`select * from public.api_claim_jobs('worker-a', 5, 60)`);
    const second = await test.database.query(`select * from public.api_claim_jobs('worker-b', 5, 60)`);
    assertEquals(first.length, 1);
    assertEquals(second.length, 0);
    await test.database.query(`update public.jobs set lease_expires_at = now() - interval '1 second'`);
    const third = await test.database.query<{ attempts: number }>(
      `select * from public.api_claim_jobs('worker-b', 5, 60)`,
    );
    assertEquals(third[0].attempts, 2);
  } finally {
    await test.close();
  }
});

Deno.test("weekly quota and rate limit functions are atomic counters", async () => {
  const test = await createTestDatabase();
  try {
    const user = await test.createUser();
    const consume = () =>
      test.database.query<{ allowed: boolean }>(
        `select public.api_consume_weekly_quota($1, '2026-10-19', 1) as allowed`,
        [user],
      );
    assertEquals((await consume())[0].allowed, true);
    assertEquals((await consume())[0].allowed, false);
    await test.database.query(`select public.api_refund_weekly_quota($1, '2026-10-19')`, [user]);
    assertEquals((await consume())[0].allowed, true);

    const hit = () =>
      test.database.query<{ allowed: boolean; retry_after_seconds: number }>(
        `select * from public.api_hit_rate_limit('user:test', 60, 2)`,
      );
    assertEquals((await hit())[0].allowed, true);
    assertEquals((await hit())[0].allowed, true);
    const refused = (await hit())[0];
    assertEquals(refused.allowed, false);
    assert(refused.retry_after_seconds >= 1 && refused.retry_after_seconds <= 60);
  } finally {
    await test.close();
  }
});

Deno.test("deleting the data of a user leaves the other users untouched", async () => {
  const test = await createTestDatabase();
  try {
    const alice = await test.createUser();
    const bob = await test.createUser();
    for (const user of [alice, bob]) {
      await test.database.query(`insert into public.analyses (user_id, input_kind) values ($1, 'manual')`, [user]);
      await test.database.query(`insert into public.profiles (user_id) values ($1)`, [user]);
    }
    await test.database.query(`select public.api_delete_user_data($1)`, [alice]);
    const remaining = await test.database.query<{ user_id: string }>(`select user_id from public.analyses`);
    assertEquals(remaining.map((row) => row.user_id), [bob]);
    assertEquals((await test.database.query(`select * from public.profiles`)).length, 1);
  } finally {
    await test.close();
  }
});
