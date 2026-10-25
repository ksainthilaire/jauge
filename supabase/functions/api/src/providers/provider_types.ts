/**
 * Interfaces of the outside services used by the API. Each one has a deterministic simulated implementation
 * (`simulated_providers.ts`) used by the tests and by `JAUGE_PROVIDER_MODE=simulated`; the live implementations
 * (Apify actors, AI provider, social search, RevenueCat, Apple) plug in behind the same interfaces.
 *
 * Costs are in micro US dollars (1 USD = 1 000 000).
 */

/** Salary range in thousands of euros, annual gross. */
export interface SalaryRange {
  readonly minimum: number;
  readonly maximum: number;
}

/** Contract types of the API contract. */
export type ContractType =
  | "permanent"
  | "fixed_term"
  | "apprenticeship"
  | "internship"
  | "freelance"
  | "temporary"
  | "other";

/** An offer as read from its page, its text or its image. */
export interface ExtractedOffer {
  readonly jobTitle: string;
  readonly company: string | null;
  /** City as written ("Lyon 3e"). */
  readonly city: string | null;
  /** French department when known ("69"). */
  readonly departmentCode: string | null;
  readonly contractType: ContractType | null;
  readonly workMode: string | null;
  /** ISO date of publication. */
  readonly publishedOn: string | null;
  readonly displayedSalary: SalaryRange | null;
  readonly skills: readonly string[];
  /** Number of applicants shown by the site (LinkedIn shows it), null otherwise. */
  readonly displayedApplicantCount: number | null;
}

/** Result of reading an offer. */
export type ExtractionOutcome =
  | { readonly kind: "extracted"; readonly offer: ExtractedOffer; readonly costMicroUsd: number }
  | { readonly kind: "unreadable"; readonly recognizedCompany: string | null; readonly costMicroUsd: number }
  | { readonly kind: "unsupported"; readonly costMicroUsd: number };

/** Failure of a provider. Retryable failures are retried with a backoff until the deadline of the analysis. */
export class ProviderError extends Error {
  /**
   * @param provider name of the provider.
   * @param code short code for the logs (`timeout`, `http_503`, `not_configured`).
   * @param retryable true when a later attempt may succeed.
   */
  constructor(readonly provider: string, readonly code: string, readonly retryable: boolean) {
    super(`${provider}: ${code}`);
    this.name = "ProviderError";
  }
}

/** Reads the offer behind a link (page of a job site, career page). */
export interface OfferReader {
  /** Name for the logs and the cost journal. */
  readonly name: string;
  /** Cost of one reading, checked against the daily limit before the call. */
  estimateCostMicroUsd(): number;
  /**
   * Reads the offer of a canonical link.
   *
   * @param url canonical link.
   * @param site known job site, null for another site.
   * @param today date of today (ISO), to turn "il y a 2 jours" into a date.
   */
  readLink(url: string, site: string | null, today: string): Promise<ExtractionOutcome>;
}

/** What a market collection looks for: one job family in one department. */
export interface CollectionRequest {
  /** A representative job title of the family (search words of the job sites). */
  readonly jobTitle: string;
  readonly jobFamily: string;
  /** City at the center of the search. */
  readonly city: string;
  readonly departmentCode: string;
  readonly radiusKilometers: number;
  /** Largest number of offers to bring back (the price of most actors is per result). */
  readonly maximumOffers: number;
  /** Date of today (ISO). */
  readonly today: string;
}

/** An offer of the market brought back by a source. */
export interface CollectedOfferInput {
  readonly source: string;
  readonly externalId: string;
  readonly jobTitle: string;
  readonly company: string | null;
  readonly city: string | null;
  readonly departmentCode: string | null;
  readonly publishedOn: string | null;
  readonly salary: SalaryRange | null;
  /** Applicants shown by the site at the time of the collection. */
  readonly applicantCount: number | null;
  readonly contractType: string | null;
  readonly workMode: string | null;
  readonly skills: readonly string[];
  readonly url: string | null;
}

/** Start of a collection: immediate results, or a run to poll (Apify runs take minutes). */
export type CollectionStart =
  | { readonly kind: "finished"; readonly offers: readonly CollectedOfferInput[]; readonly costMicroUsd: number }
  | { readonly kind: "started"; readonly runId: string };

/** State of a running collection. */
export type CollectionPoll =
  | { readonly kind: "running" }
  | { readonly kind: "finished"; readonly offers: readonly CollectedOfferInput[]; readonly costMicroUsd: number }
  | { readonly kind: "failed"; readonly code: string; readonly costMicroUsd: number };

