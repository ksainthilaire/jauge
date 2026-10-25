/**
 * Estimation of the competition of an offer, its salary, the trend of its market and its twin offers. Pure
 * functions, documented step by step in docs/backend/score.md (the screen "Comment on estime" explains the same
 * thing to the user).
 */

import type { SalaryRange } from "../providers/provider_types.ts";
import { normalizeSearchText } from "../text/search_text.ts";

/** Time constant of the arrival of applications: about 70 % of them arrive in the first 5 days. */
export const ARRIVAL_TIME_CONSTANT_DAYS = 4.2;
/** Applicants of an offer when nothing is known about its market (France, all jobs). */
export const PRIOR_FINAL_APPLICANTS = 80;
/** Final applicants at which the absolute part of the score reaches 100. */
export const SCORE_SATURATION_APPLICANTS = 400;
/** Twins must have been published within these days (older offers are usually closed). */
export const TWIN_MAXIMUM_AGE_DAYS = 45;

/** Reliability of an estimate and its usual error. */
export type Reliability = "high" | "medium" | "low";

/** Usual relative error of each reliability level (shown on the screen "Comment on estime"). */
export const RELIABILITY_SPREAD: Readonly<Record<Reliability, number>> = { high: 0.15, medium: 0.25, low: 0.4 };

/** Reasons that raise or lower the reliability (texts in methodology_texts.ts). */
export type ReliabilityReason =
  | "applicants_shown_by_site"
  | "many_comparable_offers"
  | "few_comparable_offers"
  | "recent_offer"
  | "unknown_publication_date"
  | "displayed_salary"
  | "no_displayed_salary"
  | "partial_sources";

/** An offer of the market, as read from `collected_offers`. */
export interface MarketOffer {
  readonly id: string;
  readonly jobTitle: string;
  readonly company: string | null;
  readonly city: string | null;
  readonly departmentCode: string | null;
  readonly latitude: number | null;
  readonly longitude: number | null;
  readonly publishedOn: string | null;
  readonly salary: SalaryRange | null;
  readonly applicantCount: number | null;
  readonly applicantCountObservedAt: string | null;
  readonly contractType: string | null;
  readonly workMode: string | null;
  readonly skills: readonly string[];
  readonly url: string | null;
}

/** What is known about the offer to estimate. */
export interface TargetOffer {
  readonly publishedOn: string | null;
  readonly displayedSalary: SalaryRange | null;
  readonly contractType: string | null;
  readonly workMode: string | null;
  readonly displayedApplicantCount: number | null;
}

/** Month of history of the market (table market_statistics). */
export interface MarketHistoryPoint {
  /** First day of the month (`2026-10-01`). */
  readonly month: string;
  readonly applicantsPerOffer: number | null;
  readonly salaryMedian: number | null;
  readonly salaryFirstQuartile: number | null;
  readonly salaryThirdQuartile: number | null;
  readonly salaryOfferCount: number;
}

/** Estimate of the applicants of an offer. */
export interface CompetitionEstimate {
  /** Most likely number of applicants today. */
  readonly estimate: number;
  readonly low: number;
  readonly high: number;
  readonly reliability: Reliability;
  readonly reasons: readonly ReliabilityReason[];
  /** Expected applicants when the offer closes, used for comparisons. */
  readonly finalApplicants: number;
  /** Share of the comparable offers that get fewer applicants. */
  readonly percentileInRegion: number;
  /** Score from 0 (calm) to 100 (crowded). */
  readonly score: number;
  /** Final applicants of a typical offer of the market (median of the anchors, or history, or prior). */
  readonly marketLevel: number;
  /** Offers with a known number of applicants used as anchors. */
  readonly anchorCount: number;
  readonly comparableOfferCount: number;
}

/** Share of the applications received after a number of days online. */
export function arrivalShare(days: number): number {
  return 1 - Math.exp(-Math.max(days, 0) / ARRIVAL_TIME_CONSTANT_DAYS);
}

/** Whole days between two ISO dates (or date times). */
export function daysBetweenDates(start: string, end: string): number {
  return Math.floor((Date.parse(end.slice(0, 10)) - Date.parse(start.slice(0, 10))) / 86_400_000);
}

