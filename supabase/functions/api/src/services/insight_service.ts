import { type Database, queryOne } from "../database/database.ts";
import { distanceKilometers, findCity } from "../geography/cities.ts";
import { notFound } from "../http/api_error.ts";
import type { Clock } from "../operations/clock.ts";
import { utcDay } from "../operations/clock.ts";
import {
  estimateTwins,
  negotiationTarget,
  type ReliabilityReason,
  type SalaryScale,
  salaryScale,
  type Trend,
} from "../scoring/estimation.ts";
import { type Language, LOWERING_REASONS, reliabilityReasonText } from "../scoring/methodology_texts.ts";
import type { SalaryRange } from "../providers/provider_types.ts";
import { matchesSearch, searchWords } from "../text/search_text.ts";
import type { AnalysisRecord } from "./analysis_records.ts";
import { DEFAULT_RADIUS_KILOMETERS, type SearchArea } from "./area.ts";
import { loadComparables, loadHistory, loadOffersAround } from "./market_data.ts";

/** Sort orders of the twin offers. */
export type TwinSort = "least_disputed" | "best_paid" | "most_recent";

interface SnapshotInsights {
  readonly salaryScale: SalaryScale | null;
  readonly salarySourceCount: number;
  readonly skills: { name: string; sharePercent: number }[];
  readonly trend: Trend;
}

interface InternalResult {
  readonly reasons?: ReliabilityReason[];
  readonly jobFamily?: string;
  readonly departmentCode?: string;
}

function cityLabel(city: string | null | undefined): string {
  return (city ?? "").replace(/\s+\d{1,2}\s*(e|er|ème)$/i, "").trim();
}

/**
 * Details of a completed analysis: salary, skills, trend, twins, methodology, and the feedback of the user.
 */
export class InsightService {
  /**
   * @param database database.
   * @param clock clock.
   */
  constructor(private readonly database: Database, private readonly clock: Clock) {}

  private async insights(analysis: AnalysisRecord): Promise<SnapshotInsights> {
    const row = await queryOne<{ insights: SnapshotInsights | null }>(
      this.database,
      `select insights from public.offer_snapshots where id = $1::uuid`,
      [analysis.snapshotId],
    );
    if (!row?.insights) throw notFound("Insights");
    return row.insights;
  }

  private internal(analysis: AnalysisRecord): InternalResult {
    return (analysis.result?.internal as InternalResult | undefined) ?? {};
  }

  /** Salary scale of the area (the department of the area when it has data, the one of the offer otherwise). */
  async salary(analysis: AnalysisRecord, area: SearchArea | null): Promise<Record<string, unknown>> {
    const internal = this.internal(analysis);
    let scale: SalaryScale | null = null;
    const areaCity = area ? findCity(area.city, area.departmentCode) ?? findCity(area.city) : null;
    const areaDepartment = area?.departmentCode ?? areaCity?.departmentCode ?? null;
    let areaName = cityLabel(analysis.offer?.city as string | undefined);
    if (areaDepartment && internal.jobFamily && areaDepartment !== internal.departmentCode) {
      const today = utcDay(this.clock.now());
      const comparables = await loadComparables(this.database, internal.jobFamily, areaDepartment, today);
      const history = await loadHistory(this.database, internal.jobFamily, areaDepartment, today);
      scale = salaryScale(comparables, history);
      if (scale) areaName = area?.city ?? areaName;
    }
    if (!scale) scale = (await this.insights(analysis)).salaryScale;
    else if (area?.city) areaName = cityLabel(area.city);
    if (!scale) throw notFound("Salary data");
    const offerRange = (analysis.offer?.displayedSalary as SalaryRange | null | undefined) ??
      (analysis.result?.estimatedSalary as SalaryRange | null | undefined) ?? null;
    return {
      areaName,
      offerCount: scale.offerCount,
      scaleMinimum: Math.min(scale.scaleMinimum, offerRange?.minimum ?? scale.scaleMinimum),
      scaleMaximum: Math.max(scale.scaleMaximum, offerRange?.maximum ?? scale.scaleMaximum),
      middleHalf: scale.middleHalf,
      median: scale.median,
      offerRange,
      negotiationTarget: negotiationTarget(scale, offerRange),
    };
  }

  /** Skills asked for by the comparable offers. */
  async skills(analysis: AnalysisRecord): Promise<Record<string, unknown>> {
    const insights = await this.insights(analysis);
    return { skills: insights.skills.map((skill) => ({ ...skill, owned: null })) };
  }

  /** Applicants per offer over 12 months. */
  async trend(analysis: AnalysisRecord): Promise<Record<string, unknown>> {
    const insights = await this.insights(analysis);
    return { points: insights.trend.points, changeThreeMonthsPercent: insights.trend.changeThreeMonthsPercent };
  }

