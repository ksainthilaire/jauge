import { canonicalizeLink } from "../../offers/offer_key.ts";
import type { ApifyOfferActorReader } from "../apify/apify_offer_source.ts";
import type { FetchFunction } from "../live_account_services.ts";
import type { AiProvider, ExtractedOffer, ExtractionOutcome, OfferReader, PaidStepBudget } from "../provider_types.ts";
import { ProviderError } from "../provider_types.ts";
import { findJobPosting, linkedInApplicantCount, offerFromJobPosting, pageTitle, visibleText } from "./job_posting.ts";
import {
  contractTypeOf,
  parseApplicantCount,
  parsePublicationDate,
  parseSalaryText,
  workModeOf,
} from "./offer_parsing.ts";

/** Settings of the reader. */
export interface LiveOfferReaderSettings {
  /** Timeout of the page request, in milliseconds. */
  readonly pageTimeoutMilliseconds: number;
  /** Largest page read, in bytes (job pages weigh 100 KB to 1 MB). */
  readonly maximumPageBytes: number;
  /** User agent of the page request. */
  readonly userAgent: string;
  /** Shortest page text worth sending to the AI. */
  readonly minimumTextCharacters: number;
}

/** Default settings. */
export const DEFAULT_LIVE_OFFER_READER_SETTINGS: LiveOfferReaderSettings = {
  pageTimeoutMilliseconds: 8_000,
  maximumPageBytes: 2_000_000,
  userAgent: "Mozilla/5.0 (compatible; JaugeLinkPreview/1.0; +https://jauge.io)",
  minimumTextCharacters: 300,
};

/** What came back from the page of the offer. */
type PageResult =
  | { readonly kind: "page"; readonly html: string }
  /** The site refused (403, 429, 999, challenge page) or answered something that is not a page. */
  | { readonly kind: "blocked"; readonly code: string }
  /** 404 or 410: the offer was removed. */
  | { readonly kind: "gone" }
  /** Network error or 5xx: may work later. */
  | { readonly kind: "error"; readonly code: string };

const MAXIMUM_REDIRECTS = 3;
const CHALLENGE = /cf-challenge|challenge-platform|captcha|just a moment\.\.\.|access denied|verify you are human/i;

/**
 * Reads offers the cheapest way first:
 *
 * 1. the page itself (one HTTP request, free) and its schema.org `JobPosting` (LinkedIn public pages, HelloWork,
 *    France Travail, Welcome to the Jungle, Apec, most career sites), plus the applicant count of LinkedIn pages;
 * 2. when the site refuses direct reading (Indeed), the Apify actor of the site (about 0.005 USD), if the daily limit
 *    allows it;
 * 3. when the page has no structured data, its visible text structured by the AI (about 0.0003 USD).
 *
 * The page text never leaves the worker except to the AI provider, and is never stored.
 */
export class LiveOfferReader implements OfferReader {
  readonly name = "offer_reader";
  private readonly settings: LiveOfferReaderSettings;

  /**
   * @param ai AI provider (a "not connected" provider simply skips step 3).
   * @param actorReaders Apify readers by site (step 2), empty without an Apify token.
   * @param fetcher fetch function.
   * @param settings settings.
   */
  constructor(
    private readonly ai: AiProvider,
    private readonly actorReaders: Readonly<Record<string, ApifyOfferActorReader>>,
    private readonly fetcher: FetchFunction = fetch,
    settings: Partial<LiveOfferReaderSettings> = {},
  ) {
    this.settings = { ...DEFAULT_LIVE_OFFER_READER_SETTINGS, ...settings };
  }

  /** @inheritdoc */
  estimateCostMicroUsd(): number {
    // The first way is free; the paid ones ask the budget themselves.
    return 0;
  }

