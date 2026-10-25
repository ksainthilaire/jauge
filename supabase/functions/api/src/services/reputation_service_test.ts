import { assert, assertEquals } from "@std/assert";
import { LanguageModelAiProvider } from "../ai/language_model_ai_provider.ts";
import { ManualClock } from "../operations/clock.ts";
import { MemoryLogger } from "../operations/logger.ts";
import { SpendGuard } from "../operations/spend_guard.ts";
import type { SocialMention, SocialSearch } from "../providers/provider_types.ts";
import { createSimulatedProviders, RecordingAccountServices } from "../providers/simulated_providers.ts";
import { ReplayModelClient, reply } from "../testing/recorded_answers.ts";
import { createTestDatabase } from "../testing/test_database.ts";
import { companyKey, ReputationService } from "./reputation_service.ts";

/** Paid social search that counts its calls. */
class CountingSearch implements SocialSearch {
  readonly name = "apify_social";
  readonly paid = true;
  calls = 0;
  constructor(private readonly mentions: SocialMention[]) {}
  estimateCostMicroUsd(): number {
    return 45_000;
  }
  search() {
    this.calls += 1;
    return Promise.resolve({ mentions: this.mentions, costMicroUsd: 18_600 });
  }
}

const MENTION: SocialMention = {
  source: "reddit",
  url: "https://www.reddit.com/r/lyon/comments/1abcd2/a/",
  publishedOn: "2026-09-12",
  text: "Process rapide chez Atelier Sorel.",
};

const SUMMARY = {
  relevant: true,
  tone: "positive",
  summaryFrench: "Des messages publics évoquent un recrutement rapide.",
  summaryEnglish: "Some public posts mention a fast hiring process.",
  citedPostNumbers: [1],
};

async function setUp(
  mentions: SocialMention[],
  replies = [reply(SUMMARY, 800, 80), reply(SUMMARY)],
  dailyLimitUsd = 3,
) {
  const test = await createTestDatabase();
  const clock = new ManualClock(new Date("2026-10-25T10:00:00Z"));
  const logger = new MemoryLogger();
  const search = new CountingSearch(mentions);
  const client = new ReplayModelClient(replies);
  const providers = {
    ...createSimulatedProviders(),
    socialSearch: search,
    ai: new LanguageModelAiProvider(client, logger, { timeoutMilliseconds: 1000, maximumAttempts: 2 }),
  };
  const guard = new SpendGuard(
    test.database,
    clock,
    { dailyLimitUsd, warningRatio: 0.8, simulated: false },
    new RecordingAccountServices(),
    logger,
  );
  const service = new ReputationService(test.database, providers, guard, clock, logger, 30);
  return { test, clock, search, client, service };
}

const OFFER = { company: "Atelier Sorel SAS", jobTitle: "Chargé·e de marketing digital", city: "Lyon" };

Deno.test("the reputation of a company is searched once and served from the cache for 30 days, in both languages", async () => {
  const { test, clock, search, client, service } = await setUp([MENTION]);
  try {
    const french = await service.reputationFor(OFFER, "fr", null);
    assertEquals(french, {
      summary: "Des messages publics évoquent un recrutement rapide.",
      tone: "positive",
      mentionCount: 1,
      sources: ["reddit"],
      links: [{ source: "reddit", url: "https://www.reddit.com/r/lyon/comments/1abcd2/a/" }],
    });
    clock.advance(29 * 86_400_000);
    const english = await service.reputationFor({ ...OFFER, company: "atelier sorel" }, "en", null);
    assertEquals(english?.summary, "Some public posts mention a fast hiring process.");
    assertEquals([search.calls, client.requests.length], [1, 1], "one search and one AI call for the company");

    const costs = await test.database.query<{ operation: string; cost_micro_usd: number; input_tokens: number | null }>(
      `select operation, cost_micro_usd::integer as cost_micro_usd, input_tokens from public.provider_costs order by id`,
    );
    assertEquals(costs, [
      { operation: "social_search", cost_micro_usd: 18_600, input_tokens: null },
      { operation: "reputation", cost_micro_usd: 800 * 0.25 + 80 * 1.5, input_tokens: 800 },
    ]);

    clock.advance(2 * 86_400_000);
    await service.reputationFor(OFFER, "fr", null);
    assertEquals(search.calls, 2, "searched again after 30 days");
  } finally {
    await test.close();
  }
});

Deno.test("nothing found is cached too; the daily limit is not cached", async () => {
  const empty = await setUp([]);
  try {
    assertEquals(await empty.service.reputationFor(OFFER, "fr", null), null);
    assertEquals(await empty.service.reputationFor(OFFER, "fr", null), null);
    assertEquals(empty.search.calls, 1);
    const rows = await empty.test.database.query<{ found: boolean }>(`select found from public.company_reputations`);
    assertEquals(rows, [{ found: false }]);
  } finally {
    await empty.test.close();
  }

  const broke = await setUp([MENTION], [], 0.01);
  try {
    assertEquals(await broke.service.reputationFor(OFFER, "fr", null), null);
    assertEquals(broke.search.calls, 0, "over the daily limit nothing is searched");
    const rows = await broke.test.database.query(`select 1 from public.company_reputations`);
    assertEquals(rows.length, 0, "and nothing is cached, so the next day searches");
  } finally {
    await broke.test.close();
  }
});

Deno.test("the key of a company ignores case, accents and legal forms; no company means no search", async () => {
  assertEquals(await companyKey("Atelier Sorel SAS"), await companyKey("ATELIER SOREL"));
  assert((await companyKey("Atelier Sorel")) !== (await companyKey("Atelier Soleil")));
  const { test, search, service } = await setUp([MENTION]);
  try {
    assertEquals(await service.reputationFor({ ...OFFER, company: null }, "fr", null), null);
    assertEquals(search.calls, 0);
  } finally {
    await test.close();
  }
});
