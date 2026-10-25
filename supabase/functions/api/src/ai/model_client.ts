import type { AiUsage } from "../providers/provider_types.ts";
import type { SchemaNode } from "./json_schema.ts";

/** Fetch function (injected so that tests replay recorded answers without network). */
export type { FetchFunction } from "../providers/live_account_services.ts";

/** Price of a model, in US dollars per million tokens. */
export interface ModelPricing {
  readonly inputUsdPerMillionTokens: number;
  readonly outputUsdPerMillionTokens: number;
}

/** Tokens used by one call (or by every attempt of an operation). */
export type TokenUsage = AiUsage;

/** An image given to the model with the text. */
export interface ModelImage {
  readonly bytes: Uint8Array;
  readonly mimeType: string;
}

/** One request of structured output. */
export interface ModelRequest {
  /** Instructions of Jauge (trusted). */
  readonly system: string;
  /** Message with the data (untrusted parts are fenced by the prompt builders). */
  readonly user: string;
  readonly image?: ModelImage;
  /** Expected shape of the answer. */
  readonly schema: SchemaNode;
  /** Name of the schema (Mistral asks for one). */
  readonly schemaName: string;
  readonly maximumOutputTokens: number;
  readonly temperature: number;
  readonly timeoutMilliseconds: number;
}

/** Raw answer of the model: the JSON text (not yet validated) and the tokens used. */
export interface ModelReply {
  readonly text: string;
  readonly usage: TokenUsage;
  /** True when the model stopped because of the output limit (the JSON is then probably cut). */
  readonly truncated: boolean;
}

/** A model that answers with JSON following a schema (Gemini, Mistral). */
export interface ModelClient {
  /** Provider name for the logs and the cost journal (`gemini`, `mistral`). */
  readonly name: string;
  /** Model identifier sent to the provider. */
  readonly model: string;
  readonly pricing: ModelPricing;
  /**
   * Sends one request. Throws `ProviderError` (retryable for network, timeout, 429 and 5xx; not retryable for
   * 400, 401, 403 and refusals).
   */
  generate(request: ModelRequest): Promise<ModelReply>;
}

/**
 * Cost of a number of tokens, in micro US dollars (rounded up so that the journal never under counts).
 *
 * @param pricing price of the model.
 * @param inputTokens input tokens.
 * @param outputTokens output tokens.
 */
export function costMicroUsd(pricing: ModelPricing, inputTokens: number, outputTokens: number): number {
  return Math.ceil(inputTokens * pricing.inputUsdPerMillionTokens + outputTokens * pricing.outputUsdPerMillionTokens);
}

/** Base64 of bytes (images sent inline to the models). */
export function toBase64(bytes: Uint8Array): string {
  let binary = "";
  const chunk = 0x8000;
  for (let index = 0; index < bytes.length; index += chunk) {
    binary += String.fromCharCode(...bytes.subarray(index, index + chunk));
  }
  return btoa(binary);
}

/** HTTP status to provider error code and retryable flag. */
export function classifyHttpStatus(status: number): { code: string; retryable: boolean } {
  return { code: `http_${status}`, retryable: status === 408 || status === 429 || status >= 500 };
}