/** Rounds a count the way people say it: 212 gives 210, 37 gives 35, 8 stays 8. */
export function roundCount(value: number): number {
  if (value >= 100) return Math.round(value / 10) * 10;
  if (value >= 20) return Math.round(value / 5) * 5;
  return Math.max(1, Math.round(value));
}

/** Value at a quantile (0 to 1) of sorted values, linear interpolation. */
export function quantile(sortedValues: readonly number[], position: number): number {
  if (sortedValues.length === 0) return Number.NaN;
  const index = (sortedValues.length - 1) * position;
  const lower = Math.floor(index);
  const upper = Math.ceil(index);
  return sortedValues[lower] + (sortedValues[upper] - sortedValues[lower]) * (index - lower);
}

function weightedMedian(entries: readonly { value: number; weight: number }[]): number {
  const sorted = [...entries].sort((first, second) => first.value - second.value);
  const total = sorted.reduce((sum, entry) => sum + entry.weight, 0);
  let cumulative = 0;
  for (const entry of sorted) {
    cumulative += entry.weight;
    if (cumulative >= total / 2) return entry.value;
  }
  return sorted[sorted.length - 1]?.value ?? Number.NaN;
}

/** Multiplier of the applicants of an offer from its own features (salary shown, remote work, contract). */
export function offerFactor(
  offer: {
    displayedSalary?: SalaryRange | null;
    salary?: SalaryRange | null;
    workMode: string | null;
    contractType: string | null;
  },
): number {
  let factor = 1;
  if (offer.displayedSalary ?? offer.salary) factor *= 1.15;
  if (offer.workMode && /tele|remote|hybride|distance/.test(normalizeSearchText(offer.workMode))) factor *= 1.15;
  switch (offer.contractType) {
    case "internship":
    case "apprenticeship":
      factor *= 1.3;
      break;
    case "fixed_term":
      factor *= 0.9;
      break;
    case "freelance":
    case "temporary":
      factor *= 0.8;
      break;
  }
  return factor;
}

/** Final applicants of an anchor: its shown count divided by the share already arrived when it was read. */
function anchorFinalApplicants(offer: MarketOffer): number | null {
  if (offer.applicantCount === null || !offer.publishedOn || !offer.applicantCountObservedAt) return null;
  const daysOnline = daysBetweenDates(offer.publishedOn, offer.applicantCountObservedAt);
  if (daysOnline < 1) return null;
  return offer.applicantCount / arrivalShare(daysOnline);
}

/**
 * Final applicants of a typical offer of the market: weighted median of the anchors (weight 1 for anchors of the
 * last 90 days, 0.5 before), each divided by its own offer factor; history of the market when there are fewer
 * than 3 anchors; prior otherwise.
 */
export function marketLevel(
  comparables: readonly MarketOffer[],
  history: readonly MarketHistoryPoint[],
  today: string,
): {
  level: number;
  anchorCount: number;
} {
  const anchors: { value: number; weight: number }[] = [];
  for (const offer of comparables) {
    const finalApplicants = anchorFinalApplicants(offer);
    if (finalApplicants === null) continue;
    const age = offer.publishedOn ? daysBetweenDates(offer.publishedOn, today) : 365;
    anchors.push({ value: finalApplicants / offerFactor(offer), weight: age <= 90 ? 1 : 0.5 });
  }
  if (anchors.length >= 3) return { level: weightedMedian(anchors), anchorCount: anchors.length };
  const recentHistory = [...history].reverse().find((point) => point.applicantsPerOffer !== null);
  return { level: recentHistory?.applicantsPerOffer ?? PRIOR_FINAL_APPLICANTS, anchorCount: anchors.length };
}

/** Multiplier from the balance of the market: more offers than usual this month means fewer applicants each. */
export function demandFactor(comparables: readonly MarketOffer[], today: string): number {
  if (comparables.length < 12) return 1;
  const recent =
    comparables.filter((offer) => offer.publishedOn && daysBetweenDates(offer.publishedOn, today) <= 30).length;
  const monthlyAverage = comparables.length / 12;
  return Math.min(1.3, Math.max(0.75, (monthlyAverage / Math.max(recent, 1)) ** 0.3));
}

/** Score of an offer from its final applicants and its percentile in the market. */
export function competitionScore(finalApplicants: number, percentile: number): number {
  const absolute = (100 * Math.log1p(finalApplicants)) / Math.log1p(SCORE_SATURATION_APPLICANTS);
  return Math.min(100, Math.max(0, Math.round(0.6 * percentile + 0.4 * Math.min(100, absolute))));
}

