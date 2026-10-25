import { type Database, queryOne, toIsoTimestamp } from "../database/database.ts";
import type { Language } from "../scoring/methodology_texts.ts";

/** Kinds of the reading steps shown on screen 03. */
export type StepKind = "position_and_place" | "skills" | "comparison" | "applicants";

/** One reading step. */
export interface ReadingStep {
  readonly kind: StepKind;
  readonly status: "pending" | "running" | "done";
  readonly detail: string | null;
}

/** Steps of a new analysis. */
export function initialSteps(): ReadingStep[] {
  return [
    { kind: "position_and_place", status: "running", detail: null },
    { kind: "skills", status: "pending", detail: null },
    { kind: "comparison", status: "pending", detail: null },
    { kind: "applicants", status: "pending", detail: null },
  ];
}

/** Row of `analyses` as read by the API. */
export interface AnalysisRecord {
  readonly id: string;
  readonly userId: string;
  readonly inputKind: "link" | "manual" | "text" | "image";
  readonly inputOrigin: string | null;
  readonly inputUrl: string | null;
  readonly manualJobTitle: string | null;
  readonly manualCity: string | null;
  readonly areaCity: string | null;
  readonly areaDepartmentCode: string | null;
  readonly areaRadiusKilometers: number | null;
  readonly language: Language;
  readonly offerId: string | null;
  readonly snapshotId: string | null;
  readonly status: "processing" | "completed" | "failed";
  readonly progressPercent: number;
  readonly steps: ReadingStep[];
  readonly publishedDaysAgo: number | null;
  readonly hasDisplayedSalary: boolean | null;
  readonly failureCode: string | null;
  readonly recognizedCompany: string | null;
  readonly quotaWeek: string | null;
  readonly createdAt: string;
  readonly offer: Record<string, unknown> | null;
  readonly result: Record<string, unknown> | null;
  readonly isFavorite: boolean;
}

/** Columns selected for an [AnalysisRecord] (aliases `a` for analyses, `r` for results). */
const ANALYSIS_COLUMNS = `
  a.id::text as id, a.user_id::text as user_id, a.input_kind, a.input_origin, a.input_url, a.manual_job_title,
  a.manual_city, a.area_city, a.area_department_code, a.area_radius_kilometers, a.language, a.offer_id::text as offer_id,
  a.snapshot_id::text as snapshot_id, a.status, a.progress_percent, a.steps, a.published_days_ago,
  a.has_displayed_salary, a.failure_code, a.recognized_company, a.quota_week::text as quota_week, a.created_at,
  r.offer as result_offer, r.result as result_result,
  exists (select 1 from public.favorites f where f.user_id = a.user_id and f.analysis_id = a.id) as is_favorite`;

type AnalysisRow = Record<string, unknown>;

function toRecord(row: AnalysisRow): AnalysisRecord {
  return {
    id: String(row.id),
    userId: String(row.user_id),
    inputKind: row.input_kind as AnalysisRecord["inputKind"],
    inputOrigin: (row.input_origin as string | null) ?? null,
    inputUrl: (row.input_url as string | null) ?? null,
    manualJobTitle: (row.manual_job_title as string | null) ?? null,
    manualCity: (row.manual_city as string | null) ?? null,
    areaCity: (row.area_city as string | null) ?? null,
    areaDepartmentCode: (row.area_department_code as string | null) ?? null,
    areaRadiusKilometers: (row.area_radius_kilometers as number | null) ?? null,
    language: row.language === "en" ? "en" : "fr",
    offerId: (row.offer_id as string | null) ?? null,
    snapshotId: (row.snapshot_id as string | null) ?? null,
    status: row.status as AnalysisRecord["status"],
    progressPercent: Number(row.progress_percent ?? 0),
    steps: (row.steps as ReadingStep[] | null) ?? [],
    publishedDaysAgo: (row.published_days_ago as number | null) ?? null,
    hasDisplayedSalary: (row.has_displayed_salary as boolean | null) ?? null,
    failureCode: (row.failure_code as string | null) ?? null,
    recognizedCompany: (row.recognized_company as string | null) ?? null,
    quotaWeek: (row.quota_week as string | null) ?? null,
    createdAt: toIsoTimestamp(row.created_at),
    offer: (row.result_offer as Record<string, unknown> | null) ?? null,
    result: (row.result_result as Record<string, unknown> | null) ?? null,
    isFavorite: row.is_favorite === true,
  };
}

