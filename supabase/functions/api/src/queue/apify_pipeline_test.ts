import { assert, assertEquals } from "@std/assert";
import { loadConfiguration } from "../configuration.ts";
import { createProviders } from "../providers/provider_registry.ts";
import { FakeApify, readFixture, readJsonFixture } from "../testing/fake_apify.ts";
import { createTestApplication } from "../testing/test_application.ts";

const LINK = "https://www.linkedin.com/jobs/view/4012345678";
const WEBHOOK_SECRET = "apify-webhook-secret-apify-webhook-secret";
const LINKEDIN = "curious_coder~linkedin-jobs-scraper";
const HELLOWORK = "sbzh~hellowork-jobs-scraper";
const INDEED = "misceres~indeed-scraper";

/** A test API in live mode whose Apify and job sites are the recorded fake. */
async function liveApplication(
  environment: Record<string, string>,
  behavior: { linkedinPolls?: number; helloworkPolls?: number } = {},
  pipelineSettings: { computeMarginSeconds?: number; collectionPollSeconds?: number } = {},
) {
  const apify = new FakeApify({
    [LINKEDIN]: {
      items: await readJsonFixture("apify/fixtures/linkedin_items.json"),
      runningPolls: behavior.linkedinPolls ?? 2,
    },
    [HELLOWORK]: {
      items: await readJsonFixture("apify/fixtures/hellowork_items.json"),
      runningPolls: behavior.helloworkPolls ?? 1,
      resultUsd: 0.0008,
    },
    [INDEED]: { items: await readJsonFixture("apify/fixtures/indeed_items.json"), runningPolls: 0, resultUsd: 0.005 },
  });
  apify.pages.set(LINK, await readFixture("offers/fixtures/linkedin_job.html"));
  const fullEnvironment = {
    JAUGE_PROVIDER_MODE: "live",
    APIFY_TOKEN: "apify_api_test_token",
    APIFY_WEBHOOK_URL: "https://project.supabase.co/functions/v1/api/webhooks/apify",
    APIFY_WEBHOOK_SECRET: WEBHOOK_SECRET,
    // The social search (Reddit, X) also uses Apify: tested in its own lot.
    JAUGE_SOCIAL_SOURCES: "none",
    ...environment,
  };
  const configuration = loadConfiguration((name) => fullEnvironment[name as keyof typeof fullEnvironment]);
  const providers = createProviders(configuration, {}, apify.fetcher, undefined, () => Promise.resolve());
  const api = await createTestApplication({
    environment: fullEnvironment,
    providers: () => providers,
    pipelineSettings,
  });
  return { api, apify, providers };
}

Deno.test("live mode with an Apify token: LinkedIn and HelloWork, then Indeed for a thin market", async () => {
  const { api, apify, providers } = await liveApplication({});
  try {
    assertEquals(providers.mode, "live");
    assertEquals(providers.offerSources.map((source) => [source.name, source.complementary === true]), [
      ["linkedin", false],
      ["hellowork", false],
      ["indeed", true],
    ]);
    const user = await api.test.createUser();
    const created = await api.request("POST", "/v1/analyses", { userId: user, body: { type: "link", url: LINK } });
    assertEquals(created.status, 202);
    await api.runWorker();
    const analysis = await api.request("GET", `/v1/analyses/${created.body.id}`, { userId: user });
    assertEquals(analysis.body.status, "completed");
    const offer = analysis.body.offer as { company: string; city: string };
    assertEquals([offer.company, offer.city], ["Atelier Sorel", "Lyon"]);
    const result = analysis.body.result as { comparableOfferCount: number; reliability: string };
    // 3 LinkedIn + 2 HelloWork + 3 Indeed offers, the Atelier Sorel offer on LinkedIn and Indeed counted once.
    assertEquals(result.comparableOfferCount, 7);

    const collections = await api.test.database.query<
      { provider: string; status: string; item_count: number; cost_micro_usd: number; reserved_micro_usd: number }
    >(
      `select provider, status, item_count, cost_micro_usd::integer as cost_micro_usd,
              reserved_micro_usd::integer as reserved_micro_usd
       from public.source_collections order by provider`,
    );
    assertEquals(collections, [
      { provider: "hellowork", status: "succeeded", item_count: 2, cost_micro_usd: 2_450, reserved_micro_usd: 0 },
      { provider: "indeed", status: "succeeded", item_count: 3, cost_micro_usd: 15_050, reserved_micro_usd: 0 },
      { provider: "linkedin", status: "succeeded", item_count: 3, cost_micro_usd: 6_050, reserved_micro_usd: 0 },
    ]);
    const costs = await api.test.database.query<
      { provider: string; operation: string; cost: number; simulated: boolean }
    >(
      `select provider, operation, sum(cost_micro_usd)::integer as cost, bool_or(simulated) as simulated
       from public.provider_costs group by provider, operation order by provider`,
    );
    assertEquals(costs, [
      { provider: "hellowork", operation: "collect", cost: 2_450, simulated: false },
      { provider: "indeed", operation: "collect", cost: 15_050, simulated: false },
      { provider: "linkedin", operation: "collect", cost: 6_050, simulated: false },
    ]);
    const stored = await api.test.database.query<{ source: string; count: number }>(
      `select source, count(*)::integer as count from public.collected_offers group by source order by source`,
    );
    assertEquals(stored, [
      { source: "hellowork", count: 2 },
      { source: "indeed", count: 3 },
      { source: "linkedin", count: 3 },
    ]);
    const applicants = await api.test.database.query<{ applicant_count: number }>(
      `select applicant_count from public.collected_offers where source = 'linkedin' and external_id = '4012345678'`,
    );
    assertEquals(applicants[0].applicant_count, 87);
    // The analyzed offer itself was read from its page (free): no Apify run for it.
    assertEquals(apify.startsOf(LINKEDIN).length, 1);
    assert(
      apify.startsOf(LINKEDIN)[0].url.searchParams.has("webhooks"),
      "runs carry the webhook that wakes the analysis",
    );

    // Same market a minute later: the market slice is reused, nothing is collected again.
    const second = await api.request("POST", "/v1/analyses", {
      userId: await api.test.createUser(),
      body: { type: "manual", jobTitle: "Chargé de marketing digital", city: "Lyon" },
    });
    await api.runWorker();
    assertEquals(apify.startsOf(LINKEDIN).length + apify.startsOf(HELLOWORK).length, 2);
    assertEquals(second.status, 202);
  } finally {
    await api.close();
  }
});