/** Share (0 to 100) of the values strictly under a value, ties counted half. */
export function percentileOf(value: number, values: readonly number[]): number {
  if (values.length === 0) return 50;
  const under = values.filter((other) => other < value).length;
  const equal = values.filter((other) => other === value).length;
  return Math.round((100 * (under + equal / 2)) / values.length);
}

/** Final applicants expected for each comparable offer (its anchor when shown, the market level otherwise). */
export function comparableFinalApplicants(
  comparables: readonly MarketOffer[],
  level: number,
  demand: number,
): number[] {
  return comparables.map((offer) => anchorFinalApplicants(offer) ?? level * demand * offerFactor(offer));
}

/**
 * Estimates the applicants of an offer.
 *
 * @param input offer, comparable offers (same job family, same department, last 12 months), market history,
 *   date of today, and whether some sources were missing.
 */
export function estimateCompetition(input: {
  target: TargetOffer;
  comparables: readonly MarketOffer[];
  history: readonly MarketHistoryPoint[];
  today: string;
  partialSources: boolean;
}): CompetitionEstimate {
  const { target, comparables, today } = input;
  const { level, anchorCount } = marketLevel(comparables, input.history, today);
  const demand = demandFactor(comparables, today);
  const knownAge = target.publishedOn ? Math.max(0, daysBetweenDates(target.publishedOn, today)) : null;
  const daysOnline = knownAge ?? 7;
  const reasons: ReliabilityReason[] = [];
  let points = 0;

  let finalApplicants = level * demand * offerFactor(target);
  let estimate = finalApplicants * arrivalShare(Math.max(daysOnline, 0.5));
  if (target.displayedApplicantCount !== null && target.displayedApplicantCount > 0) {
    estimate = target.displayedApplicantCount;
    finalApplicants = estimate / arrivalShare(Math.max(daysOnline, 0.5));
    reasons.push("applicants_shown_by_site");
    points += 3;
  }
  if (anchorCount >= 30) {
    points += 2;
    reasons.push("many_comparable_offers");
  } else if (anchorCount >= 8) {
    points += 1;
  } else {
    reasons.push("few_comparable_offers");
  }
  if (knownAge === null) {
    points -= 1;
    reasons.push("unknown_publication_date");
  } else if (knownAge >= 3) {
    points += 1;
  } else {
    reasons.push("recent_offer");
  }
  if (target.displayedSalary) {
    points += 1;
    reasons.push("displayed_salary");
  } else {
    reasons.push("no_displayed_salary");
  }
  if (input.partialSources) {
    points -= 1;
    reasons.push("partial_sources");
  }
  const reliability: Reliability = points >= 3 ? "high" : points === 2 ? "medium" : "low";
  const spread = RELIABILITY_SPREAD[reliability];
  const rounded = roundCount(estimate);
  const percentile = percentileOf(finalApplicants, comparableFinalApplicants(comparables, level, demand));
  return {
    estimate: rounded,
    low: roundCount(rounded * (1 - spread)),
    high: roundCount(rounded * (1 + spread)),
    reliability,
    reasons,
    finalApplicants,
    percentileInRegion: Math.min(100, Math.max(0, percentile)),
    score: competitionScore(finalApplicants, percentile),
    marketLevel: level,
    anchorCount,
    comparableOfferCount: comparables.length,
  };
}

/** A twin offer with its own estimate. */
export interface TwinEstimate {
  readonly offer: MarketOffer;
  readonly estimatedApplicants: number;
  readonly score: number;
}

/**
 * Estimates every recent comparable offer (published within 45 days) so that twins can be chosen by area later.
 * Twins are the ones with a lower score than the offer.
 *
 * @param input comparables, market level, today, and the score of the offer.
 */