/** A source of market offers (Apify actor of LinkedIn, Indeed, Hellowork, Glassdoor, Welcome to the Jungle). */
export interface OfferSource {
  /** Name stored in `collected_offers.source`. */
  readonly name: string;
  /** False for free sources (they run even when the daily limit is reached). */
  readonly paid: boolean;
  /** Cost of a collection, checked against the daily limit before it starts. */
  estimateCostMicroUsd(request: CollectionRequest): number;
  /** Starts a collection. */
  start(request: CollectionRequest): Promise<CollectionStart>;
  /** Polls a started collection. */
  poll(runId: string): Promise<CollectionPoll>;
}

/** A public post about a company or a job (Reddit, X). Only used in memory, never stored. */
export interface SocialMention {
  readonly source: "reddit" | "x";
  readonly url: string;
  readonly publishedOn: string | null;
  /** Text of the post, cut to 600 characters. */
  readonly text: string;
}

/** Searches public posts about a company and a job. */
export interface SocialSearch {
  readonly name: string;
  readonly paid: boolean;
  estimateCostMicroUsd(): number;
  /**
   * Finds recent public posts.
   *
   * @param query company (may be null), job title and city.
   */
  search(query: { company: string | null; jobTitle: string; city: string | null }): Promise<{
    mentions: readonly SocialMention[];
    costMicroUsd: number;
  }>;
}

/** Reputation of the company and the job as summarized by the AI from the social posts. */
export interface Reputation {
  /** Two sentences at most, in the language of the analysis. */
  readonly summary: string;
  readonly tone: "positive" | "mixed" | "negative" | "unknown";
  /** Number of posts read. */
  readonly mentionCount: number;
  /** Sources that brought posts. */
  readonly sources: readonly ("reddit" | "x")[];
}

/** Provider of artificial intelligence (Gemini Flash-Lite, Mistral Small...). */
export interface AiProvider {
  readonly name: string;
  /** Cost of one call of an operation, checked against the daily limit before the call. */
  estimateCostMicroUsd(operation: "extract_text" | "extract_image" | "reputation"): number;
  /**
   * Reads an offer from its text (read on the phone or pasted).
   *
   * @param text text of the offer.
   * @param today date of today (ISO).
   */
  extractOfferFromText(text: string, today: string): Promise<ExtractionOutcome>;
  /**
   * Reads an offer from an image (screenshot or photo). The image is never kept by the provider (no training,
   * no retention option where the provider offers one).
   */
  extractOfferFromImage(image: Uint8Array, mimeType: string, today: string): Promise<ExtractionOutcome>;
  /**
   * Summarizes what candidates and employees say (social posts) about the company and the job.
   *
   * @param input offer and posts.
   */
  summarizeReputation(input: {
    company: string | null;
    jobTitle: string;
    mentions: readonly SocialMention[];
    language: "fr" | "en";
  }): Promise<{ reputation: Reputation | null; costMicroUsd: number }>;
}

/** RevenueCat, server side (secret key). */
export interface SubscriptionStore {
  /** Deletes the customer and its purchase history (`DELETE /v1/subscribers/{app_user_id}`). */
  deleteCustomer(appUserId: string): Promise<void>;
}

/** Sign in with Apple, server side. */
export interface AppleTokenRevoker {
  /**
   * Revokes the Apple tokens of the user from a fresh authorization code given by the app at deletion time
   * (`POST https://appleid.apple.com/auth/token` then `/auth/revoke`).
   */
  revokeWithAuthorizationCode(authorizationCode: string): Promise<void>;
}

/** Supabase Auth administration. */
export interface AuthAdministration {
  /** Deletes the auth user (cascades to devices and notifications). */
  deleteUser(userId: string): Promise<void>;
}

/** Sends an operations alert (cost limit, failing queue) to the owner. */
export interface OperationsAlerter {
  send(title: string, message: string): Promise<void>;
}

/** Every outside service, as one object given to the application. */
export interface Providers {
  readonly mode: "simulated" | "live";
  readonly offerReader: OfferReader;
  readonly offerSources: readonly OfferSource[];
  readonly socialSearch: SocialSearch;
  readonly ai: AiProvider;
  readonly subscriptionStore: SubscriptionStore;
  readonly appleTokenRevoker: AppleTokenRevoker;
  readonly authAdministration: AuthAdministration;
  readonly operationsAlerter: OperationsAlerter;
}
