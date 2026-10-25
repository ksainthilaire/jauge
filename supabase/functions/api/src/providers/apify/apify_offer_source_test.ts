import { assert, assertEquals, assertRejects } from "@std/assert";
import { FakeApify, readJsonFixture } from "../../testing/fake_apify.ts";
import type { CollectionRequest } from "../provider_types.ts";
import { ProviderError } from "../provider_types.ts";
import { HELLOWORK_ACTOR, INDEED_ACTOR, INDEED_OFFER_ACTOR, LINKEDIN_ACTOR } from "./actor_catalog.ts";
import { ApifyClient, parseRun } from "./apify_client.ts";
import { ApifyOfferActorReader, ApifyOfferSource } from "./apify_offer_source.ts";
import { estimateRunCostMicroUsd, runCostMicroUsd } from "./apify_pricing.ts";

const TODAY = "2026-10-25";
const REQUEST: CollectionRequest = {
  jobTitle: "Chargé de marketing digital",
  jobFamily: "charge marketing digital",
  city: "Lyon",
  departmentCode: "69",
  radiusKilometers: 30,
  maximumOffers: 60,
  today: TODAY,
};
const noWait = () => Promise.resolve();
const settings = { tier: "BRONZE" as const, webhook: null, today: () => TODAY };

Deno.test("prices: estimate is the cap of a run, the real cost comes from the charged events", async () => {
  // LinkedIn on Starter: 60 results at 0.001 USD plus one start at 0.00005 USD.
  assertEquals(estimateRunCostMicroUsd(LINKEDIN_ACTOR.prices, "BRONZE", 60, 1024), 60_050);
  assertEquals(estimateRunCostMicroUsd(LINKEDIN_ACTOR.prices, "FREE", 60, 1024), 120_050);
  assertEquals(estimateRunCostMicroUsd(INDEED_ACTOR.prices, "BRONZE", 60, 1024), 300_000);
  assertEquals(estimateRunCostMicroUsd(INDEED_ACTOR.prices, "GOLD", 60, 1024), 180_000);
  assertEquals(estimateRunCostMicroUsd(HELLOWORK_ACTOR.prices, "SILVER", 60, 1024), 48_050);
  const succeeded = parseRun(await readJsonFixture("apify/fixtures/run_succeeded.json"));
  assertEquals(succeeded.status, "SUCCEEDED");
  // 6 results at the BRONZE price of the run (0.001) and one start (0.00005); platform usage is not billed.
  assertEquals(runCostMicroUsd(succeeded, LINKEDIN_ACTOR.prices, "BRONZE", 6, 1024), 6_050);
  assertEquals(runCostMicroUsd(succeeded, LINKEDIN_ACTOR.prices, "FREE", 6, 1024), 12_050);
  // Without prices in the run, the catalog prices are used (Indeed: "result" event).
  const timedOut = parseRun(await readJsonFixture("apify/fixtures/run_timed_out.json"));
  assertEquals(runCostMicroUsd(timedOut, INDEED_ACTOR.prices, "BRONZE", 3, 1024), 15_000);
});

Deno.test("client: token in the header, caps and webhook in the query, never in a log", async () => {
  const apify = new FakeApify({ "curious_coder~linkedin-jobs-scraper": { items: [] } });
  const source = new ApifyOfferSource(LINKEDIN_ACTOR, new ApifyClient("apify_api_secret", apify.fetcher, {}, noWait), {
    ...settings,
    webhook: { url: "https://project.supabase.co/functions/v1/api/webhooks/apify", secret: "s".repeat(40) },
  });
  const start = await source.start(REQUEST);
  assertEquals(start, { kind: "started", runId: "run00000001" });
  const call = apify.apifyCalls[0];
  assertEquals(call.url.pathname, "/v2/acts/curious_coder~linkedin-jobs-scraper/runs");
  assertEquals(call.headers.get("authorization"), "Bearer apify_api_secret");
  assert(!call.url.toString().includes("apify_api_secret"));
  assertEquals(call.url.searchParams.get("maxItems"), "60");
  assertEquals(call.url.searchParams.get("maxTotalChargeUsd"), "0.060050");
  assertEquals(call.url.searchParams.get("timeout"), "240");
  assertEquals(call.url.searchParams.get("memory"), "1024");
  const webhooks = JSON.parse(atob(call.url.searchParams.get("webhooks")!));
  assertEquals(webhooks[0].requestUrl, "https://project.supabase.co/functions/v1/api/webhooks/apify");
  assertEquals(webhooks[0].eventTypes.includes("ACTOR.RUN.SUCCEEDED"), true);
  assertEquals(JSON.parse(webhooks[0].headersTemplate), { "x-jauge-apify-secret": "s".repeat(40) });
  assertEquals((call.body as { limitPerSource: number }).limitPerSource, 60);
});