export function estimateTwins(input: {
  comparables: readonly MarketOffer[];
  history: readonly MarketHistoryPoint[];
  today: string;
  targetScore: number;
  excludedExternalUrl: string | null;
}): TwinEstimate[] {
  const { level } = marketLevel(input.comparables, input.history, input.today);
  const demand = demandFactor(input.comparables, input.today);
  const finals = comparableFinalApplicants(input.comparables, level, demand);
  const twins: TwinEstimate[] = [];
  input.comparables.forEach((offer, index) => {
    if (!offer.publishedOn) return;
    const age = daysBetweenDates(offer.publishedOn, input.today);
    if (age < 0 || age > TWIN_MAXIMUM_AGE_DAYS) return;
    if (input.excludedExternalUrl && offer.url === input.excludedExternalUrl) return;
    const finalApplicants = finals[index];
    const score = competitionScore(finalApplicants, percentileOf(finalApplicants, finals));
    if (score >= input.targetScore) return;
    const estimated = offer.applicantCount ?? finalApplicants * arrivalShare(Math.max(age, 0.5));
    twins.push({ offer, estimatedApplicants: roundCount(estimated), score });
  });
  return twins.sort((first, second) => first.score - second.score);
}

/** Salary scale of a market. */
export interface SalaryScale {
  readonly offerCount: number;
  readonly scaleMinimum: number;
  readonly scaleMaximum: number;
  readonly middleHalf: SalaryRange;
  readonly median: number;
}

/**
 * Salary scale from the offers that show a salary (middle of each range): 10th and 90th percentiles for the scale,
 * quartiles for the middle half. Falls back to the history of the market, and returns null without data.
 */
export function salaryScale(
  offers: readonly MarketOffer[],
  history: readonly MarketHistoryPoint[],
): SalaryScale | null {
  const salaries = offers
    .filter((offer) => offer.salary !== null)
    .map((offer) => (offer.salary!.minimum + offer.salary!.maximum) / 2)
    .sort((first, second) => first - second);
  if (salaries.length >= 3) {
    const scaleMinimum = Math.floor(quantile(salaries, 0.1));
    const scaleMaximum = Math.max(Math.ceil(quantile(salaries, 0.9)), scaleMinimum + 2);
    const first = Math.round(quantile(salaries, 0.25));
    const third = Math.max(Math.round(quantile(salaries, 0.75)), first);
    return {
      offerCount: salaries.length,
      scaleMinimum,
      scaleMaximum,
      middleHalf: { minimum: first, maximum: third },
      median: Math.round(quantile(salaries, 0.5)),
    };
  }
  const point = [...history].reverse().find((entry) =>
    entry.salaryMedian !== null && entry.salaryFirstQuartile !== null && entry.salaryThirdQuartile !== null
  );
  if (!point) return null;
  const first = point.salaryFirstQuartile!;
  const third = point.salaryThirdQuartile!;
  return {
    offerCount: point.salaryOfferCount,
    scaleMinimum: Math.max(1, first - Math.ceil((third - first) * 0.8) - 1),
    scaleMaximum: third + Math.ceil((third - first) * 0.8) + 1,
    middleHalf: { minimum: first, maximum: third },
    median: point.salaryMedian!,
  };
}

/**
 * Probable salary of the offer: the displayed one, otherwise the 40th to 65th percentiles of the comparable
 * salaries (offers that hide their salary pay a bit under the middle in general), null without enough data.
 */
export function probableSalary(displayed: SalaryRange | null, offers: readonly MarketOffer[]): SalaryRange | null {
  if (displayed) return displayed;
  const salaries = offers
    .filter((offer) => offer.salary !== null)
    .map((offer) => (offer.salary!.minimum + offer.salary!.maximum) / 2)
    .sort((first, second) => first - second);
  if (salaries.length < 3) return null;
  const minimum = Math.round(quantile(salaries, 0.4));
  return { minimum, maximum: Math.max(minimum + 1, Math.round(quantile(salaries, 0.65))) };
}

/** Salary to ask for: the third quartile, kept inside the range of the offer when it is known. */
export function negotiationTarget(scale: SalaryScale, offerRange: SalaryRange | null): number {
  const target = scale.middleHalf.maximum;
  if (!offerRange) return target;
  return Math.min(offerRange.maximum, Math.max(offerRange.minimum, target));
}

/** Monthly trend of the applicants per offer. */
export interface Trend {
  readonly points: readonly { month: string; applicantsPerOffer: number }[];
  readonly changeThreeMonthsPercent: number;
}

/**
 * Applicants per offer over the last 12 months: median of the anchors of each month (final applicants), the
 * history of the market for months without 2 anchors, months without any data are left out.
 */