  /** @inheritdoc */
  async readLink(
    url: string,
    site: string | null,
    today: string,
    budget: PaidStepBudget = () => Promise.resolve(true),
  ): Promise<ExtractionOutcome> {
    const page = await this.fetchPage(url);
    let costMicroUsd = 0;

    if (page.kind === "page") {
      const posting = findJobPosting(page.html);
      const offer = posting ? offerFromJobPosting(posting, today) : null;
      if (offer) return { kind: "extracted", offer: this.withPageDetails(offer, page.html, site), costMicroUsd: 0 };
    }
    if (page.kind === "gone") return { kind: "unreadable", recognizedCompany: null, costMicroUsd: 0 };

    const actorReader = site ? this.actorReaders[site] : undefined;
    if (actorReader && page.kind !== "page" && (await budget(actorReader.estimateCostMicroUsd()))) {
      try {
        const reading = await actorReader.read(url, today);
        costMicroUsd += reading.costMicroUsd;
        if (reading.offer) return { kind: "extracted", offer: reading.offer, costMicroUsd };
      } catch (error) {
        if (!(error instanceof ProviderError)) throw error;
        // Apify unavailable: the user is offered the other ways (screenshot, manual entry).
      }
    }

    if (page.kind === "page") {
      const text = visibleText(page.html);
      if (
        text.length >= this.settings.minimumTextCharacters &&
        (await budget(this.ai.estimateCostMicroUsd("extract_text")))
      ) {
        try {
          const outcome = await this.ai.extractOfferFromText(text, today);
          costMicroUsd += outcome.costMicroUsd;
          if (outcome.kind === "extracted") {
            const enriched = enrichFromText(outcome.offer, text, today);
            return { kind: "extracted", offer: this.withPageDetails(enriched, page.html, site), costMicroUsd };
          }
          if (outcome.kind === "unreadable") {
            return {
              kind: "unreadable",
              recognizedCompany: outcome.recognizedCompany ?? companyFromPageTitle(page.html),
              costMicroUsd,
            };
          }
        } catch (error) {
          if (!(error instanceof ProviderError) || error.retryable) throw error;
        }
      }
      return { kind: "unreadable", recognizedCompany: companyFromPageTitle(page.html), costMicroUsd };
    }
    if (page.kind === "error" && costMicroUsd === 0) {
      // The site is down or slow: the work queue tries again with a backoff, until the deadline.
      throw new ProviderError(this.name, `page_${page.code}`, true);
    }
    return { kind: "unsupported", costMicroUsd };
  }

  /** @inheritdoc */
  async readText(text: string, today: string): Promise<ExtractionOutcome> {
    const outcome = await this.ai.extractOfferFromText(text, today);
    if (outcome.kind !== "extracted") return outcome;
    return { ...outcome, offer: enrichFromText(outcome.offer, text, today) };
  }

  /** The applicant count that LinkedIn shows in its public page (not in its structured data). */
  private withPageDetails(offer: ExtractedOffer, html: string, site: string | null): ExtractedOffer {
    if (site !== "linkedin" || offer.displayedApplicantCount !== null) return offer;
    return { ...offer, displayedApplicantCount: linkedInApplicantCount(html) };
  }

  private async fetchPage(url: string): Promise<PageResult> {
    let current = url;
    for (let hop = 0; hop <= MAXIMUM_REDIRECTS; hop++) {
      let response: Response;
      try {
        response = await this.fetcher(current, {
          method: "GET",
          redirect: "manual",
          headers: {
            "User-Agent": this.settings.userAgent,
            Accept: "text/html,application/xhtml+xml",
            "Accept-Language": "fr-FR,fr;q=0.9,en;q=0.6",
          },
          signal: AbortSignal.timeout(this.settings.pageTimeoutMilliseconds),
        });
      } catch {
        return { kind: "error", code: "network" };
      }
      if (response.status >= 300 && response.status < 400) {
        await response.body?.cancel();
        const location = response.headers.get("location");
        if (!location) return { kind: "blocked", code: "redirect" };
        let next: string;
        try {
          next = new URL(location, current).toString();
        } catch {
          return { kind: "blocked", code: "redirect" };
        }
        // Every hop is checked like a pasted link: never a private or local address.
        if (!canonicalizeLink(next)) return { kind: "blocked", code: "redirect" };
        if (/\/(login|signin|authwall|uas\/login)/i.test(new URL(next).pathname)) {
          return { kind: "blocked", code: "login" };
        }
        current = next;
        continue;
      }
      if (response.status === 404 || response.status === 410) {
        await response.body?.cancel();
        return { kind: "gone" };
      }
      if (response.status >= 500 && response.status !== 999) {
        await response.body?.cancel();
        return { kind: "error", code: `http_${response.status}` };
      }
      if (!response.ok) {
        await response.body?.cancel();
        return { kind: "blocked", code: `http_${response.status}` };
      }
      const type = response.headers.get("content-type") ?? "";
      if (type && !/html|xml/i.test(type)) {
        await response.body?.cancel();
        return { kind: "blocked", code: "not_html" };
      }
      const html = await readLimited(response, this.settings.maximumPageBytes);
      if (!/application\/ld\+json/i.test(html) && CHALLENGE.test(html.slice(0, 20_000))) {
        return { kind: "blocked", code: "challenge" };
      }
      return { kind: "page", html };
    }
    return { kind: "blocked", code: "too_many_redirects" };
  }
}