Deno.test("client: reads are retried on 5xx, 429 and network errors; a start only on 429 and 5xx", async () => {
  const apify = new FakeApify({ "sbzh~hellowork-jobs-scraper": { items: [], runningPolls: 0 } });
  const client = new ApifyClient("token", apify.fetcher, {}, noWait);
  apify.failures.push(503, 429);
  const run = await client.startRun("sbzh~hellowork-jobs-scraper", {}, {
    maximumItems: 10,
    maximumTotalChargeUsd: 0.01,
    timeoutSeconds: 60,
    memoryMegabytes: 1024,
  });
  assertEquals(apify.startsOf("sbzh~hellowork-jobs-scraper").length, 3, "two refusals, then the run");
  apify.failures.push("network", 502);
  assertEquals((await client.getRun(run.id)).status, "SUCCEEDED");
  // A network error during a start may have created a paid run: no blind retry.
  apify.failures.push("network");
  const error = await assertRejects(
    () =>
      client.startRun("sbzh~hellowork-jobs-scraper", {}, {
        maximumItems: 10,
        maximumTotalChargeUsd: 0.01,
        timeoutSeconds: 60,
        memoryMegabytes: 1024,
      }),
    ProviderError,
  );
  assertEquals([error.code, error.retryable], ["network", true]);
  assertEquals(apify.startsOf("sbzh~hellowork-jobs-scraper").length, 4);
  // Wrong token, no credit: not retried, not retryable.
  apify.failures.push(401);
  assertEquals((await assertRejects(() => client.getRun(run.id), ProviderError)).retryable, false);
  apify.failures.push(402);
  assertEquals((await assertRejects(() => client.getRun(run.id), ProviderError)).code, "payment_required");
  apify.failures.push(500, 500, 500);
  assertEquals((await assertRejects(() => client.getRun(run.id), ProviderError)).code, "http_500");
});

Deno.test("source: running, then finished with mapped offers and the real cost", async () => {
  const items = await readJsonFixture<unknown[]>("apify/fixtures/linkedin_items.json");
  const apify = new FakeApify({ "curious_coder~linkedin-jobs-scraper": { items, runningPolls: 1 } });
  const source = new ApifyOfferSource(LINKEDIN_ACTOR, new ApifyClient("token", apify.fetcher, {}, noWait), settings);
  assertEquals(source.estimateCostMicroUsd(REQUEST), 60_050);
  assertEquals(source.estimateCostMicroUsd({ ...REQUEST, maximumOffers: 500 }), 100_050, "capped by the catalog");
  const start = await source.start(REQUEST);
  if (start.kind !== "started") throw new Error("not started");
  assertEquals(await source.poll(start.runId), { kind: "running" });
  const finished = await source.poll(start.runId);
  if (finished.kind !== "finished") throw new Error("not finished");
  assertEquals(finished.offers.length, 3);
  assertEquals(finished.costMicroUsd, 6_050, "6 results charged (even the unusable ones) and one start");
  const itemsCall = apify.apifyCalls.find((call) => call.url.pathname.endsWith("/items"))!;
  assertEquals(itemsCall.url.searchParams.get("fields"), LINKEDIN_ACTOR.fields.join(","));
  assertEquals(itemsCall.url.searchParams.get("clean"), "true");
});

Deno.test("source: a timed out run keeps what it found, a failed one reports its cost, a hiccup waits", async () => {
  const items = await readJsonFixture<unknown[]>("apify/fixtures/indeed_items.json");
  const apify = new FakeApify({
    "misceres~indeed-scraper": { items, runningPolls: 0, finalStatus: "TIMED-OUT", resultUsd: 0.005 },
    "sbzh~hellowork-jobs-scraper": { items: [], runningPolls: 0, finalStatus: "FAILED" },
  });
  const client = new ApifyClient("token", apify.fetcher, { retries: 0 }, noWait);
  const indeed = new ApifyOfferSource(INDEED_ACTOR, client, settings, true);
  assertEquals(indeed.complementary, true);
  const indeedRun = await indeed.start(REQUEST);
  if (indeedRun.kind !== "started") throw new Error("not started");
  const partial = await indeed.poll(indeedRun.runId);
  assertEquals(partial.kind, "finished");
  if (partial.kind === "finished") assertEquals(partial.offers.length, 3);

  const hellowork = new ApifyOfferSource(HELLOWORK_ACTOR, client, settings);
  const helloworkRun = await hellowork.start(REQUEST);
  if (helloworkRun.kind !== "started") throw new Error("not started");
  apify.failures.push(503);
  assertEquals(await hellowork.poll(helloworkRun.runId), { kind: "running" }, "a 503 is not the end of the run");
  assertEquals(await hellowork.poll(helloworkRun.runId), { kind: "failed", code: "run_failed", costMicroUsd: 50 });
  assertEquals(await hellowork.poll("unknownRun0001"), { kind: "failed", code: "not_found", costMicroUsd: 0 });
});

Deno.test("one offer read by an actor: wait for the run, abort it when it is too slow", async () => {
  const items = await readJsonFixture<unknown[]>("apify/fixtures/indeed_items.json");
  const quick = new FakeApify({ "misceres~indeed-scraper": { items, runningPolls: 0, resultUsd: 0.005 } });
  const reader = new ApifyOfferActorReader(
    INDEED_OFFER_ACTOR,
    new ApifyClient("token", quick.fetcher, {}, noWait),
    "BRONZE",
  );
  assertEquals(reader.estimateCostMicroUsd(), 5_000);
  const reading = await reader.read("https://fr.indeed.com/viewjob?jk=a1b2c3d4e5f60718", TODAY);
  assertEquals(reading.offer?.jobTitle, "Chargé de marketing digital H/F");
  assertEquals(reading.offer?.displayedSalary, { minimum: 36, maximum: 40 });
  assertEquals(reading.costMicroUsd, 5_050);
  assertEquals(quick.startsOf("misceres~indeed-scraper")[0].url.searchParams.get("waitForFinish"), "30");

  const slow = new FakeApify({ "misceres~indeed-scraper": { items, runningPolls: 5 } });
  const slowReader = new ApifyOfferActorReader(
    INDEED_OFFER_ACTOR,
    new ApifyClient("token", slow.fetcher, {}, noWait),
    "BRONZE",
  );
  const late = await slowReader.read("https://fr.indeed.com/viewjob?jk=a1b2c3d4e5f60718", TODAY);
  assertEquals(late, { offer: null, costMicroUsd: 5_000 });
  assert(slow.apifyCalls.some((call) => call.url.pathname.endsWith("/abort")));
});