Deno.test("daily limit: running runs reserve their cap, so parallel sources cannot overshoot it", async () => {
  // 0.10 USD: LinkedIn (cap 0.06005) fits, HelloWork (0.04805) would go over with the reservation, Indeed too.
  const { api, apify } = await liveApplication({ JAUGE_DAILY_SPEND_LIMIT_USD: "0.1" });
  try {
    const user = await api.test.createUser();
    const created = await api.request("POST", "/v1/analyses", { userId: user, body: { type: "link", url: LINK } });
    await api.runWorker();
    assertEquals(
      (await api.request("GET", `/v1/analyses/${created.body.id}`, { userId: user })).body.status,
      "completed",
    );
    const collections = await api.test.database.query<{ provider: string; status: string; error_code: string | null }>(
      `select provider, status, error_code from public.source_collections order by provider`,
    );
    assertEquals(collections, [
      { provider: "hellowork", status: "skipped", error_code: "daily_limit" },
      { provider: "indeed", status: "skipped", error_code: "daily_limit" },
      { provider: "linkedin", status: "succeeded", error_code: null },
    ]);
    assertEquals(apify.startsOf(HELLOWORK).length, 0);
    const spent = await api.test.database.query<{ spent: number }>(
      `select public.api_daily_spend_micro_usd((now() at time zone 'utc')::date)::integer as spent`,
    );
    assert(spent[0].spent <= 100_000, `spent ${spent[0].spent}`);
    const alerts = await api.test.database.query<{ level: string }>(`select level from public.spend_alerts`);
    assertEquals(alerts.map((alert) => alert.level).sort(), ["limit_reached"]);
  } finally {
    await api.close();
  }
});

