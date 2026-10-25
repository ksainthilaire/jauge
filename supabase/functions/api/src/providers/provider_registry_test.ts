import { assertEquals, assertRejects } from "@std/assert";
import { loadConfiguration } from "../configuration.ts";
import { createProviders } from "./provider_registry.ts";
import { ProviderError } from "./provider_types.ts";

const configuration = (environment: Record<string, string>) => loadConfiguration((name) => environment[name]);

Deno.test("without keys the providers stay simulated (default), and live mode without keys is not connected", async () => {
  assertEquals(createProviders(configuration({ GEMINI_API_KEY: "k", APIFY_TOKEN: "t" })).ai.name, "simulated_ai");
  const live = createProviders(configuration({ JAUGE_PROVIDER_MODE: "live" }));
  await assertRejects(() => live.ai.writeAdvice({} as never), ProviderError);
  await assertRejects(() => live.socialSearch.search({ company: "a", jobTitle: "b", city: null }), ProviderError);
});

Deno.test("live mode: Gemini by default, Mistral by configuration, Apify social search with a token", () => {
  const gemini = createProviders(configuration({ JAUGE_PROVIDER_MODE: "live", GEMINI_API_KEY: "k", APIFY_TOKEN: "t" }));
  assertEquals([gemini.ai.name, gemini.socialSearch.name], ["gemini", "apify_social"]);
  assertEquals(gemini.ai.estimateCostMicroUsd("advice"), 1800 * 0.25 + 450 * 1.5);

  const mistral = createProviders(
    configuration({ JAUGE_PROVIDER_MODE: "live", AI_PROVIDER: "mistral", MISTRAL_API_KEY: "k" }),
  );
  assertEquals(mistral.ai.name, "mistral");
  assertEquals(mistral.ai.estimateCostMicroUsd("advice"), 1800 * 0.15 + 450 * 0.6);

  // A model without a known price is refused unless its prices are configured (its cost must be checkable).
  const unknown = { JAUGE_PROVIDER_MODE: "live", GEMINI_API_KEY: "k", GEMINI_MODEL: "gemini-9-flash-lite" };
  assertEquals(createProviders(configuration(unknown)).ai.name, "ai");
  const priced = createProviders(
    configuration({
      ...unknown,
      AI_INPUT_USD_PER_MILLION_TOKENS: "0.5",
      AI_OUTPUT_USD_PER_MILLION_TOKENS: "2",
    }),
  );
  assertEquals(priced.ai.estimateCostMicroUsd("advice"), 1800 * 0.5 + 450 * 2);

  const xOnly = createProviders(
    configuration({ JAUGE_PROVIDER_MODE: "live", APIFY_TOKEN: "t", JAUGE_SOCIAL_SOURCES: "x" }),
  );
  assertEquals(xOnly.socialSearch.estimateCostMicroUsd(), 5000);
});
