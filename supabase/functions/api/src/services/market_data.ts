import { type Database, toIsoTimestampOrNull } from "../database/database.ts";
import { mergeDuplicateOffers } from "../offers/deduplication.ts";
import {
  daysBetweenDates,
  type MarketHistoryPoint,
  type MarketOffer,
  marketTrend,
  quantile,
} from "../scoring/estimation.ts";

/** Columns of `collected_offers` read as [MarketOffer]. */
const MARKET_COLUMNS = `id::text as id, job_title, company, city, department_code, latitude, longitude,
  published_on::text as published_on, salary_minimum, salary_maximum, applicant_count, applicant_count_observed_at,
  contract_type, work_mode, skills, url`;

/** Turns a row of `collected_offers` into a [MarketOffer]. */
export function toMarketOffer(row: Record<string, unknown>): MarketOffer {
  return {
    id: String(row.id),
    jobTitle: String(row.job_title),
    company: (row.company as string | null) ?? null,
    city: (row.city as string | null) ?? null,
    departmentCode: (row.department_code as string | null) ?? null,
    latitude: row.latitude === null || row.latitude === undefined ? null : Number(row.latitude),
    longitude: row.longitude === null || row.longitude === undefined ? null : Number(row.longitude),
    publishedOn: (row.published_on as string | null) ?? null,
    salary: row.salary_minimum !== null && row.salary_maximum !== null && row.salary_minimum !== undefined
      ? { minimum: Number(row.salary_minimum), maximum: Number(row.salary_maximum) }
      : null,
    applicantCount: row.applicant_count === null || row.applicant_count === undefined
      ? null
      : Number(row.applicant_count),
    applicantCountObservedAt: toIsoTimestampOrNull(row.applicant_count_observed_at),
    contractType: (row.contract_type as string | null) ?? null,
    workMode: (row.work_mode as string | null) ?? null,
    skills: (row.skills as string[] | null) ?? [],
    url: (row.url as string | null) ?? null,
  };
}

function yearBefore(today: string): string {
  const date = new Date(`${today}T00:00:00Z`);
  date.setUTCFullYear(date.getUTCFullYear() - 1);
  return date.toISOString().slice(0, 10);
}

/**
 * Comparable offers: same job family, same department, published in the last 12 months (or without date), one entry
 * per real offer (an offer published on several sites is merged, see `offers/deduplication.ts`).
 *
 * @param database database.
 * @param family job family.
 * @param department department code.
 * @param today date of today (ISO).
 */
export async function loadComparables(
  database: Database,
  family: string,
  department: string,
  today: string,
): Promise<MarketOffer[]> {
  const rows = await database.query<Record<string, unknown>>(
    `select ${MARKET_COLUMNS} from public.collected_offers
     where job_family = $1 and department_code = $2 and (published_on is null or published_on >= $3::date)
     order by published_on desc nulls last
     limit 3000`,
    [family, department, yearBefore(today)],
  );
  return mergeDuplicateOffers(rows.map(toMarketOffer));
}

/**
 * Offers of a job family around a point (bounding box, then exact distance in the caller), published within the
 * last 12 months, duplicates between sources merged: candidates of the twins of an area.
 */
export async function loadOffersAround(
  database: Database,
  family: string,
  center: { latitude: number; longitude: number },
  radiusKilometers: number,
  today: string,
): Promise<MarketOffer[]> {
  const latitudeDelta = radiusKilometers / 111;
  const longitudeDelta = radiusKilometers / (111 * Math.max(0.2, Math.cos((center.latitude * Math.PI) / 180)));
  const rows = await database.query<Record<string, unknown>>(
    `select ${MARKET_COLUMNS} from public.collected_offers
     where job_family = $1 and latitude between $2 and $3 and longitude between $4 and $5
       and (published_on is null or published_on >= $6::date)
     limit 3000`,
    [
      family,
      center.latitude - latitudeDelta,
      center.latitude + latitudeDelta,
      center.longitude - longitudeDelta,
      center.longitude + longitudeDelta,
      yearBefore(today),
    ],
  );
  return mergeDuplicateOffers(rows.map(toMarketOffer));
}

