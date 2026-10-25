import type { Logger } from "../operations/logger.ts";
import {
  type Advice,
  type AdviceFacts,
  type AiOperation,
  type AiProvider,
  type AiUsage,
  type ExtractedOffer,
  type ExtractionOutcome,
  ProviderError,
  type ReputationSummary,
  type SocialMention,
} from "../providers/provider_types.ts";
import { normalizeSearchText } from "../text/search_text.ts";
import { type SchemaNode, validateAgainstSchema } from "./json_schema.ts";
import { costMicroUsd, type ModelClient, type ModelImage } from "./model_client.ts";
import { numbersIn, removeLongDashes, textProblem, unexpectedWeekdays } from "./output_guards.ts";
import { ADVICE_PROMPT_VERSION, ADVICE_SCHEMA, type AdviceAnswer, advicePrompt } from "./prompts/v1/advice.ts";
import {
  OFFER_EXTRACTION_PROMPT_VERSION,
  OFFER_EXTRACTION_SCHEMA,
  type OfferExtractionAnswer,
  offerFromImagePrompt,
  offerFromTextPrompt,
} from "./prompts/v1/offer_extraction.ts";
import {
  REPUTATION_PROMPT_VERSION,
  REPUTATION_SCHEMA,
  type ReputationAnswer,
  reputationPrompt,
} from "./prompts/v1/reputation.ts";
import { newFenceMarker } from "./prompts/untrusted_data.ts";

/** Settings of the AI provider. */
export interface AiProviderSettings {
  /** Time given to one call of the model. */
  readonly timeoutMilliseconds: number;
  /** Calls per operation when the answer is not valid (first call included). */
  readonly maximumAttempts: number;
}

/** Default settings: 20 s per call (worker budget 50 s), one retry when the JSON is not valid. */
export const DEFAULT_AI_PROVIDER_SETTINGS: AiProviderSettings = { timeoutMilliseconds: 20_000, maximumAttempts: 2 };

/**
 * Tokens of one call, used for the estimate checked against the daily limit before the call (an image is about
 * 1 100 tokens on Gemini and Mistral; posts are about 150 tokens each, 40 at most). Outputs are capped by
 * `maximumOutputTokens`, so the real cost cannot run away.
 */
const OPERATION_BUDGETS: Readonly<
  Record<AiOperation, { estimatedInputTokens: number; maximumOutputTokens: number; temperature: number }>
> = {
  extract_text: { estimatedInputTokens: 3_000, maximumOutputTokens: 700, temperature: 0 },
  extract_image: { estimatedInputTokens: 2_200, maximumOutputTokens: 700, temperature: 0 },
  reputation: { estimatedInputTokens: 7_000, maximumOutputTokens: 500, temperature: 0.2 },
  advice: { estimatedInputTokens: 1_800, maximumOutputTokens: 450, temperature: 0.4 },
};

const PROMPT_VERSIONS: Readonly<Record<AiOperation, string>> = {
  extract_text: OFFER_EXTRACTION_PROMPT_VERSION,
  extract_image: OFFER_EXTRACTION_PROMPT_VERSION,
  reputation: REPUTATION_PROMPT_VERSION,
  advice: ADVICE_PROMPT_VERSION,
};

const WORK_MODE_LABELS = { on_site: "Sur site", hybrid: "Hybride", remote: "Télétravail" } as const;

/** Legal yearly working hours in France (35 hours a week), to turn an hourly rate into a yearly salary. */
const HOURS_PER_YEAR = 1607;
const WORKING_DAYS_PER_YEAR = 218;

