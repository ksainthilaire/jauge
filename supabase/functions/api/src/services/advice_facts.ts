import type { AdviceFacts, Reputation, SalaryRange } from "../providers/provider_types.ts";
import { negotiationTarget, type SalaryScale } from "../scoring/estimation.ts";
import { normalizeSearchText } from "../text/search_text.ts";

/** Smallest share of the comparable offers for a skill to be called missing. */
const MISSING_SKILL_MINIMUM_SHARE = 20;

/** What the pipeline knows when it writes the advice. */
export interface AdviceSources {
  readonly language: "fr" | "en";
  readonly offer: {
    readonly jobTitle: string;
    readonly company: string | null;
    readonly city: string | null;
    readonly contractType: string | null;
    readonly workMode: string | null;
    readonly publishedOn: string | null;
    readonly displayedSalary: SalaryRange | null;
    readonly skills: readonly string[];
  };
  /** Result of the snapshot plus the twins of the user. */
  readonly result: Record<string, unknown>;
  readonly insights: {
    readonly salaryScale: SalaryScale | null;
    readonly skills: readonly { name: string; sharePercent: number }[];
  } | null;
  /** Internal context (never shown): only its summary reaches the AI, never its tone, counts or sources. */
  readonly reputation: Reputation | null;
  /** Date of today (ISO). */
  readonly today: string;
}

/**
 * Facts given to the AI for the advice: only values computed by Jauge (the AI may not write any other number), plus
 * the summary of the public posts about the hiring of the company as internal context.
 *
 * @param sources offer, result, insights and reputation.
 */
export function buildAdviceFacts(sources: AdviceSources): AdviceFacts {
  const result = sources.result;
  const score = Number(result.score ?? 0);
  const applicants = (result.applicants as { estimate: number; low: number; high: number } | undefined) ??
    { estimate: 0, low: 0, high: 0 };
  const estimatedSalary = (result.estimatedSalary as SalaryRange | null | undefined) ?? null;
  const scale = sources.insights?.salaryScale ?? null;
  const offerRange = sources.offer.displayedSalary ?? estimatedSalary;
  const offerSkills = new Set(sources.offer.skills.map((skill) => normalizeSearchText(skill)));
  const marketSkills = (sources.insights?.skills ?? []).slice(0, 8);
  const publishedDaysAgo = sources.offer.publishedOn
    ? Math.max(0, Math.round((Date.parse(sources.today) - Date.parse(sources.offer.publishedOn)) / 86_400_000))
    : null;
  return {
    language: sources.language,
    offer: { ...sources.offer, publishedDaysAgo, skills: sources.offer.skills.slice(0, 20) },
    score,
    scoreLevel: score <= 33 ? "calm" : score <= 66 ? "disputed" : "crowded",
    applicants,
    reliability: (result.reliability as AdviceFacts["reliability"] | undefined) ?? "low",
    percentileInRegion: Number(result.percentileInRegion ?? 0),
    regionName: String(result.regionName ?? ""),
    comparableOfferCount: Number(result.comparableOfferCount ?? 0),
    estimatedSalary,
    salaryMedian: scale?.median ?? null,
    negotiationTarget: scale ? negotiationTarget(scale, offerRange) : null,
    trendThreeMonthsPercent: typeof result.trendThreeMonthsPercent === "number" ? result.trendThreeMonthsPercent : null,
    twinOfferCount: Number(result.twinOfferCount ?? 0),
    twinRadiusKilometers: Number(result.twinRadiusKilometers ?? 0),
    timingAdvice: (result.timingAdvice as AdviceFacts["timingAdvice"] | undefined) ?? null,
    marketSkills,
    missingSkillCandidates: marketSkills
      .filter((skill) => skill.sharePercent >= MISSING_SKILL_MINIMUM_SHARE)
      .filter((skill) => !offerSkills.has(normalizeSearchText(skill.name)))
      .map((skill) => skill.name),
    candidateFeedback: sources.reputation?.summary ?? null,
  };
}
