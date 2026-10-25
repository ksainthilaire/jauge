import { REPUTATION_PROMPT_VERSION } from "../ai/prompts/v1/reputation.ts";
import { type Database, queryOne } from "../database/database.ts";
import { sha256Hex } from "../offers/offer_key.ts";
import type { Clock } from "../operations/clock.ts";
import type { Logger } from "../operations/logger.ts";
import type { SpendGuard } from "../operations/spend_guard.ts";
import type { Providers, Reputation, ReputationLink } from "../providers/provider_types.ts";
import { normalizeSearchText } from "../text/search_text.ts";

/** Days a reputation (or the fact that nothing was found) is reused for every user. */
export const DEFAULT_REPUTATION_CACHE_DAYS = 30;

/** Legal forms and words that do not change the company ("Atelier Sorel SAS" is "Atelier Sorel"). */
const COMPANY_NOISE = /\b(sas|sasu|sarl|eurl|sa|sca|snc|scop|gmbh|ltd|inc|llc|group|groupe|france)\b/g;

/**
 * Key of the cache of a company: sha256 of its folded name without legal form.
 *
 * @param company company as written in the offer.
 */
export async function companyKey(company: string): Promise<string> {
  const folded = normalizeSearchText(company).replace(COMPANY_NOISE, " ").replace(/\s+/g, " ").trim();
  return await sha256Hex(folded || normalizeSearchText(company));
}

interface ReputationRow {
  found: boolean;
  tone: Reputation["tone"] | null;
  summary_french: string | null;
  summary_english: string | null;
  mention_count: number;
  sources: string[];
  links: ReputationLink[];
}

/**
 * Reputation of the company of an offer from public Reddit and X posts, summarized by the AI. One search and one
 * summary per company every 30 days, whatever the number of users and offers (the summary is written in French and
 * in English at once). "Nothing found" is cached too. Failures and the daily limit are not cached, so the next
 * analysis tries again. Costs (social search, AI tokens) are journaled and checked against the daily limit.
 */
export class ReputationService {
  /**
   * @param database database.
   * @param providers social search and AI.
   * @param spendGuard daily spending limit.
   * @param clock clock.
   * @param logger structured logger.
   * @param cacheDays days a reputation is reused.
   */
  constructor(
    private readonly database: Database,
    private readonly providers: Providers,
    private readonly spendGuard: SpendGuard,
    private readonly clock: Clock,
    private readonly logger: Logger,
    private readonly cacheDays: number = DEFAULT_REPUTATION_CACHE_DAYS,
  ) {}

  /**
   * Reputation of a company in a language, from the cache or from a new search. Null when nothing useful was found
   * or when it cannot be known now (provider down, daily limit). Throws on provider errors (the caller goes on).
   *
   * @param offer company and job title of the offer.
   * @param language language of the analysis.
   * @param snapshotId snapshot that pays the search (cost journal).
   * @param cacheOnly true to read the cache without searching (the social stage already searched).
   */
  async reputationFor(
    offer: { company: string | null; jobTitle: string; city: string | null },
    language: "fr" | "en",
    snapshotId: string | null,
    cacheOnly = false,
  ): Promise<Reputation | null> {
    const company = offer.company?.trim();
    if (!company || company.length < 2) return null;
    const key = await companyKey(company);
    const cached = await queryOne<ReputationRow>(
      this.database,
      `select found, tone, summary_french, summary_english, mention_count, sources, links
       from public.company_reputations where company_key = $1 and expires_at > $2::timestamptz`,
      [key, this.clock.now().toISOString()],
    );
    if (cached) return toReputation(cached, language);
    if (cacheOnly) return null;

    const search = this.providers.socialSearch;
    if (search.paid && !(await this.spendGuard.allows(search.estimateCostMicroUsd()))) return null;
    const found = await search.search({ company, jobTitle: offer.jobTitle, city: offer.city });
    await this.spendGuard.record({
      provider: search.name,
      operation: "social_search",
      costMicroUsd: found.costMicroUsd,
      units: found.mentions.length,
      snapshotId,
    });
    if (found.mentions.length === 0) {
      await this.save(key, null);
      return null;
    }
    const ai = this.providers.ai;
    if (!(await this.spendGuard.allows(ai.estimateCostMicroUsd("reputation")))) return null;
    const summarized = await ai.summarizeReputation({ company, jobTitle: offer.jobTitle, mentions: found.mentions });
    await this.spendGuard.record({
      provider: ai.name,
      operation: "reputation",
      costMicroUsd: summarized.costMicroUsd,
      snapshotId,
      usage: summarized.usage,
    });
    const row: ReputationRow | null = summarized.summary
      ? {
        found: true,
        tone: summarized.summary.tone,
        summary_french: summarized.summary.summaryFrench,
        summary_english: summarized.summary.summaryEnglish,
        mention_count: summarized.summary.mentionCount,
        sources: [...summarized.summary.sources],
        links: [...summarized.summary.links],
      }
      : null;
    await this.save(key, row);
    this.logger.log("info", "reputation_searched", {
      mentions: found.mentions.length,
      found: row !== null,
    });
    return row ? toReputation(row, language) : null;
  }

  private async save(key: string, row: ReputationRow | null): Promise<void> {
    const now = this.clock.now();
    await this.database.query(
      `insert into public.company_reputations (company_key, found, tone, summary_french, summary_english, mention_count,
                                               sources, links, prompt_version, searched_at, expires_at)
       values ($1, $2, $3, $4, $5, $6, array(select jsonb_array_elements_text($7::jsonb)), $8::jsonb, $9,
               $10::timestamptz, $11::timestamptz)
       on conflict (company_key) do update set
         found = excluded.found, tone = excluded.tone, summary_french = excluded.summary_french,
         summary_english = excluded.summary_english, mention_count = excluded.mention_count, sources = excluded.sources,
         links = excluded.links, prompt_version = excluded.prompt_version, searched_at = excluded.searched_at,
         expires_at = excluded.expires_at`,
      [
        key,
        row !== null,
        row?.tone ?? null,
        row?.summary_french?.slice(0, 400) ?? null,
        row?.summary_english?.slice(0, 400) ?? null,
        row?.mention_count ?? 0,
        JSON.stringify(row?.sources ?? []),
        JSON.stringify(row?.links ?? []),
        REPUTATION_PROMPT_VERSION,
        now.toISOString(),
        new Date(now.getTime() + this.cacheDays * 86_400_000).toISOString(),
      ],
    );
  }
}

function toReputation(row: ReputationRow, language: "fr" | "en"): Reputation | null {
  const summary = language === "fr" ? row.summary_french : row.summary_english;
  if (!row.found || !row.tone || !summary) return null;
  return {
    summary,
    tone: row.tone,
    mentionCount: row.mention_count,
    sources: row.sources.filter((source): source is "reddit" | "x" => source === "reddit" || source === "x"),
    links: row.links,
  };
}
