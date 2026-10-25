import type { FetchFunction } from "../ai/model_client.ts";
import type { Logger } from "../operations/logger.ts";
import { ProviderError, type SocialMention, type SocialSearch } from "../providers/provider_types.ts";
import { normalizeSearchText } from "../text/search_text.ts";
import { anonymizePost, anonymousPostLink } from "./anonymize.ts";

/** Search of one social network through an Apify actor (pay per result). */
export interface SocialActor {
  readonly source: "reddit" | "x";
  /** Actor identifier (`user~name`). */
  readonly actorId: string;
  /** Results asked for (and paid). */
  readonly maximumItems: number;
  /** Price of one result in micro US dollars (public price of the actor). */
  readonly pricePerResultMicroUsd: number;
  /** Smallest charge of a run, even without results. */
  readonly minimumChargeMicroUsd: number;
  /** Input of the actor for a company. */
  buildInput(company: string): Record<string, unknown>;
  /** Link, text and date of one dataset item, or null when the item is not a usable post. */
  readItem(item: Record<string, unknown>): { url: string; text: string; publishedOn: string | null } | null;
}

function text(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function isoDay(value: unknown): string | null {
  if (typeof value !== "string" && typeof value !== "number") return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString().slice(0, 10);
}

/**
 * Reddit through `trudax/reddit-scraper-lite`: posts and comments that contain the company name, most relevant
 * first, the last 12 months. 3.40 USD per 1 000 results (pay per event, no platform usage), checked 25/10/2026.
 */
export function redditActor(actorId = "trudax~reddit-scraper-lite", maximumItems = 12): SocialActor {
  return {
    source: "reddit",
    actorId,
    maximumItems,
    pricePerResultMicroUsd: 3_400,
    minimumChargeMicroUsd: 0,
    buildInput: (company) => ({
      searches: [`"${company}"`],
      searchPosts: true,
      searchComments: true,
      searchCommunities: false,
      searchUsers: false,
      sort: "relevance",
      time: "year",
      includeNSFW: false,
      skipComments: true,
      skipUserPosts: true,
      maxItems: maximumItems,
      maxPostCount: maximumItems,
      maxComments: 0,
      proxy: { useApifyProxy: true },
    }),
    readItem: (item) => {
      const kind = text(item.dataType);
      if (kind !== "post" && kind !== "comment") return null;
      const body = [text(item.title), text(item.body)].filter(Boolean).join(". ");
      const url = text(item.url);
      return body && url ? { url, text: body, publishedOn: isoDay(item.createdAt) } : null;
    },
  };
}

/**
 * X through `kaitoeasyapi/twitter-x-data-tweet-scraper-pay-per-result-cheapest`: about 0.25 USD per 1 000 posts,
 * 20 posts at least per search term, a small minimum charge per call (the actor returns mock items when nothing is
 * found: they are dropped). Checked 25/10/2026.
 */
export function xActor(
  actorId = "kaitoeasyapi~twitter-x-data-tweet-scraper-pay-per-result-cheapest",
  maximumItems = 20,
): SocialActor {
  return {
    source: "x",
    actorId,
    maximumItems,
    pricePerResultMicroUsd: 250,
    minimumChargeMicroUsd: 5_000,
    buildInput: (company) => ({
      searchTerms: [`"${company}" -filter:retweets`],
      maxItems: maximumItems,
      queryType: "Top",
    }),
    readItem: (item) => {
      if (item.type !== undefined && item.type !== "tweet") return null;
      const body = text(item.text) || text(item.fullText);
      const url = text(item.url) || text(item.twitterUrl);
      return body && url ? { url, text: body, publishedOn: isoDay(item.createdAt) } : null;
    },
  };
}

/** Settings of the social search. */
export interface SocialSearchSettings {
  /** Seconds an actor may run (the synchronous endpoint of Apify waits 300 s at most). */
  readonly actorTimeoutSeconds: number;
  /** Posts given to the AI at most. */
  readonly maximumMentions: number;
}

/** Default settings: 25 s per actor (they run side by side inside the 50 s of the worker). */
export const DEFAULT_SOCIAL_SEARCH_SETTINGS: SocialSearchSettings = { actorTimeoutSeconds: 25, maximumMentions: 40 };

/**
 * Social search through Apify actors (Reddit and X side by side), with the synchronous endpoint
 * `run-sync-get-dataset-items` and a cap on the money of each run (`maxTotalChargeUsd`). Only posts that name the
 * company are kept, anonymized (no username, handle, email, phone, link; 400 characters at most). The results are
 * never stored: the reputation service keeps the summary of the AI for 30 days per company.
 */
export class ApifySocialSearch implements SocialSearch {
  readonly name = "apify_social";
  readonly paid = true;

  /**
   * @param token Apify API token (secret `APIFY_TOKEN`).
   * @param actors one actor per network.
   * @param logger structured logger.
   * @param fetcher fetch function.
   * @param settings timeouts and limits.
   */
  constructor(
    private readonly token: string,
    private readonly actors: readonly SocialActor[],
    private readonly logger: Logger,
    private readonly fetcher: FetchFunction = fetch,
    private readonly settings: SocialSearchSettings = DEFAULT_SOCIAL_SEARCH_SETTINGS,
  ) {}

  /** @inheritdoc */
  estimateCostMicroUsd(): number {
    return this.actors.reduce((sum, actor) => sum + this.maximumCost(actor), 0);
  }

  private maximumCost(actor: SocialActor): number {
    return Math.max(actor.minimumChargeMicroUsd, actor.maximumItems * actor.pricePerResultMicroUsd);
  }

  /** @inheritdoc */
  async search(query: { company: string | null; jobTitle: string; city: string | null }): Promise<{
    mentions: readonly SocialMention[];
    costMicroUsd: number;
  }> {
    const company = query.company?.replace(/["\n\r]/g, " ").replace(/\s+/g, " ").trim().slice(0, 80) ?? "";
    // Without a company name, a search would only bring posts about other employers.
    if (company.length < 2 || this.actors.length === 0) return { mentions: [], costMicroUsd: 0 };
    const folded = normalizeSearchText(company);
    const runs = await Promise.allSettled(this.actors.map((actor) => this.run(actor, company)));
    let cost = 0;
    let failures = 0;
    const mentions: SocialMention[] = [];
    const seen = new Set<string>();
    runs.forEach((run, index) => {
      const actor = this.actors[index];
      if (run.status === "rejected") {
        failures += 1;
        cost += actor.minimumChargeMicroUsd;
        const code = run.reason instanceof ProviderError ? run.reason.code : "failed";
        this.logger.log("warning", "social_source_failed", { source: actor.source, error: code });
        return;
      }
      cost += Math.max(actor.minimumChargeMicroUsd, run.value.length * actor.pricePerResultMicroUsd);
      for (const item of run.value) {
        const read = actor.readItem(item);
        if (!read || !normalizeSearchText(read.text).includes(folded)) continue;
        const url = anonymousPostLink(actor.source, read.url);
        if (!url || seen.has(url)) continue;
        seen.add(url);
        mentions.push({ source: actor.source, url, publishedOn: read.publishedOn, text: anonymizePost(read.text) });
      }
    });
    if (failures === this.actors.length) throw new ProviderError(this.name, "all_sources_failed", true);
    return { mentions: mentions.slice(0, this.settings.maximumMentions), costMicroUsd: cost };
  }

  private async run(actor: SocialActor, company: string): Promise<Record<string, unknown>[]> {
    const parameters = new URLSearchParams({
      timeout: String(this.settings.actorTimeoutSeconds),
      maxItems: String(actor.maximumItems),
      maxTotalChargeUsd: (this.maximumCost(actor) / 1_000_000 + 0.01).toFixed(3),
      clean: "true",
      format: "json",
    });
    let response: Response;
    try {
      response = await this.fetcher(
        `https://api.apify.com/v2/acts/${encodeURIComponent(actor.actorId)}/run-sync-get-dataset-items?${parameters}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json", Authorization: `Bearer ${this.token}` },
          body: JSON.stringify(actor.buildInput(company)),
          signal: AbortSignal.timeout((this.settings.actorTimeoutSeconds + 5) * 1000),
        },
      );
    } catch {
      throw new ProviderError(`apify_${actor.source}`, "timeout", true);
    }
    if (!response.ok) {
      await response.body?.cancel();
      const retryable = response.status === 408 || response.status === 429 || response.status >= 500;
      throw new ProviderError(`apify_${actor.source}`, `http_${response.status}`, retryable);
    }
    const items = await response.json();
    if (!Array.isArray(items)) throw new ProviderError(`apify_${actor.source}`, "invalid_answer", false);
    return items.filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null);
  }
}