function addDays(day: string, days: number): string {
  const date = new Date(`${day}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/** True when an amount in euros is written in the text ("36000", "36 000", "36k", "36 k€", "2 500,50"). */
export function amountWrittenIn(text: string, euros: number): boolean {
  const compact = text.toLowerCase().replace(/(\d)[\s  .](?=\d{3}\b)/g, "$1");
  if (new RegExp(`(^|[^\\d])${euros}([^\\d]|$)`).test(compact)) return true;
  if (euros % 1000 === 0 && new RegExp(`(^|[^\\d])${euros / 1000}\\s?k`).test(compact)) return true;
  return false;
}

/**
 * Reads offers, summarizes reputations and writes advice with a language model (Gemini or Mistral, through a
 * `ModelClient`). Every answer is constrained by a JSON schema, validated again here, checked for invented
 * numbers, links and personal data, and asked again once when it fails. Tokens and cost of every attempt are
 * returned so that the pipeline journals them (`provider_costs`) and the daily limit stays true.
 */
export class LanguageModelAiProvider implements AiProvider {
  readonly name: string;

  /**
   * @param client model client.
   * @param logger structured logger (codes only, never the content).
   * @param settings timeouts and attempts.
   * @param random random source of the fence markers (tests fix it).
   */
  constructor(
    private readonly client: ModelClient,
    private readonly logger: Logger,
    private readonly settings: AiProviderSettings = DEFAULT_AI_PROVIDER_SETTINGS,
    private readonly random: () => number = Math.random,
  ) {
    this.name = client.name;
  }

  /** @inheritdoc */
  estimateCostMicroUsd(operation: AiOperation): number {
    const budget = OPERATION_BUDGETS[operation];
    return costMicroUsd(this.client.pricing, budget.estimatedInputTokens, budget.maximumOutputTokens);
  }

  /** @inheritdoc */
  async extractOfferFromText(text: string, today: string): Promise<ExtractionOutcome> {
    const marker = newFenceMarker(this.random);
    const prompt = offerFromTextPrompt(text, today, marker);
    const answer = await this.structured<OfferExtractionAnswer>("extract_text", prompt, OFFER_EXTRACTION_SCHEMA);
    return this.toOutcome(answer, today, text);
  }

  /** @inheritdoc */
  async extractOfferFromImage(image: Uint8Array, mimeType: string, today: string): Promise<ExtractionOutcome> {
    const marker = newFenceMarker(this.random);
    const prompt = offerFromImagePrompt(today, marker);
    const answer = await this.structured<OfferExtractionAnswer>(
      "extract_image",
      prompt,
      OFFER_EXTRACTION_SCHEMA,
      () => null,
      { bytes: image, mimeType },
    );
    return this.toOutcome(answer, today, null);
  }

  /** @inheritdoc */
  async summarizeReputation(input: {
    company: string | null;
    jobTitle: string;
    mentions: readonly SocialMention[];
  }): Promise<{ summary: ReputationSummary | null; costMicroUsd: number; usage?: AiUsage }> {
    if (!input.company || input.mentions.length === 0) return { summary: null, costMicroUsd: 0 };
    const mentions = input.mentions.slice(0, 40);
    const marker = newFenceMarker(this.random);
    const prompt = reputationPrompt(input.company, input.jobTitle, mentions, marker);
    const answer = await this.structured<ReputationAnswer>("reputation", prompt, REPUTATION_SCHEMA, (value) => {
      const french = removeLongDashes(value.summaryFrench);
      const english = removeLongDashes(value.summaryEnglish);
      if (!value.relevant) return null;
      if (!french || !english) return "summaries are empty while relevant is true";
      // The summary may not contain any number: counts are shown by the app.
      return textProblem(`${french} ${english}`, new Set()) ??
        (value.citedPostNumbers.some((number) => number > mentions.length) ? "cites a post that does not exist" : null);
    });
    const cost = { costMicroUsd: answer.costMicroUsd, usage: answer.usage };
    if (!answer.value || !answer.value.relevant || answer.value.tone === "unknown") return { summary: null, ...cost };
    const cited = [...new Set(answer.value.citedPostNumbers)].map((number) => mentions[number - 1]).filter(Boolean);
    const sources = [...new Set(mentions.map((mention) => mention.source))];
    return {
      summary: {
        summaryFrench: removeLongDashes(answer.value.summaryFrench),
        summaryEnglish: removeLongDashes(answer.value.summaryEnglish),
        tone: answer.value.tone,
        mentionCount: mentions.length,
        sources,
        links: cited.slice(0, 5).map((mention) => ({ source: mention.source, url: mention.url })),
      },
      ...cost,
    };
  }

  /** @inheritdoc */
  async writeAdvice(facts: AdviceFacts): Promise<{ advice: Advice | null; costMicroUsd: number; usage?: AiUsage }> {
    const marker = newFenceMarker(this.random);
    const allowedNumbers = new Set(numbersIn(JSON.stringify(facts)));
    const candidates = new Map(facts.missingSkillCandidates.map((skill) => [normalizeSearchText(skill), skill]));
    const answer = await this.structured<AdviceAnswer>(
      "advice",
      advicePrompt(facts, marker),
      ADVICE_SCHEMA,
      (value) => {
        const advice = removeLongDashes(value.advice);
        if (advice.length < 20) return "advice is too short";
        const negotiation = value.negotiationArgument ? removeLongDashes(value.negotiationArgument) : null;
        if (negotiation && facts.negotiationTarget === null) return "negotiationArgument must be null without target";
        const problem = textProblem(`${advice} ${negotiation ?? ""}`, allowedNumbers);
        if (problem) return problem;
        const days = unexpectedWeekdays(`${advice} ${negotiation ?? ""}`, facts.timingAdvice?.applyBefore ?? null);
        if (days.length > 0) return `names a weekday that is not applyBefore: ${days.join(", ")}`;
        return null;
      },
    );
    const cost = { costMicroUsd: answer.costMicroUsd, usage: answer.usage };
    if (!answer.value) return { advice: null, ...cost };
    const missingSkills = [
      ...new Set(
        answer.value.missingSkills
          .map((skill) => candidates.get(normalizeSearchText(skill)))
          .filter((skill): skill is string => skill !== undefined),
      ),
    ].slice(0, 4);
    return {
      advice: {
        text: removeLongDashes(answer.value.advice),
        negotiationArgument: answer.value.negotiationArgument
          ? removeLongDashes(answer.value.negotiationArgument)
          : null,
        missingSkills,
      },
      ...cost,
    };
  }

  /**
   * Calls the model until the answer is valid JSON of the schema and passes the check, at most
   * `maximumAttempts` times. Returns null as value when every attempt failed (the caller degrades gracefully).
   */
  private async structured<T>(
    operation: AiOperation,
    prompt: { system: string; user: string },
    schema: SchemaNode,
    check: (value: T) => string | null = () => null,
    image?: ModelImage,
  ): Promise<{ value: T | null; costMicroUsd: number; usage: AiUsage }> {
    const budget = OPERATION_BUDGETS[operation];
    let inputTokens = 0;
    let outputTokens = 0;
    let problem: string | null = null;
    const usage = (): AiUsage => ({ model: this.client.model, inputTokens, outputTokens });
    const total = () => costMicroUsd(this.client.pricing, inputTokens, outputTokens);
    for (let attempt = 1; attempt <= this.settings.maximumAttempts; attempt++) {
      let reply;
      try {
        reply = await this.client.generate({
          system: prompt.system,
          user: problem
            ? `${prompt.user}\n\nYour previous answer was rejected (${problem}). Answer again: valid JSON of the schema only, following every rule.`
            : prompt.user,
          image,
          schema,
          schemaName: operation,
          maximumOutputTokens: budget.maximumOutputTokens,
          temperature: budget.temperature,
          timeoutMilliseconds: this.settings.timeoutMilliseconds,
        });
      } catch (error) {
        // Tokens of the earlier attempts are paid: keep them in the journal rather than losing them.
        if (attempt > 1 && error instanceof ProviderError) break;
        throw error;
      }
      inputTokens += reply.usage.inputTokens;
      outputTokens += reply.usage.outputTokens;
      let parsed: unknown;
      try {
        parsed = JSON.parse(reply.text);
      } catch {
        problem = reply.truncated
          ? "the JSON was cut by the output limit, be shorter"
          : "the answer was not valid JSON";
        continue;
      }
      const validation = validateAgainstSchema<T>(schema, parsed);
      if (!validation.valid) {
        problem = `schema: ${validation.problem}`;
        continue;
      }
      problem = check(validation.value);
      if (problem === null) {
        this.logger.log("info", "ai_call", {
          provider: this.name,
          model: this.client.model,
          operation,
          promptVersion: PROMPT_VERSIONS[operation],
          attempts: attempt,
          inputTokens,
          outputTokens,
          costMicroUsd: total(),
        });
        return { value: validation.value, costMicroUsd: total(), usage: usage() };
      }
    }
    this.logger.log("warning", "ai_answer_rejected", {
      provider: this.name,
      model: this.client.model,
      operation,
      promptVersion: PROMPT_VERSIONS[operation],
      reason: (problem ?? "call_failed").replace(/:.*$/, ""),
      inputTokens,
      outputTokens,
      costMicroUsd: total(),
    });
    return { value: null, costMicroUsd: total(), usage: usage() };
  }

  private toOutcome(
    answer: { value: OfferExtractionAnswer | null; costMicroUsd: number; usage: AiUsage },
    today: string,
    sourceText: string | null,
  ): ExtractionOutcome {
    const cost = { costMicroUsd: answer.costMicroUsd, usage: answer.usage };
    const value = answer.value;
    if (!value) return { kind: "unreadable", recognizedCompany: null, ...cost };
    const company = value.company?.trim() || null;
    const jobTitle = value.jobTitle?.trim() || null;
    if (!value.isJobOffer || !jobTitle) return { kind: "unreadable", recognizedCompany: company, ...cost };
    // A number must be written in the text it was read from: an empty field rather than an invented one.
    const written = (amount: number | null) =>
      amount !== null && (sourceText === null || amountWrittenIn(sourceText, amount)) ? amount : null;
    const offer: ExtractedOffer = {
      jobTitle,
      company,
      city: value.city?.trim() || null,
      departmentCode: value.departmentCode && /^([0-9]{2}|2[AB]|9[78][0-9])$/.test(value.departmentCode)
        ? value.departmentCode
        : null,
      contractType: value.contractType,
      workMode: value.workMode ? WORK_MODE_LABELS[value.workMode] : null,
      publishedOn: publicationDate(value, today),
      displayedSalary: yearlySalary(
        written(value.salaryMinimumEuros),
        written(value.salaryMaximumEuros),
        value.salaryPeriod,
      ),
      skills: [...new Set(value.skills.map((skill) => skill.trim()).filter((skill) => skill.length > 0))].slice(0, 20),
      displayedApplicantCount: written(value.displayedApplicantCount),
    };
    return { kind: "extracted", offer, ...cost };
  }
}

function publicationDate(value: OfferExtractionAnswer, today: string): string | null {
  if (
    value.publishedOn && /^\d{4}-\d{2}-\d{2}$/.test(value.publishedOn) && !Number.isNaN(Date.parse(value.publishedOn))
  ) {
    if (value.publishedOn <= today && value.publishedOn >= addDays(today, -365)) return value.publishedOn;
    return null;
  }
  if (value.publishedDaysAgo !== null) return addDays(today, -value.publishedDaysAgo);
  return null;
}

/** Salary in thousands of euros per year (gross) from the amounts as written, or null when it makes no sense. */
export function yearlySalary(
  minimum: number | null,
  maximum: number | null,
  period: OfferExtractionAnswer["salaryPeriod"],
): { minimum: number; maximum: number } | null {
  if (minimum === null && maximum === null) return null;
  const low = minimum ?? maximum!;
  const high = maximum ?? minimum!;
  const yearly = (amount: number) => {
    switch (period) {
      case "month":
        return amount * 12;
      case "day":
        return amount * WORKING_DAYS_PER_YEAR;
      case "hour":
        return amount * HOURS_PER_YEAR;
      default:
        return amount;
    }
  };
  const result = { minimum: Math.round(yearly(low) / 1000), maximum: Math.round(yearly(high) / 1000) };
  if (result.minimum < 1 || result.maximum > 1000 || result.minimum > result.maximum) return null;
  return result;
}