  /**
   * Twin offers around the search area: same job, fewer applicants, within the radius.
   *
   * @param analysis completed analysis.
   * @param options area, sort and search query.
   */
  async twins(
    analysis: AnalysisRecord,
    options: { area: SearchArea | null; radius: number | null; sort: TwinSort; query: string | null },
  ): Promise<Record<string, unknown>> {
    const internal = this.internal(analysis);
    const radius = options.radius ?? options.area?.radiusKilometers ?? analysis.areaRadiusKilometers ??
      DEFAULT_RADIUS_KILOMETERS;
    const center =
      (options.area ? findCity(options.area.city, options.area.departmentCode) ?? findCity(options.area.city) : null) ??
        findCity(analysis.areaCity, analysis.areaDepartmentCode) ??
        findCity(analysis.offer?.city as string | undefined);
    const areaName = center?.name ?? cityLabel(options.area?.city ?? (analysis.offer?.city as string | undefined));
    if (!center || !internal.jobFamily) return { areaName, radiusKilometers: radius, items: [] };
    const today = utcDay(this.clock.now());
    const candidates = (await loadOffersAround(this.database, internal.jobFamily, center, radius, today))
      .filter((offer) => offer.latitude !== null && offer.longitude !== null)
      .map((offer) => ({
        offer,
        distance: distanceKilometers(center, { latitude: offer.latitude!, longitude: offer.longitude! }),
      }))
      .filter((entry) => entry.distance <= radius);
    const distances = new Map(candidates.map((entry) => [entry.offer.id, entry.distance]));
    const history = await loadHistory(
      this.database,
      internal.jobFamily,
      internal.departmentCode ?? center.departmentCode,
      today,
    );
    const twins = estimateTwins({
      comparables: candidates.map((entry) => entry.offer),
      history,
      today,
      targetScore: Number(analysis.result?.score ?? 100),
      excludedExternalUrl: (analysis.offer?.sourceUrl as string | null | undefined) ?? null,
    });
    const words = searchWords(options.query);
    const filtered = twins.filter((twin) =>
      matchesSearch(words, twin.offer.jobTitle, twin.offer.company, twin.offer.city)
    );
    const sorted = [...filtered].sort((first, second) => {
      if (options.sort === "best_paid") {
        return (second.offer.salary?.maximum ?? -1) - (first.offer.salary?.maximum ?? -1) || first.score - second.score;
      }
      if (options.sort === "most_recent") {
        return (second.offer.publishedOn ?? "").localeCompare(first.offer.publishedOn ?? "") ||
          first.score - second.score;
      }
      return first.score - second.score || first.estimatedApplicants - second.estimatedApplicants;
    });
    return {
      areaName,
      radiusKilometers: radius,
      items: sorted.slice(0, 50).map((twin) => ({
        id: twin.offer.id,
        jobTitle: twin.offer.jobTitle,
        company: twin.offer.company ?? "",
        place: twin.offer.city ?? "",
        salary: twin.offer.salary,
        estimatedApplicants: twin.estimatedApplicants,
        score: twin.score,
        distanceKilometers: Math.round(distances.get(twin.offer.id) ?? 0),
        publishedOn: twin.offer.publishedOn,
        url: twin.offer.url,
      })),
    };
  }

  /** How the estimate was made, with the reasons of its reliability in the language of the user. */
  async methodology(analysis: AnalysisRecord, language: Language): Promise<Record<string, unknown>> {
    const insights = await this.insights(analysis);
    const result = analysis.result ?? {};
    const reliability = String(result.reliability ?? "low");
    const reasons = this.internal(analysis).reasons ?? [];
    const shown = reliability === "high"
      ? reasons.filter((reason) => !LOWERING_REASONS.includes(reason))
      : reasons.filter((reason) => LOWERING_REASONS.includes(reason));
    return {
      applicants: result.applicants,
      reliability,
      reliabilityReasons: shown.map((reason) => reliabilityReasonText(reason, language, analysis.publishedDaysAgo)),
      comparableOfferCount: Number(result.comparableOfferCount ?? 0),
      regionName: String(result.regionName ?? ""),
      salarySourceCount: insights.salarySourceCount,
    };
  }

  /** Saves the real number of applicants given by the user (used to measure the error each month). */
  async saveFeedback(userId: string, analysis: AnalysisRecord, actualApplicants: number): Promise<void> {
    const estimate = (analysis.result?.applicants as { estimate?: number } | undefined)?.estimate ?? null;
    await this.database.query(
      `insert into public.applicant_feedback (user_id, analysis_id, offer_id, actual_applicants, estimated_applicants)
       values ($1::uuid, $2::uuid, $3::uuid, $4, $5)
       on conflict (user_id, analysis_id) do update set actual_applicants = excluded.actual_applicants, updated_at = now()`,
      [userId, analysis.id, analysis.offerId, actualApplicants, estimate],
    );
  }
}
