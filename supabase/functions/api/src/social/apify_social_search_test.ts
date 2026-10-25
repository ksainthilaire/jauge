import { assert, assertEquals, assertRejects } from "@std/assert";
import { MemoryLogger } from "../operations/logger.ts";
import { ProviderError } from "../providers/provider_types.ts";
import { jsonResponse, recordedFetch, REDDIT_ITEMS, X_ITEMS } from "../testing/recorded_answers.ts";
import { anonymizePost, anonymousPostLink } from "./anonymize.ts";
import { ApifySocialSearch, redditActor, xActor } from "./apify_social_search.ts";

const QUERY = { company: "Atelier Sorel", jobTitle: "Chargé·e de marketing digital", city: "Lyon" };

Deno.test("Reddit and X through Apify: posts about the company only, anonymized, cost per result", async () => {
  const recorded = recordedFetch([jsonResponse(REDDIT_ITEMS, 201), jsonResponse(X_ITEMS, 201)]);
  const search = new ApifySocialSearch("apify-token", [redditActor(), xActor()], new MemoryLogger(), recorded.fetcher);
  const found = await search.search(QUERY);

  assert(recorded.calls[0].url.startsWith(
    "https://api.apify.com/v2/acts/trudax~reddit-scraper-lite/run-sync-get-dataset-items?timeout=25&maxItems=12",
  ));
  assert(recorded.calls[0].url.includes("maxTotalChargeUsd="));
  assert(!recorded.calls[0].url.includes("apify-token"), "the token is never in the URL");
  assertEquals(new Headers(recorded.calls[0].init?.headers).get("Authorization"), "Bearer apify-token");
  assertEquals(JSON.parse(String(recorded.calls[0].init?.body)).searches, ['"Atelier Sorel"']);
  assertEquals(JSON.parse(String(recorded.calls[1].init?.body)).searchTerms, ['"Atelier Sorel" -filter:retweets']);

  assertEquals(found.mentions.map((mention) => [mention.source, mention.url]), [
    ["reddit", "https://www.reddit.com/r/lyon/comments/1abcd2/atelier_sorel_entretien/"],
    ["reddit", "https://www.reddit.com/r/lyon/comments/1abcd2/atelier_sorel_entretien/kx1/"],
    ["x", "https://x.com/i/status/1845000000000000001"],
  ]);
  const texts = found.mentions.map((mention) => mention.text).join(" ");
  for (const personal of ["invented_user", "moi@example.com", "invented_friend", "06 12 34 56 78", "Invented Person"]) {
    assert(!texts.includes(personal), `${personal} must not reach the AI`);
  }
  // Reddit: 4 results x 3 400 micro dollars; X: 2 results, minimum charge 5 000.
  assertEquals(found.costMicroUsd, 4 * 3400 + 5000);
  assertEquals(search.estimateCostMicroUsd(), 12 * 3400 + 20 * 250 + 0);
});

Deno.test("one network down: the other one still answers; both down: retryable error", async () => {
  const logger = new MemoryLogger();
  const partial = new ApifySocialSearch(
    "t",
    [redditActor(), xActor()],
    logger,
    recordedFetch([jsonResponse({ error: {} }, 408), jsonResponse(X_ITEMS, 201)]).fetcher,
  );
  const found = await partial.search(QUERY);
  assertEquals(found.mentions.length, 1);
  assertEquals(logger.lines[0].fields, { source: "reddit", error: "http_408" });

  const down = new ApifySocialSearch(
    "t",
    [redditActor(), xActor()],
    logger,
    recordedFetch([jsonResponse({}, 502), new TypeError("network")]).fetcher,
  );
  const error = await assertRejects(() => down.search(QUERY), ProviderError);
  assertEquals([error.code, error.retryable], ["all_sources_failed", true]);
});

Deno.test("without a company nothing is searched (and nothing is paid)", async () => {
  const recorded = recordedFetch([]);
  const search = new ApifySocialSearch("t", [redditActor(), xActor()], new MemoryLogger(), recorded.fetcher);
  assertEquals(await search.search({ ...QUERY, company: null }), { mentions: [], costMicroUsd: 0 });
  assertEquals(recorded.calls.length, 0);
});

Deno.test("anonymization: extracts are short and links carry no username", () => {
  assertEquals(
    anonymizePost("Merci @someone et u/other, écrivez à a.b@example.fr"),
    "Merci [pseudo] et [pseudo], écrivez à [email]",
  );
  assert(anonymizePost("mot ".repeat(300)).length <= 403);
  assertEquals(
    anonymousPostLink("x", "https://twitter.com/someone/status/1845000000000000001?s=20"),
    "https://x.com/i/status/1845000000000000001",
  );
  assertEquals(
    anonymousPostLink("reddit", "https://old.reddit.com/r/lyon/comments/1abcd2/t/?utm=1"),
    "https://www.reddit.com/r/lyon/comments/1abcd2/t/",
  );
  assertEquals(anonymousPostLink("reddit", "https://www.reddit.com/user/someone/"), null);
  assertEquals(anonymousPostLink("reddit", "https://evil.example/r/lyon"), null);
});
