import { assert, assertEquals } from "@std/assert";
import { LanguageModelAiProvider } from "../ai/language_model_ai_provider.ts";
import { MemoryLogger } from "../operations/logger.ts";
import { ProviderError, type SocialMention, type SocialSearch } from "../providers/provider_types.ts";
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

/** Words of the reputation that must never reach a response of the API. */
const REPUTATION_TRACES = ["reputation", "reddit", "x.com", "Des avis publics", "Some public posts", "Bonne ambiance"];

function assertNoReputation(body: unknown, where: string): void {
  const text = JSON.stringify(body);
  for (const trace of REPUTATION_TRACES) {
    assert(!text.includes(trace), `${where} must not contain "${trace}": ${text.slice(0, 200)}`);
  }
}

Deno.test("the reputation is internal context of the advice: never in any response, the advice may use it", async () => {
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
    assertNoReputation(created.body, "the creation");
    await api.runWorker();
    const analysis = await api.request("GET", `/v1/analyses/${created.body.id}`, { userId: user });
    const result = analysis.body.result as Record<string, Record<string, unknown>>;
    assertEquals(analysis.body.status, "completed");
    assertEquals("reputation" in result, false);
    assert(String(result.advice.text).includes("Prévois une relance"), "the advice uses the internal context");
    assert(Array.isArray(result.advice.missingSkills));
    assertNoReputation(analysis.body, "the analysis");

    // Collected and summarized once for the company (30 day cache), stored outside the result.
    const cached = await api.test.database.query<{ found: boolean; links: unknown }>(
      `select found, links from public.company_reputations`,
    );
    assertEquals(cached, [{ found: true, links: [] }]);
    const stored = await api.test.database.query<{ result: Record<string, unknown> }>(
      `select result from public.analysis_results`,
    );
    assertEquals("reputation" in stored[0].result, false, "not stored in the result either");

    const id = String(created.body.id);
    for (const path of ["", "/salary", "/skills", "/trend", "/twins"]) {
      const response = await api.request("GET", `/v1/analyses/${id}${path}`, { userId: user });
      assertNoReputation(response.body, `GET /v1/analyses/{id}${path}`);
    }
    assertNoReputation((await api.request("GET", "/v1/analyses", { userId: user })).body, "the list");
    const share = await api.request("POST", "/v1/shares", { userId: user, body: { analysisId: id, format: "link" } });
    assertNoReputation(share.body, "the share");
    const publicView = await api.request("GET", `/public/analyses/${id}`);
    assertEquals(publicView.status, 200);
    assertNoReputation(publicView.body, "the public page");

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
    assert(String(englishResult.advice.text).includes("follow up"));
    assertNoReputation(english.body, "the English analysis");
    assertEquals(search.calls, 1, "the company is searched once");
  } finally {
    await api.close();
  }
});

Deno.test("the reputation never leaks: results stored before the change, failed advice, failed analyses", async () => {
  const failingAdvice = new SimulatedAiProvider();
  failingAdvice.writeAdvice = () => Promise.reject(new ProviderError("simulated_ai", "server_error", true));
  const api = await createTestApplication({
    providers: (accounts) => ({
      ...createSimulatedProviders(accounts),
      socialSearch: new FixedSearch(),
      ai: failingAdvice,
    }),
  });
  try {
    const user = await api.test.createUser();
    await api.makePlus(user);
    const created = await api.request("POST", "/v1/analyses", { userId: user, body: { type: "link", url: LINK } });
    await api.runWorker();
    const id = String(created.body.id);
    const analysis = await api.request("GET", `/v1/analyses/${id}`, { userId: user });
    assertEquals(analysis.body.status, "completed");
    assertEquals((analysis.body.result as Record<string, unknown>).advice, null, "the advice failed");
    assertNoReputation(analysis.body, "an analysis whose advice failed");

    // A result stored by the previous version with a reputation and its links.
    await api.test.database.query(
      `update public.analysis_results
       set result = result || '{"reputation":{"summary":"Des avis publics","tone":"positive","mentionCount":2,
         "sources":["reddit"],"links":[{"source":"reddit","url":"https://www.reddit.com/r/a/1"}]}}'::jsonb
       where analysis_id = $1::uuid`,
      [id],
    );
    assertNoReputation((await api.request("GET", `/v1/analyses/${id}`, { userId: user })).body, "an old result");
    await api.request("POST", "/v1/shares", { userId: user, body: { analysisId: id, format: "link" } });
    assertNoReputation((await api.request("GET", `/public/analyses/${id}`)).body, "the public page of an old result");

    // A failed analysis and the errors of the API.
    const unreadable = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "text", text: "Bonne ambiance." },
    });
    await api.runWorker();
    if (unreadable.status < 400) {
      const failed = await api.request("GET", `/v1/analyses/${unreadable.body.id}`, { userId: user });
      assertEquals(failed.body.status, "failed");
      assertNoReputation(failed.body, "a failed analysis");
    } else {
      assertNoReputation(unreadable.body, "a refused analysis");
    }
    const missing = await api.request("GET", "/v1/analyses/00000000-0000-4000-8000-000000000000", { userId: user });
    assertEquals(missing.status, 404);
    assertNoReputation(missing.body, "an error");
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
