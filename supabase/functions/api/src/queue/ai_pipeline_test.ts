import { assert, assertEquals } from "@std/assert";
import { LanguageModelAiProvider } from "../ai/language_model_ai_provider.ts";
import { MemoryLogger } from "../operations/logger.ts";
import type { SocialMention, SocialSearch } from "../providers/provider_types.ts";
import { createSimulatedProviders, SimulatedAiProvider } from "../providers/simulated_providers.ts";
import { GOOD_ADVICE, ReplayModelClient, reply } from "../testing/recorded_answers.ts";
import { createTestApplication } from "../testing/test_application.ts";

const LINK = "https://www.linkedin.com/jobs/view/3912345678";

/** Social search that always finds the same two posts and counts its calls. */
class FixedSearch implements SocialSearch {
  readonly name = "fixed_social";
  readonly paid = false;
  calls = 0;
  estimateCostMicroUsd(): number {
    return 0;
  }
  search(): Promise<{ mentions: readonly SocialMention[]; costMicroUsd: number }> {
    this.calls += 1;
    return Promise.resolve({
      mentions: [
        {
          source: "reddit",
          url: "https://www.reddit.com/r/lyon/comments/1abcd2/a/",
          publishedOn: null,
          text: "Bonne ambiance.",
        },
        { source: "x", url: "https://x.com/i/status/1845000000000000001", publishedOn: null, text: "Bonne réponse." },
      ],
      costMicroUsd: 0,
    });
  }
}

Deno.test("a completed analysis carries the advice and the reputation with its links, in the user's language", async () => {
  const search = new FixedSearch();
  const api = await createTestApplication({
    providers: (accounts) => ({ ...createSimulatedProviders(accounts), socialSearch: search }),
  });
  try {
    const user = await api.test.createUser();
    const created = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "link", url: LINK },
      headers: { "Accept-Language": "fr-FR" },
    });
    await api.runWorker();
    const analysis = await api.request("GET", `/v1/analyses/${created.body.id}`, { userId: user });
    const result = analysis.body.result as Record<string, Record<string, unknown>>;
    assertEquals(analysis.body.status, "completed");
    assertEquals(result.reputation.tone, "positive");
    assertEquals(result.reputation.links, [
      { source: "reddit", url: "https://www.reddit.com/r/lyon/comments/1abcd2/a/" },
      { source: "x", url: "https://x.com/i/status/1845000000000000001" },
    ]);
    assert(String(result.reputation.summary).startsWith("Des avis publics"));
    assert(typeof result.advice.text === "string" && String(result.advice.text).length > 10);
    assert(Array.isArray(result.advice.missingSkills));

    // Another user, in English, on the same offer: snapshot and reputation from the caches, advice in English.
    const other = await api.test.createUser();
    const second = await api.request("POST", "/v1/analyses", {
      userId: other,
      body: { type: "link", url: LINK },
      headers: { "Accept-Language": "en-US" },
    });
    await api.runWorker();
    const english = await api.request("GET", `/v1/analyses/${second.body.id}`, { userId: other });
    const englishResult = english.body.result as Record<string, Record<string, unknown>>;
    assert(String(englishResult.reputation.summary).startsWith("Some public posts"));
    assertEquals(search.calls, 1, "the company is searched once");
  } finally {
    await api.close();
  }
});

Deno.test("with a live AI, the advice costs and tokens are journaled; a rejected advice leaves the analysis complete", async () => {
  const logger = new MemoryLogger();
  // Advice replies: one valid for the first analysis (no number, so whatever the facts), then two invalid
  // (invented number) for the second one.
  const valid = {
    advice: "Postule dès maintenant : cette offre attire beaucoup de candidats.",
    negotiationArgument: null,
    missingSkills: [],
  };
  const client = new ReplayModelClient([
    reply(valid, 1700, 140),
    reply({ ...GOOD_ADVICE, advice: "Postule vite : 999 candidats." }),
    reply({ ...GOOD_ADVICE, advice: "Postule vite : 999 candidats." }),
  ]);
  const live = new LanguageModelAiProvider(client, logger, { timeoutMilliseconds: 1000, maximumAttempts: 2 });
  const simulated = new SimulatedAiProvider();
  const ai = {
    name: live.name,
    estimateCostMicroUsd: live.estimateCostMicroUsd.bind(live),
    extractOfferFromText: simulated.extractOfferFromText.bind(simulated),
    extractOfferFromImage: simulated.extractOfferFromImage.bind(simulated),
    summarizeReputation: simulated.summarizeReputation.bind(simulated),
    writeAdvice: live.writeAdvice.bind(live),
  };
  const api = await createTestApplication({
    providers: (accounts) => ({ ...createSimulatedProviders(accounts), socialSearch: new FixedSearch(), ai }),
  });
  try {
    const user = await api.test.createUser();
    await api.makePlus(user);
    const first = await api.request("POST", "/v1/analyses", { userId: user, body: { type: "link", url: LINK } });
    await api.runWorker();
    const second = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "link", url: "https://www.linkedin.com/jobs/view/3912345679" },
    });
    await api.runWorker();
    const analyses = await Promise.all(
      [first, second].map((created) => api.request("GET", `/v1/analyses/${created.body.id}`, { userId: user })),
    );
    assertEquals(analyses.map((analysis) => analysis.body.status), ["completed", "completed"]);
    assert(analyses[0].body.result && (analyses[0].body.result as Record<string, unknown>).advice !== null);
    assertEquals((analyses[1].body.result as Record<string, unknown>).advice, null);
    const costs = await api.test.database.query<{ operation: string; model: string; input_tokens: number }>(
      `select operation, model, input_tokens from public.provider_costs where operation = 'advice' order by id`,
    );
    assertEquals(costs.map((cost) => cost.input_tokens), [1700, 2000]);
    assertEquals(costs[0].model, "gemini-3.1-flash-lite");
  } finally {
    await api.close();
  }
});
