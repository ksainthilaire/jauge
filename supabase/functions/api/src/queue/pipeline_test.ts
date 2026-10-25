import { assert, assertEquals } from "@std/assert";
import { createTestApplication } from "../testing/test_application.ts";
import { loadConfiguration } from "../configuration.ts";
import { createProviders } from "../providers/provider_registry.ts";
import {
  createSimulatedProviders,
  SimulatedOfferReader,
  SimulatedOfferSource,
} from "../providers/simulated_providers.ts";
import {
  type CollectionPoll,
  type CollectionStart,
  type OfferSource,
  ProviderError,
} from "../providers/provider_types.ts";

const LINK = "https://www.linkedin.com/jobs/view/3912345678";

/** A source whose run never finishes (an Apify actor stuck for minutes). */
class StuckSource implements OfferSource {
  readonly name = "stuck";
  readonly paid = true;
  estimateCostMicroUsd(): number {
    return 1000;
  }
  start(): Promise<CollectionStart> {
    return Promise.resolve({ kind: "started", runId: "run-1" });
  }
  poll(): Promise<CollectionPoll> {
    return Promise.resolve({ kind: "running" });
  }
}

/** A reader that fails a number of times with a retryable error, then reads. */
class FlakyReader extends SimulatedOfferReader {
  constructor(private failures: number) {
    super();
  }
  override readLink(url: string, site: string | null, today: string) {
    if (this.failures > 0) {
      this.failures -= 1;
      return Promise.reject(new ProviderError("flaky_reader", "http_503", true));
    }
    return super.readLink(url, site, today);
  }
}

Deno.test("near the deadline the analysis completes with the data at hand and a lower reliability", async () => {
  const api = await createTestApplication({
    providers: (accounts) => ({ ...createSimulatedProviders(accounts), offerSources: [new StuckSource()] }),
    pipelineSettings: { computeMarginSeconds: 10_000 },
  });
  try {
    const user = await api.test.createUser();
    const created = await api.request("POST", "/v1/analyses", { userId: user, body: { type: "link", url: LINK } });
    await api.runWorker();
    const analysis = await api.request("GET", `/v1/analyses/${created.body.id}`, { userId: user });
    assertEquals(analysis.body.status, "completed");
    assertEquals((analysis.body.result as { reliability: string }).reliability, "low");
    const slice = await api.test.database.query<{ status: string; lifetime: number }>(
      `select status, extract(epoch from expires_at - collected_at)::integer as lifetime from public.market_slices`,
    );
    assertEquals(slice[0], { status: "ready", lifetime: 86_400 }, "a partial market is kept one day only");
  } finally {
    await api.close();
  }
});

Deno.test("retryable provider errors are retried with a backoff, then the analysis completes", async () => {
  const api = await createTestApplication({
    providers: (accounts) => ({ ...createSimulatedProviders(accounts), offerReader: new FlakyReader(1) }),
  });
  try {
    const user = await api.test.createUser();
    const created = await api.request("POST", "/v1/analyses", { userId: user, body: { type: "link", url: LINK } });
    await api.runWorker();
    const job = await api.test.database.query<{ status: string; last_error: string; attempts: number }>(
      `select status, last_error, attempts from public.jobs`,
    );
    assertEquals(job[0], { status: "queued", last_error: "flaky_reader:http_503", attempts: 1 });
    await api.test.database.query(`update public.jobs set run_after = now()`);
    await api.runWorker();
    assertEquals(
      (await api.request("GET", `/v1/analyses/${created.body.id}`, { userId: user })).body.status,
      "completed",
    );
  } finally {
    await api.close();
  }
});

