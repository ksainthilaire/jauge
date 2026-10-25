import type { Configuration } from "../configuration.ts";
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
 */
export function createProviders(
  configuration: Configuration,
  connectors: LiveConnectors = {},
  fetcher: FetchFunction = fetch,
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
    socialSearch: connectors.socialSearch ?? new NotConnectedProvider("social_search"),
    ai: connectors.ai ?? new NotConnectedProvider("ai"),
    ...accountServices,
  };
}
