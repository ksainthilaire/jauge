import { GEMINI_PRICES, GeminiClient } from "../ai/gemini_client.ts";
import { LanguageModelAiProvider } from "../ai/language_model_ai_provider.ts";
import { MISTRAL_PRICES, MistralClient } from "../ai/mistral_client.ts";
import type { ModelClient, ModelPricing } from "../ai/model_client.ts";
import type { Configuration } from "../configuration.ts";
import type { Logger } from "../operations/logger.ts";
import { ApifySocialSearch, redditActor, type SocialActor, xActor } from "../social/apify_social_search.ts";
import {
  AppleSignInRevoker,
  type FetchFunction,
  RevenueCatSubscriptionStore,
  SupabaseAuthAdministration,
  WebhookOperationsAlerter,
} from "./live_account_services.ts";
import type {
  AiProvider,
  AppleTokenRevoker,
  AuthAdministration,
  OfferReader,
  OfferSource,
  Providers,
  SocialSearch,
  SubscriptionStore,
} from "./provider_types.ts";
import { ProviderError } from "./provider_types.ts";
import { createSimulatedProviders, RecordingAccountServices } from "./simulated_providers.ts";

/**
 * Placeholder of a live provider that is not connected yet (phase 2: Apify connectors, AI, social search). Every call
 * fails with `not_configured`, which the pipeline handles like an unavailable provider.
 */
class NotConnectedProvider implements OfferReader, SocialSearch, AiProvider {
  readonly paid = true;
  constructor(readonly name: string) {}
  estimateCostMicroUsd(): number {
    return 0;
  }
  private fail(): never {
    throw new ProviderError(this.name, "not_configured", false);
  }
  readLink(): Promise<never> {
    return Promise.resolve().then(() => this.fail());
  }
  search(): Promise<never> {
    return Promise.resolve().then(() => this.fail());
  }
  extractOfferFromText(): Promise<never> {
    return Promise.resolve().then(() => this.fail());
  }
  extractOfferFromImage(): Promise<never> {
    return Promise.resolve().then(() => this.fail());
  }
  summarizeReputation(): Promise<never> {
    return Promise.resolve().then(() => this.fail());
  }
  writeAdvice(): Promise<never> {
    return Promise.resolve().then(() => this.fail());
  }
}

/** Logger used when none is given (tests of the registry). */
const SILENT_LOGGER: Logger = { log: () => {} };

/**
 * Price of the configured model: the configured prices when given, otherwise the public price list. A model missing
 * from both cannot be used (its cost could not be checked against the daily limit).
 */
function modelPricing(configuration: Configuration, model: string, list: Readonly<Record<string, ModelPricing>>) {
  const { inputUsdPerMillionTokens, outputUsdPerMillionTokens } = configuration.ai;
  if (inputUsdPerMillionTokens !== null && outputUsdPerMillionTokens !== null) {
    return { inputUsdPerMillionTokens, outputUsdPerMillionTokens };
  }
  return list[model] ?? null;
}

/**
 * Live AI provider from the configuration (`AI_PROVIDER`, `GEMINI_API_KEY` or `MISTRAL_API_KEY`), null when its key
 * or the price of its model is missing.
 *
 * @param configuration configuration.
 * @param fetcher fetch function.
 * @param logger structured logger.
 */
export function createLiveAi(configuration: Configuration, fetcher: FetchFunction, logger: Logger): AiProvider | null {
  const settings = configuration.ai;
  let client: ModelClient | null = null;
  if (settings.provider === "mistral" && settings.mistralApiKey) {
    const pricing = modelPricing(configuration, settings.mistralModel, MISTRAL_PRICES);
    if (pricing) client = new MistralClient(settings.mistralApiKey, settings.mistralModel, pricing, fetcher);
  } else if (settings.provider === "gemini" && settings.geminiApiKey) {
    const pricing = modelPricing(configuration, settings.geminiModel, GEMINI_PRICES);
    if (pricing) client = new GeminiClient(settings.geminiApiKey, settings.geminiModel, pricing, fetcher);
  }
  return client ? new LanguageModelAiProvider(client, logger) : null;
}

/**
 * Live social search (Reddit and X through Apify) from the configuration, null without `APIFY_TOKEN` or without
 * any network in `JAUGE_SOCIAL_SOURCES`.
 *
 * @param configuration configuration.
 * @param fetcher fetch function.
 * @param logger structured logger.
 */
export function createLiveSocialSearch(
  configuration: Configuration,
  fetcher: FetchFunction,
  logger: Logger,
): SocialSearch | null {
  const { apifyToken, sources, redditActorId, xActorId } = configuration.social;
  if (!apifyToken || sources.length === 0) return null;
  const actors: SocialActor[] = [];
  if (sources.includes("reddit")) actors.push(redditActor(redditActorId ?? undefined));
  if (sources.includes("x")) actors.push(xActor(xActorId ?? undefined));
  return new ApifySocialSearch(apifyToken, actors, logger, fetcher);
}

/** Live connectors that phase 2 registers here (one line each). */
export interface LiveConnectors {
  offerReader?: OfferReader;
  offerSources?: readonly OfferSource[];
  socialSearch?: SocialSearch;
  ai?: AiProvider;
}

/**
 * Builds the providers from the configuration. In `simulated` mode the market, AI and social providers are the
 * deterministic fakes; the account services (RevenueCat, Apple, Supabase Auth) are live as soon as their secrets
 * exist, and simulated otherwise. In `live` mode, connectors that are not given stay "not connected".
 *
 * @param configuration configuration.
 * @param connectors live connectors (phase 2).
 * @param fetcher fetch function.
 * @param logger structured logger of the live connectors.
 */
export function createProviders(
  configuration: Configuration,
  connectors: LiveConnectors = {},
  fetcher: FetchFunction = fetch,
  logger: Logger = SILENT_LOGGER,
): Providers {
  const recording = new RecordingAccountServices();
  const subscriptionStore: SubscriptionStore = configuration.revenueCatSecretApiKey
    ? new RevenueCatSubscriptionStore(configuration.revenueCatSecretApiKey, fetcher)
    : recording;
  const { teamId, clientId, keyId, privateKey } = configuration.apple;
  const appleTokenRevoker: AppleTokenRevoker = teamId && clientId && keyId && privateKey
    ? new AppleSignInRevoker({ teamId, clientId, keyId, privateKey }, fetcher)
    : recording;
  const authAdministration: AuthAdministration = configuration.supabaseUrl && configuration.serviceRoleKey
    ? new SupabaseAuthAdministration(configuration.supabaseUrl, configuration.serviceRoleKey, fetcher)
    : recording;
  const operationsAlerter = new WebhookOperationsAlerter(configuration.alertWebhookUrl, fetcher);
  const accountServices = { subscriptionStore, appleTokenRevoker, authAdministration, operationsAlerter };

  if (configuration.providerMode === "simulated") {
    return { ...createSimulatedProviders(recording), ...accountServices };
  }
  return {
    mode: "live",
    offerReader: connectors.offerReader ?? new NotConnectedProvider("offer_reader"),
    offerSources: connectors.offerSources ?? [],
    socialSearch: connectors.socialSearch ?? createLiveSocialSearch(configuration, fetcher, logger) ??
      new NotConnectedProvider("social_search"),
    ai: connectors.ai ?? createLiveAi(configuration, fetcher, logger) ?? new NotConnectedProvider("ai"),
    ...accountServices,
  };
}