/** Reads at most `maximumBytes` of a body as text. */
async function readLimited(response: Response, maximumBytes: number): Promise<string> {
  if (!response.body) return "";
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    while (size < maximumBytes) {
      const { done, value } = await reader.read();
      if (done || !value) break;
      chunks.push(value);
      size += value.byteLength;
    }
  } finally {
    await reader.cancel().catch(() => undefined);
  }
  const bytes = new Uint8Array(Math.min(size, maximumBytes));
  let offset = 0;
  for (const chunk of chunks) {
    const part = chunk.subarray(0, Math.min(chunk.byteLength, bytes.byteLength - offset));
    bytes.set(part, offset);
    offset += part.byteLength;
    if (offset >= bytes.byteLength) break;
  }
  return new TextDecoder().decode(bytes);
}

/**
 * Fills the fields the AI left empty with what the text says plainly (free): "47 candidats", "Publiée il y a 3 jours",
 * "35 000 - 40 000 € par an", "CDI", "Télétravail partiel". The AI is never overruled.
 *
 * @param offer offer read by the AI.
 * @param text text of the offer.
 * @param today date of today (ISO).
 */
export function enrichFromText(offer: ExtractedOffer, text: string, today: string): ExtractedOffer {
  const lines = text.split(/\n+/).map((line) => line.trim()).filter(Boolean).slice(0, 400);
  const find = (pattern: RegExp) => lines.find((line) => pattern.test(line) && line.length <= 160) ?? null;
  const applicantLine = find(/\d[\d\s]*\s*(candidats?|candidatures?|applicants?|postulants?)\b/i);
  const dateLine = find(/\b(publi[ée]e?s?|posted|mise? en ligne|il y a|ago)\b/i);
  const salaryLine = find(/\d.*(€|\beur\b|euros?)|(€|euros?).*\d/i);
  const contractLine = find(/\b(CDI|CDD|alternance|apprentissage|stage|int[ée]rim|freelance)\b/i);
  const remoteLine = find(/\b(t[ée]l[ée]travail|remote|hybride|hybrid)\b/i);
  return {
    ...offer,
    displayedApplicantCount: offer.displayedApplicantCount ??
      (applicantLine
        ? parseApplicantCount(
          /(?:over|plus de)?\s*\d[\d\s]*\s*(candidats?|candidatures?|applicants?|postulants?)/i
            .exec(applicantLine)?.[0] ?? null,
        )
        : null),
    publishedOn: offer.publishedOn ?? (dateLine ? parsePublicationDate(dateLine, today) : null),
    displayedSalary: offer.displayedSalary ?? (salaryLine ? parseSalaryText(salaryLine) : null),
    contractType: offer.contractType ??
      (contractLine
        ? contractTypeOf(/\b(CDI|CDD|alternance|apprentissage|stage|int[ée]rim|freelance)\b/i.exec(contractLine)?.[0])
        : null),
    workMode: offer.workMode ?? (remoteLine ? workModeOf(remoteLine) : null),
  };
}

/** Company guessed from a page title ("Chargé de marketing - Atelier Sorel | LinkedIn"), for unreadable pages. */
export function companyFromPageTitle(html: string): string | null {
  const title = pageTitle(html);
  const parts = title?.split(/\s[-|·]\s/) ?? [];
  return parts.length >= 3 ? parts[parts.length - 2].slice(0, 200) : null;
}