export function marketTrend(
  comparables: readonly MarketOffer[],
  history: readonly MarketHistoryPoint[],
  today: string,
): Trend {
  const months: string[] = [];
  const start = new Date(`${today.slice(0, 7)}-01T00:00:00Z`);
  for (let offset = 11; offset >= 0; offset--) {
    const month = new Date(start);
    month.setUTCMonth(month.getUTCMonth() - offset);
    months.push(month.toISOString().slice(0, 7));
  }
  const byMonth = new Map<string, number[]>();
  for (const offer of comparables) {
    const finalApplicants = anchorFinalApplicants(offer);
    if (finalApplicants === null || !offer.publishedOn) continue;
    const key = offer.publishedOn.slice(0, 7);
    byMonth.set(key, [...(byMonth.get(key) ?? []), finalApplicants]);
  }
  const historyByMonth = new Map(history.map((point) => [point.month.slice(0, 7), point.applicantsPerOffer]));
  const points: { month: string; applicantsPerOffer: number }[] = [];
  for (const month of months) {
    const values = (byMonth.get(month) ?? []).sort((first, second) => first - second);
    const value = values.length >= 2 ? quantile(values, 0.5) : historyByMonth.get(month) ?? null;
    if (value !== null && Number.isFinite(value)) points.push({ month, applicantsPerOffer: Math.round(value) });
  }
  const last = points[points.length - 1];
  const threeMonthsBefore = last ? points.find((point) => monthDistance(point.month, last.month) === 3) : undefined;
  const change = last && threeMonthsBefore && threeMonthsBefore.applicantsPerOffer > 0
    ? Math.round(
      (100 * (last.applicantsPerOffer - threeMonthsBefore.applicantsPerOffer)) / threeMonthsBefore.applicantsPerOffer,
    )
    : 0;
  return { points, changeThreeMonthsPercent: change };
}

function monthDistance(first: string, second: string): number {
  const [firstYear, firstMonth] = first.split("-").map(Number);
  const [secondYear, secondMonth] = second.split("-").map(Number);
  return (secondYear - firstYear) * 12 + (secondMonth - firstMonth);
}

/** Skills asked for by the comparable offers, with their share. */
export function skillDemand(offers: readonly MarketOffer[], limit = 8): { name: string; sharePercent: number }[] {
  const withSkills = offers.filter((offer) => offer.skills.length > 0);
  if (withSkills.length === 0) return [];
  const counts = new Map<string, { count: number; spellings: Map<string, number> }>();
  for (const offer of withSkills) {
    const seen = new Set<string>();
    for (const skill of offer.skills) {
      const key = normalizeSearchText(skill);
      if (!key || seen.has(key)) continue;
      seen.add(key);
      const entry = counts.get(key) ?? { count: 0, spellings: new Map<string, number>() };
      entry.count += 1;
      entry.spellings.set(skill.trim(), (entry.spellings.get(skill.trim()) ?? 0) + 1);
      counts.set(key, entry);
    }
  }
  return [...counts.values()]
    .map((entry) => ({
      name: [...entry.spellings.entries()].sort((first, second) => second[1] - first[1])[0][0],
      sharePercent: Math.round((100 * entry.count) / withSkills.length),
    }))
    .filter((skill) => skill.sharePercent >= 5)
    .sort((first, second) => second.sharePercent - first.sharePercent || first.name.localeCompare(second.name))
    .slice(0, limit);
}

/** Days of the week of the contract. */
const WEEKDAYS = ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"] as const;

/** Timing advice: "apply before Thursday, about 70 % of the applications arrive in 5 days". */
export interface TimingAdvice {
  readonly applyBefore: (typeof WEEKDAYS)[number];
  readonly sharePercent: number;
  readonly withinDays: number;
}

/**
 * Advice on when to apply, while most applications are still to come: null when the publication date is unknown
 * or the offer is already older than the window.
 */
export function timingAdvice(publishedOn: string | null, today: string): TimingAdvice | null {
  if (!publishedOn) return null;
  const withinDays = Math.round(-ARRIVAL_TIME_CONSTANT_DAYS * Math.log(0.3));
  const deadline = new Date(`${publishedOn}T00:00:00Z`);
  deadline.setUTCDate(deadline.getUTCDate() + withinDays);
  if (deadline.toISOString().slice(0, 10) <= today) return null;
  return {
    applyBefore: WEEKDAYS[deadline.getUTCDay()],
    sharePercent: Math.round(100 * arrivalShare(withinDays)),
    withinDays,
  };
}
