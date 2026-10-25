import { assert, assertEquals, assertRejects } from "@std/assert";
import { ProviderError } from "../providers/provider_types.ts";
import {
  GEMINI_EXTRACTION_ANSWER,
  jsonResponse,
  MISTRAL_EXTRACTION_ANSWER,
  recordedFetch,
} from "../testing/recorded_answers.ts";
import { GEMINI_PRICES, GeminiClient } from "./gemini_client.ts";
import { MISTRAL_PRICES, MistralClient } from "./mistral_client.ts";
import { costMicroUsd, type ModelRequest } from "./model_client.ts";
import { OFFER_EXTRACTION_SCHEMA } from "./prompts/v1/offer_extraction.ts";

const REQUEST: ModelRequest = {
  system: "rules",
  user: "data",
  image: { bytes: new Uint8Array([1, 2, 3]), mimeType: "image/png" },
  schema: OFFER_EXTRACTION_SCHEMA,
  schemaName: "extract_text",
  maximumOutputTokens: 700,
  temperature: 0,
  timeoutMilliseconds: 1000,
};

Deno.test("Gemini: structured output request, key in a header, usage read from the recorded answer", async () => {
  const recorded = recordedFetch([jsonResponse(GEMINI_EXTRACTION_ANSWER)]);
  const client = new GeminiClient(
    "test-key",
    "gemini-3.1-flash-lite",
    GEMINI_PRICES["gemini-3.1-flash-lite"],
    recorded.fetcher,
  );
  const reply = await client.generate(REQUEST);
  assertEquals(
    recorded.calls[0].url,
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent",
  );
  assertEquals(new Headers(recorded.calls[0].init?.headers).get("x-goog-api-key"), "test-key");
  const body = JSON.parse(String(recorded.calls[0].init?.body));
  assertEquals(body.generationConfig.responseMimeType, "application/json");
  assertEquals(body.generationConfig.responseSchema.type, "OBJECT");
  assertEquals(body.generationConfig.thinkingConfig, { thinkingLevel: "minimal" });
  assertEquals(body.contents[0].parts[1].inlineData, { mimeType: "image/png", data: "AQID" });
  assertEquals(reply.usage, { model: "gemini-3.1-flash-lite", inputTokens: 1012, outputTokens: 148 });
  assertEquals(JSON.parse(reply.text).company, "Atelier Sorel");
  // 1 012 x 0.25 + 148 x 1.5 micro dollars.
  assertEquals(costMicroUsd(client.pricing, reply.usage.inputTokens, reply.usage.outputTokens), 475);
});

Deno.test("Mistral: strict JSON schema request, usage read from the recorded answer", async () => {
  const recorded = recordedFetch([jsonResponse(MISTRAL_EXTRACTION_ANSWER)]);
  const client = new MistralClient(
    "test-key",
    "mistral-small-2603",
    MISTRAL_PRICES["mistral-small-2603"],
    recorded.fetcher,
  );
  const reply = await client.generate(REQUEST);
  assertEquals(recorded.calls[0].url, "https://api.mistral.ai/v1/chat/completions");
  assertEquals(new Headers(recorded.calls[0].init?.headers).get("Authorization"), "Bearer test-key");
  const body = JSON.parse(String(recorded.calls[0].init?.body));
  assertEquals(body.response_format.type, "json_schema");
  assertEquals(body.response_format.json_schema.strict, true);
  assertEquals(body.response_format.json_schema.schema.additionalProperties, false);
  assert(String(body.messages[1].content[1].image_url).startsWith("data:image/png;base64,"));
  assertEquals(reply.usage.inputTokens, 1088);
  assertEquals(reply.usage.outputTokens, 151);
});

Deno.test("HTTP errors: 429 and 5xx are retryable, 400 and 401 are not, timeouts are retryable", async () => {
  for (const [status, retryable] of [[429, true], [503, true], [400, false], [401, false]] as const) {
    const client = new GeminiClient(
      "k",
      "gemini-3.1-flash-lite",
      GEMINI_PRICES["gemini-3.1-flash-lite"],
      recordedFetch([jsonResponse({ error: {} }, status)]).fetcher,
    );
    const error = await assertRejects(() => client.generate(REQUEST), ProviderError);
    assertEquals([error.code, error.retryable], [`http_${status}`, retryable]);
  }
  const timeout = new MistralClient(
    "k",
    "mistral-small-2603",
    MISTRAL_PRICES["mistral-small-2603"],
    recordedFetch([new DOMException("timed out", "TimeoutError")]).fetcher,
  );
  const error = await assertRejects(() => timeout.generate(REQUEST), ProviderError);
  assertEquals([error.code, error.retryable], ["timeout", true]);
});

Deno.test("Gemini: a blocked prompt is not retried", async () => {
  const client = new GeminiClient(
    "k",
    "gemini-3.1-flash-lite",
    GEMINI_PRICES["gemini-3.1-flash-lite"],
    recordedFetch([jsonResponse({ promptFeedback: { blockReason: "SAFETY" }, usageMetadata: {} })]).fetcher,
  );
  const error = await assertRejects(() => client.generate(REQUEST), ProviderError);
  assertEquals([error.code, error.retryable], ["blocked", false]);
});
