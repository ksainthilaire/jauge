import { ProviderError } from "../providers/provider_types.ts";
import { toGeminiSchema } from "./json_schema.ts";
import {
  classifyHttpStatus,
  type FetchFunction,
  type ModelClient,
  type ModelPricing,
  type ModelReply,
  type ModelRequest,
  toBase64,
} from "./model_client.ts";

/** Default model: the cheapest stable Flash-Lite open to new projects (October 2026, see docs/backend/ia.md). */
export const GEMINI_DEFAULT_MODEL = "gemini-3.1-flash-lite";

/** Public paid tier prices (USD per million tokens, text and image input), checked on 25/10/2026. */
export const GEMINI_PRICES: Readonly<Record<string, ModelPricing>> = {
  "gemini-3.1-flash-lite": { inputUsdPerMillionTokens: 0.25, outputUsdPerMillionTokens: 1.5 },
  "gemini-3.5-flash-lite": { inputUsdPerMillionTokens: 0.3, outputUsdPerMillionTokens: 2.5 },
  "gemini-2.5-flash-lite": { inputUsdPerMillionTokens: 0.1, outputUsdPerMillionTokens: 0.4 },
};

const GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

interface GeminiAnswer {
  candidates?: {
    content?: { parts?: { text?: string; thought?: boolean }[] };
    finishReason?: string;
  }[];
  promptFeedback?: { blockReason?: string };
  usageMetadata?: { promptTokenCount?: number; candidatesTokenCount?: number; thoughtsTokenCount?: number };
}

/**
 * Gemini Developer API (`generateContent`) with structured output (`responseMimeType: application/json` and
 * `responseSchema`). The key is sent in the `x-goog-api-key` header, never in the URL (URLs end up in logs).
 * On the paid tier, Google does not use prompts and answers to improve its products.
 */
export class GeminiClient implements ModelClient {
  readonly name = "gemini";

  /**
   * @param apiKey Gemini API key (secret `GEMINI_API_KEY`).
   * @param model model identifier.
   * @param pricing price of the model (from `GEMINI_PRICES`, or configured).
   * @param fetcher fetch function.
   */
  constructor(
    private readonly apiKey: string,
    readonly model: string,
    readonly pricing: ModelPricing,
    private readonly fetcher: FetchFunction = fetch,
  ) {}

  /** @inheritdoc */
  async generate(request: ModelRequest): Promise<ModelReply> {
    const parts: Record<string, unknown>[] = [{ text: request.user }];
    if (request.image) {
      parts.push({ inlineData: { mimeType: request.image.mimeType, data: toBase64(request.image.bytes) } });
    }
    const generationConfiguration: Record<string, unknown> = {
      responseMimeType: "application/json",
      responseSchema: toGeminiSchema(request.schema),
      temperature: request.temperature,
      maxOutputTokens: request.maximumOutputTokens,
    };
    // Thinking costs output tokens and adds nothing to these short tasks: as low as the model allows.
    if (this.model.startsWith("gemini-2.5")) generationConfiguration.thinkingConfig = { thinkingBudget: 0 };
    else if (this.model.startsWith("gemini-3")) generationConfiguration.thinkingConfig = { thinkingLevel: "minimal" };
    const body = {
      systemInstruction: { parts: [{ text: request.system }] },
      contents: [{ role: "user", parts }],
      generationConfig: generationConfiguration,
    };
    let response: Response;
    try {
      response = await this.fetcher(`${GEMINI_BASE_URL}/${encodeURIComponent(this.model)}:generateContent`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": this.apiKey },
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(request.timeoutMilliseconds),
      });
    } catch (error) {
      const timedOut = error instanceof DOMException && (error.name === "TimeoutError" || error.name === "AbortError");
      throw new ProviderError(this.name, timedOut ? "timeout" : "network", true);
    }
    if (!response.ok) {
      await response.body?.cancel();
      const { code, retryable } = classifyHttpStatus(response.status);
      throw new ProviderError(this.name, code, retryable);
    }
    const answer = await response.json() as GeminiAnswer;
    const usage = {
      model: this.model,
      inputTokens: answer.usageMetadata?.promptTokenCount ?? 0,
      outputTokens: (answer.usageMetadata?.candidatesTokenCount ?? 0) + (answer.usageMetadata?.thoughtsTokenCount ?? 0),
    };
    if (answer.promptFeedback?.blockReason) throw new ProviderError(this.name, "blocked", false);
    const candidate = answer.candidates?.[0];
    const text = (candidate?.content?.parts ?? [])
      .filter((part) => !part.thought && typeof part.text === "string")
      .map((part) => part.text)
      .join("");
    if (candidate?.finishReason === "SAFETY" || candidate?.finishReason === "PROHIBITED_CONTENT") {
      throw new ProviderError(this.name, "blocked", false);
    }
    return { text, usage, truncated: candidate?.finishReason === "MAX_TOKENS" };
  }
}