/**
 * Reads one analysis.
 *
 * @param database database.
 * @param analysisId identifier.
 * @param userId owner, or null to read any analysis (worker).
 */
export async function findAnalysis(
  database: Database,
  analysisId: string,
  userId: string | null,
): Promise<AnalysisRecord | null> {
  const row = await queryOne<AnalysisRow>(
    database,
    `select ${ANALYSIS_COLUMNS}
     from public.analyses a left join public.analysis_results r on r.analysis_id = a.id
     where a.id = $1::uuid and ($2::uuid is null or a.user_id = $2::uuid)`,
    [analysisId, userId],
  );
  return row ? toRecord(row) : null;
}

/**
 * Reads a page of the analyses of a user, newest first (keyset pagination on created_at and id).
 *
 * @param database database.
 * @param userId owner.
 * @param options cursor position, page size and folded search words.
 */
export async function listAnalyses(
  database: Database,
  userId: string,
  options: { before: { createdAt: string; id: string } | null; limit: number; likePatterns: string[] },
): Promise<AnalysisRecord[]> {
  const rows = await database.query<AnalysisRow>(
    `select ${ANALYSIS_COLUMNS}
     from public.analyses a left join public.analysis_results r on r.analysis_id = a.id
     where a.user_id = $1::uuid
       and a.status = 'completed'
       and ($2::timestamptz is null or (a.created_at, a.id) < ($2::timestamptz, $3::uuid))
       and a.search_text like all (array(select jsonb_array_elements_text($4::jsonb)))
     order by a.created_at desc, a.id desc
     limit $5`,
    [
      userId,
      options.before?.createdAt ?? null,
      options.before?.id ?? null,
      JSON.stringify(options.likePatterns),
      options.limit,
    ],
  );
  return rows.map(toRecord);
}

/** Contract representation of an analysis (`Analysis` of docs/openapi.yaml). */
export function presentAnalysis(record: AnalysisRecord): Record<string, unknown> {
  const body: Record<string, unknown> = {
    id: record.id,
    status: record.status,
    createdAt: record.createdAt,
    progressPercent: record.status === "completed" ? 100 : record.progressPercent,
    steps: record.steps,
    publishedDaysAgo: record.publishedDaysAgo,
    hasDisplayedSalary: record.hasDisplayedSalary,
    offer: record.status === "completed" ? record.offer : null,
    result: record.status === "completed" ? publicResult(record.result) : null,
    isFavorite: record.isFavorite,
    failure: record.status === "failed"
      ? { code: record.failureCode ?? "internal", recognizedCompany: record.recognizedCompany }
      : null,
  };
  return body;
}

/**
 * Result as sent to clients (internal fields removed). The reputation of the company is internal context of the
 * advice and never leaves the server, also for the results stored before it was taken out of the contract.
 */
export function publicResult(result: Record<string, unknown> | null): Record<string, unknown> | null {
  if (!result) return null;
  const { internal: _internal, reputation: _reputation, ...visible } = result;
  return visible;
}

/** Contract representation of a list item (`AnalysisSummary`). */
export function presentSummary(record: AnalysisRecord): Record<string, unknown> {
  const offer = record.offer ?? {};
  const result = record.result ?? {};
  return {
    id: record.id,
    createdAt: record.createdAt,
    jobTitle: (offer.jobTitle as string | undefined) ?? record.manualJobTitle ?? "",
    company: (offer.company as string | null | undefined) ?? null,
    place: (offer.city as string | undefined) ?? record.manualCity ?? record.areaCity ?? "",
    score: (result.score as number | undefined) ?? 0,
    isFavorite: record.isFavorite,
    status: record.status,
  };
}
