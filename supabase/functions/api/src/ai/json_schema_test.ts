import { assert, assertEquals } from "@std/assert";
import { type SchemaNode, toGeminiSchema, toJsonSchema, validateAgainstSchema } from "./json_schema.ts";
import { ADVICE_SCHEMA } from "./prompts/v1/advice.ts";
import { OFFER_EXTRACTION_SCHEMA } from "./prompts/v1/offer_extraction.ts";
import { REPUTATION_SCHEMA } from "./prompts/v1/reputation.ts";

const SCHEMA: SchemaNode = {
  type: "object",
  properties: {
    name: { type: "string", maximumLength: 5 },
    tone: { type: "string", nullable: true, enum: ["positive", "negative"] },
    count: { type: "integer", minimum: 0, maximum: 10 },
    tags: { type: "array", items: { type: "string" }, maximumItems: 2 },
  },
};

Deno.test("validation accepts a complete answer and refuses every deviation", () => {
  assert(validateAgainstSchema(SCHEMA, { name: "abc", tone: null, count: 3, tags: ["a"] }).valid);
  const problems = [
    { tone: null, count: 3, tags: [] },
    { name: "abc", tone: null, count: 3, tags: [], extra: 1 },
    { name: "abcdef", tone: null, count: 3, tags: [] },
    { name: "abc", tone: "neutral", count: 3, tags: [] },
    { name: "abc", tone: null, count: 3.5, tags: [] },
    { name: "abc", tone: null, count: 11, tags: [] },
    { name: "abc", tone: null, count: 3, tags: ["a", "b", "c"] },
    { name: null, tone: null, count: 3, tags: [] },
    ["not", "an", "object"],
  ];
  for (const value of problems) {
    const result = validateAgainstSchema(SCHEMA, value);
    assert(!result.valid, JSON.stringify(value));
  }
});

Deno.test("the schemas sent to Gemini and Mistral close every object and keep nullability", () => {
  const gemini = toGeminiSchema(SCHEMA) as { required: string[]; properties: Record<string, Record<string, unknown>> };
  assertEquals(gemini.required, ["name", "tone", "count", "tags"]);
  assertEquals(gemini.properties.tone, {
    type: "STRING",
    nullable: true,
    format: "enum",
    enum: ["positive", "negative"],
  });
  const json = toJsonSchema(SCHEMA) as {
    additionalProperties: boolean;
    properties: Record<string, Record<string, unknown>>;
  };
  assertEquals(json.additionalProperties, false);
  assertEquals(json.properties.tone.type, ["string", "null"]);
  assertEquals(json.properties.tone.enum, ["positive", "negative", null]);
  for (const schema of [OFFER_EXTRACTION_SCHEMA, REPUTATION_SCHEMA, ADVICE_SCHEMA]) {
    assertEquals(schema.type, "object");
    assert(JSON.stringify(toJsonSchema(schema)).length > 100);
  }
});
