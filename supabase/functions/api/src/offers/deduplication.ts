import { cityKey } from "../geography/cities.ts";
import type { MarketOffer } from "../scoring/estimation.ts";
import { normalizeSearchText } from "../text/search_text.ts";
import { jobFamily } from "./offer_key.ts";

/**
 * Deduplication of the market offers between sources: the same offer is often published on LinkedIn, Indeed and
 * HelloWork (and reposted by job boards). Each source keeps its own row (`collected_offers`, unique per source and
 * identifier), and the market is read with one entry per real offer, so that a job posted on three sites does not
 * count three times in the competition, the salaries, the trend and the twins.
 *
 * Two offers are the same when they have the same job family, the same company and the same city, and were published
 * within 21 days of each other. Without a company nothing is merged (two anonymous offers may be different jobs).
 */

/** Days between two publications of the same offer on two sites. */
const SAME_OFFER_DAYS = 21;

/** Words that do not identify a company ("SAS", "Groupe", "France"). */
const COMPANY_NOISE = /\b(sas|sasu|sarl|sa|eurl|sca|snc|inc|ltd|llc|gmbh|groupe|group|france|the)\b/g;

/** Normalized company name ("ATELIER SOREL SAS" and "Atelier Sorel" give "ateliersorel"). */
export function companyKey(company: string | null | undefined): string | null {
  if (!company) return null;
  const key = normalizeSearchText(company).replace(COMPANY_NOISE, " ").replace(/[^a-z0-9]+/g, "");
  return key.length >= 2 ? key : null;
}

/** Key of an offer for the deduplication, or null when it cannot be merged with another (no company). */
export function duplicateKey(offer: Pick<MarketOffer, "jobTitle" | "company" | "city">): string | null {
  const company = companyKey(offer.company);
  if (!company) return null;
  return `${jobFamily(offer.jobTitle)}|${company}|${offer.city ? cityKey(offer.city) : ""}`;
}

function days(date: string): number {
  return Date.parse(`${date}T00:00:00Z`) / 86_400_000;
}

/** Applicant count observed most recently first, then the most complete row. */
function richness(offer: MarketOffer): number {
  return (offer.applicantCount !== null ? 1000 : 0) + (offer.salary ? 100 : 0) + (offer.publishedOn ? 10 : 0) +
    Math.min(offer.skills.length, 9);
}

/**
 * Merges the rows of the same offer into one: the row with an applicant count (LinkedIn) is kept as the
 * representative (its identifier is the one the twins point to), the salary and the skills missing from it are taken
 * from the others, and the earliest publication date wins (the offer is as old as its first publication).
 *
 * @param offers market offers of several sources.
 */
export function mergeDuplicateOffers(offers: readonly MarketOffer[]): MarketOffer[] {
  const groups = new Map<string, MarketOffer[][]>();
  const result: (MarketOffer | MarketOffer[])[] = [];
  for (const offer of offers) {
    const key = duplicateKey(offer);
    if (!key) {
      result.push(offer);
      continue;
    }
    const clusters = groups.get(key) ?? [];
    const cluster = clusters.find((members) =>
      members.every((member) =>
        !member.publishedOn || !offer.publishedOn ||
        Math.abs(days(member.publishedOn) - days(offer.publishedOn)) <= SAME_OFFER_DAYS
      )
    );
    if (cluster) {
      cluster.push(offer);
    } else {
      const created = [offer];
      clusters.push(created);
      groups.set(key, clusters);
      result.push(created);
    }
  }
  return result.map((entry) => Array.isArray(entry) ? mergeCluster(entry) : entry);
}

function mergeCluster(members: readonly MarketOffer[]): MarketOffer {
  if (members.length === 1) return members[0];
  const sorted = [...members].sort((first, second) => {
    const difference = richness(second) - richness(first);
    if (difference !== 0) return difference;
    return (second.applicantCountObservedAt ?? "").localeCompare(first.applicantCountObservedAt ?? "");
  });
  const representative = sorted[0];
  const dates = members.map((member) => member.publishedOn).filter((date): date is string => date !== null).sort();
  const skills = [...new Set(members.flatMap((member) => member.skills))].slice(0, 40);
  return {
    ...representative,
    publishedOn: dates[0] ?? null,
    salary: representative.salary ?? sorted.find((member) => member.salary)?.salary ?? null,
    contractType: representative.contractType ?? sorted.find((member) => member.contractType)?.contractType ?? null,
    workMode: representative.workMode ?? sorted.find((member) => member.workMode)?.workMode ?? null,
    latitude: representative.latitude ?? sorted.find((member) => member.latitude !== null)?.latitude ?? null,
    longitude: representative.longitude ?? sorted.find((member) => member.longitude !== null)?.longitude ?? null,
    skills,
  };
}