/** History of the market: the last 13 months of `market_statistics`, oldest first. */
export async function loadHistory(
  database: Database,
  family: string,
  department: string,
  today: string,
): Promise<MarketHistoryPoint[]> {
  const rows = await database.query<Record<string, unknown>>(
    `select month::text as month, applicants_per_offer, salary_median, salary_first_quartile, salary_third_quartile,
            salary_offer_count
     from public.market_statistics
     where job_family = $1 and department_code = $2 and month >= $3::date
     order by month`,
    [family, department, `${yearBefore(today).slice(0, 7)}-01`],
  );
  return rows.map((row) => ({
    month: String(row.month),
    applicantsPerOffer: row.applicants_per_offer === null ? null : Number(row.applicants_per_offer),
    salaryMedian: row.salary_median === null ? null : Number(row.salary_median),
    salaryFirstQuartile: row.salary_first_quartile === null ? null : Number(row.salary_first_quartile),
    salaryThirdQuartile: row.salary_third_quartile === null ? null : Number(row.salary_third_quartile),
    salaryOfferCount: Number(row.salary_offer_count ?? 0),
  }));
}

/**
 * Saves the monthly statistics of a market (offers, applicants per offer, salary quartiles), so that the trend and
 * the salary scale keep a history when old offers are purged.
 */
export async function upsertMarketStatistics(
  database: Database,
  family: string,
  department: string,
  comparables: readonly MarketOffer[],
  today: string,
): Promise<void> {
  const trend = marketTrend(comparables, [], today);
  const applicantsByMonth = new Map(trend.points.map((point) => [point.month, point.applicantsPerOffer]));
  const byMonth = new Map<string, MarketOffer[]>();
  for (const offer of comparables) {
    if (!offer.publishedOn || daysBetweenDates(offer.publishedOn, today) < 0) continue;
    const month = offer.publishedOn.slice(0, 7);
    byMonth.set(month, [...(byMonth.get(month) ?? []), offer]);
  }
  const rows = [...byMonth.entries()].map(([month, offers]) => {
    const salaries = offers
      .filter((offer) => offer.salary !== null)
      .map((offer) => (offer.salary!.minimum + offer.salary!.maximum) / 2)
      .sort((first, second) => first - second);
    const enough = salaries.length >= 3;
    return {
      month: `${month}-01`,
      offer_count: offers.length,
      applicants_per_offer: applicantsByMonth.get(month) ?? null,
      salary_offer_count: salaries.length,
      salary_first_quartile: enough ? Math.round(quantile(salaries, 0.25)) : null,
      salary_median: enough ? Math.round(quantile(salaries, 0.5)) : null,
      salary_third_quartile: enough ? Math.round(quantile(salaries, 0.75)) : null,
    };
  });
  if (rows.length === 0) return;
  await database.query(
    `insert into public.market_statistics (job_family, department_code, month, offer_count, applicants_per_offer,
       salary_offer_count, salary_first_quartile, salary_median, salary_third_quartile)
     select $1, $2, month::date, offer_count, applicants_per_offer, salary_offer_count, salary_first_quartile,
            salary_median, salary_third_quartile
     from jsonb_to_recordset($3::jsonb) as item(month text, offer_count integer, applicants_per_offer integer,
       salary_offer_count integer, salary_first_quartile integer, salary_median integer, salary_third_quartile integer)
     on conflict (job_family, department_code, month) do update set
       offer_count = greatest(market_statistics.offer_count, excluded.offer_count),
       applicants_per_offer = coalesce(excluded.applicants_per_offer, market_statistics.applicants_per_offer),
       salary_offer_count = greatest(market_statistics.salary_offer_count, excluded.salary_offer_count),
       salary_first_quartile = coalesce(excluded.salary_first_quartile, market_statistics.salary_first_quartile),
       salary_median = coalesce(excluded.salary_median, market_statistics.salary_median),
       salary_third_quartile = coalesce(excluded.salary_third_quartile, market_statistics.salary_third_quartile),
       updated_at = now()`,
    [family, department, JSON.stringify(rows)],
  );
}
