import { ProviderError } from "../providers/provider_types.ts";
import { toJsonSchema } from "./json_schema.ts";
import {
  classifyHttpStatus,
  type FetchFunction,
  type ModelClient,
  type ModelPricing,
  type ModelReply,
  type ModelRequest,
  toBase64,
} from "./model_client.ts";

/** Default model: Mistral Small 4 (text and images, structured outputs), hosted in the European Union. */
export const MISTRAL_DEFAULT_MODEL = "mistral-small-2603";

/** Public prices (USD per million tokens), checked on 25/10/2026. */
export const MISTRAL_PRICES: Readonly<Record<string, ModelPricing>> = {
  "mistral-small-2603": { inputUsdPerMillionTokens: 0.15, outputUsdPerMillionTokens: 0.6 },
  "mistral-small-latest": { inputUsdPerMillionTokens: 0.15, outputUsdPerMillionTokens: 0.6 },
  "ministral-8b-2512": { inputUsdPerMillionTokens: 0.15, outputUsdPerMillionTokens: 0.15 },
  "ministral-14b-2512": { inputUsdPerMillionTokens: 0.2, outputUsdPerMillionTokens: 0.2 },
};

const MISTRAL_URL = "https://api.mistral.ai/v1/chat/completions";

interface MistralAnswer {
  choices?: { message?: { content?: string | { type: string; text?: string }[] }; finish_reason?: string }[];
  usage?: { prompt_tokens?: number; completion_tokens?: number };
}

/**
 * Mistral chat completions with a strict JSON schema (`response_format.type = json_schema`). Data processed in the
 * European Union; La Plateforme does not train on API data (the "anonymous improvement data" switch of the
 * organization must stay off, see docs/backend/ia.md).
 */
export class MistralClient implements ModelClient {
  readonly name = "mistral";

  /**
   * @param apiKey Mistral API key (secret `MISTRAL_API_KEY`).
   * @param model model identifier.
   * @param pricing price of the model.
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
    const content: Record<string, unknown>[] = [{ type: "text", text: request.user }];
    if (request.image) {
      content.push({
        type: "image_url",
        image_url: `data:${request.image.mimeType};base64,${toBase64(request.image.bytes)}`,
      });
    }
    const body = {
      model: this.model,
      messages: [
        { role: "system", content: request.system },
        { role: "user", content },
      ],
      response_format: {
        type: "json_schema",
        json_schema: { name: request.schemaName, schema: toJsonSchema(request.schema), strict: true },
      },
      temperature: request.temperature,
      max_tokens: request.maximumOutputTokens,
    };
    let response: Response;
    try {
      response = await this.fetcher(MISTRAL_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${this.apiKey}` },
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
    const answer = await response.json() as MistralAnswer;
    const choice = answer.choices?.[0];
    const raw = choice?.message?.content;
    const text = typeof raw === "string"
      ? raw
      : (raw ?? []).filter((part) => part.type === "text").map((part) => part.text ?? "").join("");
    return {
      text,
      usage: {
        model: this.model,
        inputTokens: answer.usage?.prompt_tokens ?? 0,
        outputTokens: answer.usage?.completion_tokens ?? 0,
      },
      truncated: choice?.finish_reason === "length",
    };
  }
}