Deno.test("after the last attempt the analysis fails cleanly and the free analysis comes back", async () => {
  const api = await createTestApplication({
    providers: (accounts) => ({ ...createSimulatedProviders(accounts), offerReader: new FlakyReader(100) }),
  });
  try {
    const user = await api.test.createUser();
    const created = await api.request("POST", "/v1/analyses", { userId: user, body: { type: "link", url: LINK } });
    for (let round = 0; round < 8; round++) {
      await api.test.database.query(`update public.jobs set run_after = now() where status = 'queued'`);
      await api.runWorker();
    }
    const analysis = await api.request("GET", `/v1/analyses/${created.body.id}`, { userId: user });
    assertEquals(analysis.body.failure, { code: "internal", recognizedCompany: null });
    assertEquals((await api.request("GET", "/v1/subscription", { userId: user })).body.freeAnalysesLeftThisWeek, 1);
    assert(api.logger.lines.some((line) => line.event === "job_failed"));
  } finally {
    await api.close();
  }
});

Deno.test("over the daily limit, paid sources are skipped, the owner is warned once and users still get a result", async () => {
  const api = await createTestApplication({
    providers: (accounts) => ({
      ...createSimulatedProviders(accounts),
      mode: "live",
      offerSources: [
        new SimulatedOfferSource("linkedin", {
          paid: true,
          asynchronous: false,
          showsApplicants: true,
          costPerOfferMicroUsd: 1000,
        }),
      ],
    }),
    environment: { JAUGE_DAILY_SPEND_LIMIT_USD: "3" },
  });
  try {
    await api.test.database.query(
      `insert into public.provider_costs (provider, operation, cost_micro_usd) values ('linkedin', 'collect', 2990000)`,
    );
    const user = await api.test.createUser();
    const created = await api.request("POST", "/v1/analyses", { userId: user, body: { type: "link", url: LINK } });
    await api.runWorker();
    assertEquals(
      (await api.request("GET", `/v1/analyses/${created.body.id}`, { userId: user })).body.status,
      "completed",
    );
    const collections = await api.test.database.query<{ status: string; error_code: string }>(
      `select status, error_code from public.source_collections`,
    );
    assertEquals(collections, [{ status: "skipped", error_code: "daily_limit" }]);
    assertEquals(api.accountServices.calls.filter((call) => call.startsWith("alert:")), [
      "alert:Jauge: budget du jour atteint",
    ]);
  } finally {
    await api.close();
  }
});

Deno.test("two users asking for the same offer at the same time share one collection", async () => {
  const api = await createTestApplication();
  try {
    const alice = await api.test.createUser();
    const bob = await api.test.createUser();
    const first = await api.request("POST", "/v1/analyses", { userId: alice, body: { type: "link", url: LINK } });
    const second = await api.request("POST", "/v1/analyses", { userId: bob, body: { type: "link", url: LINK } });
    await api.runWorker();
    for (const [user, id] of [[alice, first.body.id], [bob, second.body.id]]) {
      assertEquals((await api.request("GET", `/v1/analyses/${id}`, { userId: String(user) })).body.status, "completed");
    }
    assertEquals((await api.test.database.query(`select * from public.offer_snapshots`)).length, 1);
    assertEquals((await api.test.database.query(`select * from public.market_slices`)).length, 1);
  } finally {
    await api.close();
  }
});

Deno.test("live mode without connectors suggests another way (screenshot) instead of failing badly", async () => {
  const live = createProviders(
    loadConfiguration((name) => ({ JAUGE_PROVIDER_MODE: "live" } as Record<string, string>)[name]),
  );
  const api = await createTestApplication({ providers: () => live });
  try {
    assertEquals(live.mode, "live");
    const user = await api.test.createUser();
    const created = await api.request("POST", "/v1/analyses", { userId: user, body: { type: "link", url: LINK } });
    await api.runWorker();
    const analysis = await api.request("GET", `/v1/analyses/${created.body.id}`, { userId: user });
    assertEquals(analysis.body.failure, { code: "unsupported_link", recognizedCompany: null });
    assertEquals((await api.request("GET", "/v1/subscription", { userId: user })).body.freeAnalysesLeftThisWeek, 1);
  } finally {
    await api.close();
  }
});