Deno.test("a run still going on at the deadline stays reserved, then the Apify webhook stores it", async () => {
  const { api, apify } = await liveApplication(
    { JAUGE_APIFY_COMPLEMENTARY_SOURCES: "" },
    { linkedinPolls: 1_000, helloworkPolls: 0 },
    { computeMarginSeconds: 10_000 },
  );
  try {
    const user = await api.test.createUser();
    const created = await api.request("POST", "/v1/analyses", { userId: user, body: { type: "link", url: LINK } });
    await api.runWorker();
    const analysis = await api.request("GET", `/v1/analyses/${created.body.id}`, { userId: user });
    assertEquals(analysis.body.status, "completed", "the user gets a result with the data at hand");
    const running = await api.test.database.query<{ external_run_id: string; reserved: number }>(
      `select external_run_id, reserved_micro_usd::integer as reserved from public.source_collections
       where status = 'running'`,
    );
    assertEquals(running.length, 1);
    assertEquals(running[0].reserved, 60_050);
    const spentWhileRunning = await api.test.database.query<{ spent: number }>(
      `select public.api_daily_spend_micro_usd((now() at time zone 'utc')::date)::integer as spent`,
    );
    assertEquals(spentWhileRunning[0].spent, 60_050 + 2_450, "reservation of LinkedIn plus the cost of HelloWork");

    const refused = await api.request("POST", "/webhooks/apify", {
      body: { eventType: "ACTOR.RUN.SUCCEEDED", eventData: { actorRunId: running[0].external_run_id } },
      headers: { "x-jauge-apify-secret": "wrong" },
    });
    assertEquals(refused.status, 401);

    apify.runs.get(running[0].external_run_id)!.pollsLeft = 0;
    const accepted = await api.request("POST", "/webhooks/apify", {
      body: {
        eventType: "ACTOR.RUN.SUCCEEDED",
        eventData: { actorId: "hKByXkMQaC5Qt9UMN", actorRunId: running[0].external_run_id },
        resource: { id: running[0].external_run_id, status: "SUCCEEDED" },
      },
      headers: { "x-jauge-apify-secret": WEBHOOK_SECRET },
    });
    assertEquals([accepted.status, accepted.body.status], [200, "stored"]);
    const linkedin = await api.test.database.query<{ status: string; reserved: number; item_count: number }>(
      `select status, reserved_micro_usd::integer as reserved, item_count from public.source_collections
       where provider = 'linkedin'`,
    );
    assertEquals(linkedin, [{ status: "succeeded", reserved: 0, item_count: 3 }]);
    const spent = await api.test.database.query<{ spent: number }>(
      `select public.api_daily_spend_micro_usd((now() at time zone 'utc')::date)::integer as spent`,
    );
    assertEquals(spent[0].spent, 6_050 + 2_450, "the reservation became the real cost");
    // A second delivery of the same webhook changes nothing (Apify retries webhooks).
    await api.request("POST", "/webhooks/apify", {
      body: { eventData: { actorRunId: running[0].external_run_id } },
      headers: { "x-jauge-apify-secret": WEBHOOK_SECRET },
    });
    const journal = await api.test.database.query<{ count: number }>(
      `select count(*)::integer as count from public.provider_costs where provider = 'linkedin'`,
    );
    assertEquals(journal[0].count, 1);
  } finally {
    await api.close();
  }
});

Deno.test("the Apify webhook wakes the analysis that waits for the run", async () => {
  const { api } = await liveApplication({ JAUGE_APIFY_COMPLEMENTARY_SOURCES: "" }, { linkedinPolls: 1_000 }, {
    collectionPollSeconds: 5,
  });
  try {
    const user = await api.test.createUser();
    await api.request("POST", "/v1/analyses", { userId: user, body: { type: "link", url: LINK } });
    // One pass of the worker: the analysis now waits for the LinkedIn run.
    await api.application.worker.run();
    await api.test.database.query(`update public.jobs set run_after = now() + interval '1 hour'`);
    const run = await api.test.database.query<{ external_run_id: string }>(
      `select external_run_id from public.source_collections where provider = 'linkedin' and status = 'running'`,
    );
    const woken = await api.request("POST", "/webhooks/apify", {
      body: { eventData: { actorRunId: run[0].external_run_id } },
      headers: { "x-jauge-apify-secret": WEBHOOK_SECRET },
    });
    assertEquals(woken.body.status, "woken");
    const due = await api.test.database.query<{ due: boolean }>(
      `select run_after <= now() as due from public.jobs where stage = 'market_wait'`,
    );
    assertEquals(due, [{ due: true }]);
    const invalid = await api.request("POST", "/webhooks/apify", {
      body: { eventData: { actorRunId: "../../etc" } },
      headers: { "x-jauge-apify-secret": WEBHOOK_SECRET },
    });
    assertEquals(invalid.status, 400);
  } finally {
    await api.close();
  }
});

Deno.test("the providers stay simulated until JAUGE_PROVIDER_MODE=live, even with an Apify token", () => {
  const configuration = loadConfiguration(() => undefined);
  assertEquals(configuration.providerMode, "simulated");
  assertEquals(configuration.apify.sources, ["linkedin", "hellowork"]);
  assertEquals(configuration.apify.complementarySources, ["indeed"]);
  const forced = loadConfiguration((name) =>
    ({
      APIFY_TOKEN: "token",
      JAUGE_APIFY_SOURCES: "linkedin, welcome_to_the_jungle, unknown",
      APIFY_PRICING_TIER: "gold",
    } as Record<string, string>)[name]
  );
  assertEquals(forced.providerMode, "simulated", "a key alone never turns the paid providers on");
  assertEquals(forced.apify.sources, ["linkedin", "welcome_to_the_jungle"]);
  assertEquals(forced.apify.pricingTier, "GOLD");
});
