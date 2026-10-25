import type { Configuration } from "../configuration.ts";
import { type Database, queryOne, toIsoTimestamp } from "../database/database.ts";
import { departmentName, findCity } from "../geography/cities.ts";
import {
  canonicalizeLink,
  jobFamily,
  linkOfferKey,
  manualOfferKey,
  type OfferKey,
  textOfferKey,
} from "../offers/offer_key.ts";
import type { Clock } from "../operations/clock.ts";
import { utcDay } from "../operations/clock.ts";
import type { Logger } from "../operations/logger.ts";
import type { SpendGuard } from "../operations/spend_guard.ts";
import type {
  Advice,
  CollectedOfferInput,
  CollectionRequest,
  ExtractedOffer,
  ExtractionOutcome,
  OfferSource,
  Providers,
  Reputation,
} from "../providers/provider_types.ts";
import { ProviderError } from "../providers/provider_types.ts";
import type { SalaryScale } from "../scoring/estimation.ts";
import {
  daysBetweenDates,
  estimateCompetition,
  estimateTwins,
  marketTrend,
  probableSalary,
  salaryScale,
  skillDemand,
  timingAdvice,
} from "../scoring/estimation.ts";
import { normalizeSearchText } from "../text/search_text.ts";
import { type AnalysisRecord, findAnalysis, type ReadingStep, type StepKind } from "../services/analysis_records.ts";
import { loadComparables, loadHistory, upsertMarketStatistics } from "../services/market_data.ts";
import type { SubscriptionService } from "../services/subscription_service.ts";
import { ReputationService } from "../services/reputation_service.ts";
import { buildAdviceFacts } from "../services/advice_facts.ts";
import { DEFAULT_RADIUS_KILOMETERS } from "../services/area.ts";
import type { Job, JobHandler, StageOutcome } from "./job_types.ts";

/** Settings of the pipeline that tests change. */
export interface PipelineSettings {
  /** Seconds between two polls of a running collection. */
  readonly collectionPollSeconds: number;
  /** Seconds between two checks of a snapshot computed by another analysis. */
  readonly snapshotWaitSeconds: number;
  /** Offers asked to each source per collection. */
  readonly offersPerSource: number;
  /** Seconds before the deadline at which the analysis stops waiting for sources and computes with what it has. */
  readonly computeMarginSeconds: number;
  /** Below this number of offers from the main sources, the complementary sources (Indeed) are started too. */
  readonly thinMarketOffers: number;
}

/** Default settings. */
export const DEFAULT_PIPELINE_SETTINGS: PipelineSettings = {
  collectionPollSeconds: 5,
  snapshotWaitSeconds: 2,
  offersPerSource: 60,
  computeMarginSeconds: 15,
  thinMarketOffers: 25,
};

/** Stages of an analysis job, in order. */
type Stage = "read_offer" | "market" | "market_wait" | "social" | "compute" | "wait_snapshot" | "finalize";

/** Departments used when neither the offer nor the user gives one. */
const FALLBACK_DEPARTMENT = "75";
const FALLBACK_CITY = "Paris";

/** A collecting snapshot or slice older than this is considered abandoned. */
const ABANDONED_AFTER_MILLISECONDS = 10 * 60 * 1000;

function formatNumber(value: number, language: "fr" | "en"): string {
  return value.toLocaleString(language === "fr" ? "fr-FR" : "en-US");
}

/**
 * Pipeline of an analysis, run by the worker stage after stage:
 * read_offer, then the 7 day cache (finalize at once, or wait_snapshot when another analysis computes the same
 * offer), then market (market slice of 7 days: reuse or start the collections), market_wait (poll Apify runs),
 * social (social search and AI summary), compute (score, salary, trend, twins), finalize.
 */
export class AnalysisPipeline implements JobHandler {
  private readonly reputations: ReputationService;

  /**
   * @param database database.
   * @param providers outside services.
   * @param spendGuard daily spending limit.
   * @param subscriptions quota service (refunds).
   * @param configuration configuration.
   * @param clock clock.
   * @param logger structured logger.
   * @param settings pipeline settings.
   */
  constructor(
    private readonly database: Database,
    private readonly providers: Providers,
    private readonly spendGuard: SpendGuard,
    private readonly subscriptions: SubscriptionService,
    private readonly configuration: Configuration,
    private readonly clock: Clock,
    private readonly logger: Logger,
    private readonly settings: PipelineSettings = DEFAULT_PIPELINE_SETTINGS,
  ) {
    this.reputations = new ReputationService(
      database,
      providers,
      spendGuard,
      clock,
      logger,
      configuration.reputationCacheDays,
    );
  }

  /** @inheritdoc */
  async runStage(job: Job): Promise<StageOutcome> {
    const analysis = job.analysisId ? await findAnalysis(this.database, job.analysisId, null) : null;
    if (!analysis || analysis.status !== "processing") return { kind: "done" };
    switch (job.stage as Stage | "start") {
      case "start":
      case "read_offer":
        return await this.readOffer(job, analysis);
      case "market":
        return await this.market(job, analysis);
      case "market_wait":
        return await this.marketWait(job, analysis);
      case "social":
        return await this.social(job, analysis);
      case "compute":
        return await this.compute(job, analysis);
      case "wait_snapshot":
        return await this.waitSnapshot(job, analysis);
      case "finalize":
        return await this.finalize(analysis);
      default:
        return { kind: "failed", reason: `unknown_stage:${job.stage}` };
    }
  }

  /** @inheritdoc */
  async giveUp(job: Job, reason: string): Promise<void> {
    if (job.payload.ownsSnapshot === true && typeof job.payload.snapshotId === "string") {
      await this.database.query(
        `update public.offer_snapshots set status = 'failed' where id = $1::uuid and status = 'collecting'`,
        [job.payload.snapshotId],
      );
    }
    if (job.payload.ownsSlice === true && typeof job.payload.sliceId === "string") {
      await this.database.query(
        `update public.market_slices set status = 'failed' where id = $1::uuid and status = 'collecting'`,
        [job.payload.sliceId],
      );
    }
    const analysis = job.analysisId ? await findAnalysis(this.database, job.analysisId, null) : null;
    if (analysis && analysis.status === "processing") await this.fail(analysis, "internal", null, reason);
  }

  // Stage: read the offer ------------------------------------------------------------------------------------------

  private async readOffer(job: Job, analysis: AnalysisRecord): Promise<StageOutcome> {
    const today = utcDay(this.clock.now());
    let key: OfferKey;
    let extracted: ExtractedOffer | null = null;
    let sourceSite: string | null = null;
    let canonicalUrl: string | null = null;

    if (analysis.inputKind === "link") {
      const link = canonicalizeLink(analysis.inputUrl ?? "");
      if (!link) return await this.fail(analysis, "unsupported_link", null, "invalid_link");
      key = await linkOfferKey(link);
      sourceSite = link.site;
      canonicalUrl = link.url;
      const known = await this.knownOffer(key);
      if (known) {
        extracted = known;
      } else {
        const outcome = await this.readWithBudget(
          "read_link",
          this.providers.offerReader.name,
          this.providers.offerReader.estimateCostMicroUsd(),
          () =>
            this.providers.offerReader.readLink(
              link.url,
              link.site,
              today,
              (estimate) => this.spendGuard.allows(estimate),
            ),
        );
        if (outcome.kind === "unsupported") return await this.fail(analysis, "unsupported_link", null, "unsupported");
        if (outcome.kind === "unreadable") {
          return await this.fail(analysis, "unreadable_offer", outcome.recognizedCompany, "unreadable");
        }
        extracted = outcome.offer;
      }
    } else if (analysis.inputKind === "manual") {
      const city = findCity(analysis.manualCity, null);
      extracted = {
        jobTitle: analysis.manualJobTitle ?? "",
        company: null,
        city: analysis.manualCity,
        departmentCode: city?.departmentCode ?? analysis.areaDepartmentCode,
        contractType: null,
        workMode: null,
        publishedOn: null,
        displayedSalary: null,
        skills: [],
        displayedApplicantCount: null,
      };
      key = await manualOfferKey(extracted.jobTitle, extracted.city ?? "");
    } else {
      const input = await queryOne<{ image: Uint8Array | null; mime_type: string | null; text_content: string | null }>(
        this.database,
        `select image, mime_type, text_content from public.analysis_inputs where analysis_id = $1::uuid`,
        [analysis.id],
      );
      if (!input) return await this.fail(analysis, "unreadable_offer", null, "input_expired");
      const operation = input.image ? "extract_image" : "extract_text";
      const outcome = await this.readWithBudget(
        operation,
        this.providers.ai.name,
        this.providers.ai.estimateCostMicroUsd(operation),
        () =>
          input.image
            ? this.providers.ai.extractOfferFromImage(
              new Uint8Array(input.image),
              input.mime_type ?? "image/jpeg",
              today,
            )
            : this.providers.offerReader.readText
            ? this.providers.offerReader.readText(input.text_content ?? "", today)
            : this.providers.ai.extractOfferFromText(input.text_content ?? "", today),
      );
      // The image or the text is not needed any more: deleted at once (24 hours at most otherwise).
      await this.database.query(`delete from public.analysis_inputs where analysis_id = $1::uuid`, [analysis.id]);
      if (outcome.kind !== "extracted") {
        return await this.fail(
          analysis,
          "unreadable_offer",
          outcome.kind === "unreadable" ? outcome.recognizedCompany : null,
          "unreadable",
        );
      }
      extracted = outcome.offer;
      if (!extracted.city && analysis.areaCity) extracted = { ...extracted, city: analysis.areaCity };
      if (!extracted.city) return await this.fail(analysis, "unreadable_offer", extracted.company, "no_city");
      key = await textOfferKey(extracted.jobTitle, extracted.company, extracted.city);
    }

    const department = extracted.departmentCode ?? findCity(extracted.city)?.departmentCode ??
      analysis.areaDepartmentCode ?? FALLBACK_DEPARTMENT;
    const offerId = await this.upsertOffer(key, { ...extracted, departmentCode: department }, sourceSite, canonicalUrl);
    const publishedDaysAgo = extracted.publishedOn ? Math.max(0, daysBetweenDates(extracted.publishedOn, today)) : null;
    const steps = this.updateSteps(analysis, {
      position_and_place: { status: "done", detail: [extracted.jobTitle, extracted.city].filter(Boolean).join(" · ") },
      skills: {
        status: "done",
        detail: extracted.skills.length > 0
          ? `${extracted.skills.length} ${analysis.language === "fr" ? "compétences" : "skills"}`
          : null,
      },
      comparison: { status: "running", detail: null },
    });
    await this.database.query(
      `update public.analyses
       set offer_id = $2::uuid, published_days_ago = $3, has_displayed_salary = $4, steps = $5::jsonb,
           progress_percent = greatest(progress_percent, 30), updated_at = now()
       where id = $1::uuid`,
      [analysis.id, offerId, publishedDaysAgo, extracted.displayedSalary !== null, JSON.stringify(steps)],
    );

    // 7 day cache of the offer.
    const ready = await queryOne<{ id: string }>(
      this.database,
      `select id::text as id from public.offer_snapshots
       where offer_id = $1::uuid and status = 'ready' and expires_at > $2::timestamptz
       order by expires_at desc limit 1`,
      [offerId, this.clock.now().toISOString()],
    );
    if (ready) {
      await this.database.query(
        `update public.analyses set snapshot_id = $2::uuid, from_cache = true where id = $1::uuid`,
        [analysis.id, ready.id],
      );
      return { kind: "next", stage: "finalize", payload: { ...job.payload, snapshotId: ready.id } };
    }
    await this.database.query(
      `update public.offer_snapshots set status = 'failed'
       where offer_id = $1::uuid and status = 'collecting' and created_at < $2::timestamptz`,
      [offerId, new Date(this.clock.now().getTime() - ABANDONED_AFTER_MILLISECONDS).toISOString()],
    );
    const created = await this.database.query<{ id: string }>(
      `insert into public.offer_snapshots (offer_id, status) values ($1::uuid, 'collecting')
       on conflict do nothing returning id::text as id`,
      [offerId],
    );
    if (created.length === 0) {
      const running = await queryOne<{ id: string }>(
        this.database,
        `select id::text as id from public.offer_snapshots where offer_id = $1::uuid and status = 'collecting'`,
        [offerId],
      );
      if (!running) throw new Error("snapshot_race");
      await this.database.query(`update public.analyses set snapshot_id = $2::uuid where id = $1::uuid`, [
        analysis.id,
        running.id,
      ]);
      return { kind: "next", stage: "wait_snapshot", payload: { ...job.payload, snapshotId: running.id } };
    }
    await this.database.query(`update public.analyses set snapshot_id = $2::uuid where id = $1::uuid`, [
      analysis.id,
      created[0].id,
    ]);
    return {
      kind: "next",
      stage: "market",
      payload: { ...job.payload, snapshotId: created[0].id, ownsSnapshot: true },
    };
  }

  private async readWithBudget(
    operation: string,
    provider: string,
    estimate: number,
    read: () => Promise<ExtractionOutcome>,
  ): Promise<ExtractionOutcome> {
    if (!(await this.spendGuard.allows(estimate))) throw new ProviderError(provider, "daily_limit", true);
    let outcome: ExtractionOutcome;
    try {
      outcome = await read();
    } catch (error) {
      // A reader that is not connected cannot read: the user is offered the other ways (screenshot, manual entry).
      if (error instanceof ProviderError && !error.retryable) return { kind: "unsupported", costMicroUsd: 0 };
      throw error;
    }
    await this.spendGuard.record({ provider, operation, costMicroUsd: outcome.costMicroUsd, usage: outcome.usage });
    return outcome;
  }

  /** An offer read recently (within the cache days) is not read again. */
  private async knownOffer(key: OfferKey): Promise<ExtractedOffer | null> {
    const row = await queryOne<Record<string, unknown>>(
      this.database,
      `select job_title, company, city, department_code, contract_type, work_mode, published_on::text as published_on,
              displayed_salary_minimum, displayed_salary_maximum, displayed_applicant_count, skills
       from public.offers
       where normalized_key = $1 and job_title is not null and updated_at > $2::timestamptz`,
      [key.value, new Date(this.clock.now().getTime() - this.configuration.cacheDays * 86_400_000).toISOString()],
    );
    if (!row) return null;
    return {
      jobTitle: String(row.job_title),
      company: (row.company as string | null) ?? null,
      city: (row.city as string | null) ?? null,
      departmentCode: (row.department_code as string | null) ?? null,
      contractType: (row.contract_type as ExtractedOffer["contractType"]) ?? null,
      workMode: (row.work_mode as string | null) ?? null,
      publishedOn: (row.published_on as string | null) ?? null,
      displayedSalary: row.displayed_salary_minimum !== null && row.displayed_salary_maximum !== null
        ? { minimum: Number(row.displayed_salary_minimum), maximum: Number(row.displayed_salary_maximum) }
        : null,
      skills: (row.skills as string[] | null) ?? [],
      displayedApplicantCount: (row.displayed_applicant_count as number | null) ?? null,
    };
  }

  private async upsertOffer(
    key: OfferKey,
    offer: ExtractedOffer,
    site: string | null,
    url: string | null,
  ): Promise<string> {
    const salary =
      offer.displayedSalary && offer.displayedSalary.minimum >= 1 && offer.displayedSalary.maximum <= 1000 &&
        offer.displayedSalary.minimum <= offer.displayedSalary.maximum
        ? offer.displayedSalary
        : null;
    const rows = await this.database.query<{ id: string }>(
      `insert into public.offers (normalized_key, key_kind, source_site, canonical_url, job_title, job_family, company, city,
                                  department_code, contract_type, work_mode, published_on, displayed_salary_minimum,
                                  displayed_salary_maximum, displayed_applicant_count, skills)
       values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12::date, $13, $14, $15,
               array(select jsonb_array_elements_text($16::jsonb)))
       on conflict (normalized_key) do update set
         job_title = excluded.job_title, job_family = excluded.job_family, company = excluded.company,
         city = excluded.city, department_code = excluded.department_code, contract_type = excluded.contract_type,
         work_mode = excluded.work_mode, published_on = coalesce(excluded.published_on, offers.published_on),
         displayed_salary_minimum = excluded.displayed_salary_minimum,
         displayed_salary_maximum = excluded.displayed_salary_maximum,
         displayed_applicant_count = excluded.displayed_applicant_count, skills = excluded.skills, updated_at = now()
       returning id::text as id`,
      [
        key.value,
        key.kind,
        site,
        url,
        offer.jobTitle.slice(0, 200),
        jobFamily(offer.jobTitle),
        offer.company?.slice(0, 200) ?? null,
        offer.city?.slice(0, 120) ?? null,
        offer.departmentCode,
        offer.contractType,
        offer.workMode?.slice(0, 80) ?? null,
        offer.publishedOn && /^\d{4}-\d{2}-\d{2}$/.test(offer.publishedOn) ? offer.publishedOn : null,
        salary?.minimum ?? null,
        salary?.maximum ?? null,
        offer.displayedApplicantCount,
        JSON.stringify(offer.skills.slice(0, 40).map((skill) => skill.slice(0, 80))),
      ],
    );
    return rows[0].id;
  }

  // Stage: market slice -------------------------------------------------------------------------------------------

  private async offerOf(analysis: AnalysisRecord): Promise<Record<string, unknown>> {
    const offer = await queryOne<Record<string, unknown>>(
      this.database,
      `select id::text as id, job_title, job_family, company, city, department_code, contract_type, work_mode,
              published_on::text as published_on, displayed_salary_minimum, displayed_salary_maximum,
              displayed_applicant_count, skills, canonical_url
       from public.offers where id = $1::uuid`,
      [analysis.offerId],
    );
    if (!offer) throw new Error("offer_missing");
    return offer;
  }

  private nearDeadline(job: Job): boolean {
    if (!job.deadlineAt) return false;
    return Date.parse(job.deadlineAt) - this.clock.now().getTime() <= this.settings.computeMarginSeconds * 1000;
  }

  private async market(job: Job, analysis: AnalysisRecord): Promise<StageOutcome> {
    const offer = await this.offerOf(analysis);
    const family = String(offer.job_family);
    const department = String(offer.department_code ?? FALLBACK_DEPARTMENT);
    const now = this.clock.now();
    const ready = await queryOne<{ id: string }>(
      this.database,
      `select id::text as id from public.market_slices
       where job_family = $1 and department_code = $2 and status = 'ready' and expires_at > $3::timestamptz
       order by expires_at desc limit 1`,
      [family, department, now.toISOString()],
    );
    if (ready) return { kind: "next", stage: "social", payload: { ...job.payload, sliceId: ready.id } };

    await this.database.query(
      `update public.market_slices set status = 'failed'
       where job_family = $1 and department_code = $2 and status = 'collecting' and created_at < $3::timestamptz`,
      [family, department, new Date(now.getTime() - ABANDONED_AFTER_MILLISECONDS).toISOString()],
    );
    const created = await this.database.query<{ id: string }>(
      `insert into public.market_slices (job_family, department_code, status) values ($1, $2, 'collecting')
       on conflict do nothing returning id::text as id`,
      [family, department],
    );
    if (created.length === 0) {
      // Another analysis collects this market right now: wait for it, or compute with the data at hand.
      if (this.nearDeadline(job)) return { kind: "next", stage: "social", payload: { ...job.payload, partial: true } };
      return { kind: "wait", stage: "market", seconds: this.settings.collectionPollSeconds };
    }
    const sliceId = created[0].id;
    const city = findCity(String(offer.city ?? ""), department)?.name ?? (offer.city as string | null) ??
      analysis.areaCity ?? FALLBACK_CITY;
    const request: CollectionRequest = {
      jobTitle: String(offer.job_title),
      jobFamily: family,
      city,
      departmentCode: department,
      radiusKilometers: Math.max(analysis.areaRadiusKilometers ?? DEFAULT_RADIUS_KILOMETERS, 30),
      maximumOffers: this.settings.offersPerSource,
      today: utcDay(now),
    };
    let partial = false;
    for (const source of this.providers.offerSources.filter((candidate) => !candidate.complementary)) {
      const started = await this.startCollection(sliceId, source, request);
      if (!started) partial = true;
    }
    await this.setProgress(analysis, 45, { comparison: { status: "running", detail: null } });
    return {
      kind: "next",
      stage: "market_wait",
      payload: { ...job.payload, sliceId, ownsSlice: true, partial, collectionRequest: request },
    };
  }

  /** Starts one source. Returns false when the source was skipped (daily limit) or failed to start. */
  private async startCollection(sliceId: string, source: OfferSource, request: CollectionRequest): Promise<boolean> {
    const estimate = source.estimateCostMicroUsd(request);
    if (source.paid && !(await this.spendGuard.allows(estimate))) {
      await this.saveCollection(sliceId, source.name, { status: "skipped", errorCode: "daily_limit" });
      this.logger.log("warning", "source_skipped", { source: source.name, reason: "daily_limit" });
      return false;
    }
    try {
      const start = await source.start(request);
      if (start.kind === "finished") {
        await this.storeOffers(request.jobFamily, request.departmentCode, start.offers);
        await this.spendGuard.record({
          provider: source.name,
          operation: "collect",
          costMicroUsd: start.costMicroUsd,
          units: start.offers.length,
          sliceId,
        });
        await this.saveCollection(sliceId, source.name, {
          status: "succeeded",
          itemCount: start.offers.length,
          costMicroUsd: start.costMicroUsd,
        });
      } else {
        // The estimate is reserved until the run ends, so that parallel runs cannot overshoot the daily limit.
        await this.saveCollection(sliceId, source.name, {
          status: "running",
          runId: start.runId,
          reservedMicroUsd: source.paid ? estimate : 0,
        });
      }
      return true;
    } catch (error) {
      const code = error instanceof ProviderError ? error.code : "start_failed";
      await this.saveCollection(sliceId, source.name, { status: "failed", errorCode: code });
      this.logger.log("warning", "source_failed", { source: source.name, error: code });
      return false;
    }
  }

  private async saveCollection(
    sliceId: string,
    provider: string,
    values: {
      status: string;
      runId?: string;
      itemCount?: number;
      costMicroUsd?: number;
      errorCode?: string;
      reservedMicroUsd?: number;
    },
  ): Promise<void> {
    await this.database.query(
      `insert into public.source_collections (slice_id, provider, status, external_run_id, item_count, cost_micro_usd,
                                              error_code, attempts, started_at, finished_at, reserved_micro_usd)
       values ($1::uuid, $2, $3, $4, $5, $6, $7, 1, now(), case when $3 in ('succeeded', 'failed', 'skipped') then now() end,
               case when $3 = 'running' then $8 else 0 end)
       on conflict (slice_id, provider) do update set
         status = excluded.status, external_run_id = coalesce(excluded.external_run_id, source_collections.external_run_id),
         item_count = excluded.item_count, cost_micro_usd = source_collections.cost_micro_usd + excluded.cost_micro_usd,
         error_code = excluded.error_code, attempts = source_collections.attempts + 1, finished_at = excluded.finished_at,
         reserved_micro_usd = excluded.reserved_micro_usd`,
      [
        sliceId,
        provider,
        values.status,
        values.runId ?? null,
        values.itemCount ?? 0,
        Math.round(values.costMicroUsd ?? 0),
        values.errorCode ?? null,
        Math.round(values.reservedMicroUsd ?? 0),
      ],
    );
  }

  private async storeOffers(
    family: string,
    searchDepartment: string,
    offers: readonly CollectedOfferInput[],
  ): Promise<void> {
    const observedAt = this.clock.now().toISOString();
    const rows = offers.slice(0, 500).flatMap((offer) => {
      const title = offer.jobTitle?.trim().slice(0, 200);
      const externalId = offer.externalId?.trim().slice(0, 300);
      if (!title || !externalId) return [];
      const department = offer.departmentCode && /^([0-9]{2}|2[AB]|9[78][0-9])$/.test(offer.departmentCode)
        ? offer.departmentCode
        : findCity(offer.city)?.departmentCode ?? searchDepartment;
      const city = findCity(offer.city, department) ?? findCity(offer.city);
      const salary = offer.salary && offer.salary.minimum >= 1 && offer.salary.maximum <= 1000 &&
          offer.salary.minimum <= offer.salary.maximum
        ? offer.salary
        : null;
      return [{
        source: offer.source.slice(0, 40),
        external_id: externalId,
        job_title: title,
        // The family of the search, not of the title: the sources answer with close titles of the same job.
        job_family: family,
        company: offer.company?.slice(0, 200) ?? null,
        city: offer.city?.slice(0, 120) ?? null,
        department_code: department,
        latitude: city?.latitude ?? null,
        longitude: city?.longitude ?? null,
        published_on: offer.publishedOn && /^\d{4}-\d{2}-\d{2}$/.test(offer.publishedOn) ? offer.publishedOn : null,
        salary_minimum: salary?.minimum ?? null,
        salary_maximum: salary?.maximum ?? null,
        applicant_count: offer.applicantCount !== null && offer.applicantCount >= 0
          ? Math.round(offer.applicantCount)
          : null,
        applicant_count_observed_at: offer.applicantCount !== null ? observedAt : null,
        contract_type: offer.contractType?.slice(0, 40) ?? null,
        work_mode: offer.workMode?.slice(0, 80) ?? null,
        skills: offer.skills.slice(0, 40).map((skill) => skill.slice(0, 80)),
        url: offer.url && offer.url.length <= 2048 && /^https?:\/\//.test(offer.url) ? offer.url : null,
      }];
    });
    if (rows.length === 0) return;
    await this.database.query(
      `insert into public.collected_offers (source, external_id, job_title, job_family, company, city, department_code,
         latitude, longitude, published_on, salary_minimum, salary_maximum, applicant_count, applicant_count_observed_at,
         contract_type, work_mode, skills, url)
       select source, external_id, job_title, job_family, company, city, department_code, latitude, longitude,
              published_on::date, salary_minimum, salary_maximum, applicant_count, applicant_count_observed_at::timestamptz,
              contract_type, work_mode, array(select jsonb_array_elements_text(skills)), url
       from jsonb_to_recordset($1::jsonb) as item(source text, external_id text, job_title text, job_family text,
         company text, city text, department_code text, latitude double precision, longitude double precision,
         published_on text, salary_minimum integer, salary_maximum integer, applicant_count integer,
         applicant_count_observed_at text, contract_type text, work_mode text, skills jsonb, url text)
       on conflict (source, external_id) do update set
         job_title = excluded.job_title, job_family = excluded.job_family, company = excluded.company,
         city = excluded.city, department_code = excluded.department_code, latitude = excluded.latitude,
         longitude = excluded.longitude, published_on = coalesce(excluded.published_on, collected_offers.published_on),
         salary_minimum = excluded.salary_minimum, salary_maximum = excluded.salary_maximum,
         applicant_count = coalesce(excluded.applicant_count, collected_offers.applicant_count),
         applicant_count_observed_at = coalesce(excluded.applicant_count_observed_at, collected_offers.applicant_count_observed_at),
         contract_type = excluded.contract_type, work_mode = excluded.work_mode, skills = excluded.skills,
         url = excluded.url, last_seen_at = now()`,
      [JSON.stringify(rows)],
    );
  }

  private async marketWait(job: Job, analysis: AnalysisRecord): Promise<StageOutcome> {
    const sliceId = String(job.payload.sliceId);
    const request = job.payload.collectionRequest as CollectionRequest;
    const polled = await this.pollRunningCollections(sliceId, request.jobFamily, request.departmentCode);
    let partial = job.payload.partial === true || polled.failed;
    const stillRunning = polled.stillRunning;

    // Thin market: the complementary sources (Indeed) are started once the main ones are done.
    const complements = this.providers.offerSources.filter((source) => source.complementary);
    if (
      stillRunning === 0 && complements.length > 0 && job.payload.complementsStarted !== true && !this.nearDeadline(job)
    ) {
      const found = await queryOne<{ count: number }>(
        this.database,
        `select coalesce(sum(item_count), 0)::integer as count from public.source_collections
         where slice_id = $1::uuid and status = 'succeeded'`,
        [sliceId],
      );
      if ((found?.count ?? 0) < this.settings.thinMarketOffers) {
        for (const source of complements) {
          if (!(await this.startCollection(sliceId, source, request))) partial = true;
        }
        this.logger.log("info", "complementary_sources_started", { offers: found?.count ?? 0 });
        return { kind: "next", stage: "market_wait", payload: { ...job.payload, partial, complementsStarted: true } };
      }
    }

    if (stillRunning > 0 && !this.nearDeadline(job)) {
      const total = Math.max(1, this.providers.offerSources.length);
      await this.setProgress(analysis, 45 + Math.round((25 * (total - stillRunning)) / total), {});
      return {
        kind: "wait",
        stage: "market_wait",
        seconds: this.settings.collectionPollSeconds,
        payload: { ...job.payload, partial },
      };
    }
    // Runs still going on are not lost: the Apify webhook or the maintenance stores their results later.
    if (stillRunning > 0) partial = true;
    // A partial slice is kept one day only, so that the market is collected again soon.
    const lifetimeDays = partial ? 1 : this.configuration.cacheDays;
    await this.database.query(
      `update public.market_slices set status = 'ready', collected_at = $2::timestamptz, expires_at = $3::timestamptz
       where id = $1::uuid`,
      [
        sliceId,
        this.clock.now().toISOString(),
        new Date(this.clock.now().getTime() + lifetimeDays * 86_400_000).toISOString(),
      ],
    );
    return { kind: "next", stage: "social", payload: { ...job.payload, partial } };
  }

  /** Polls the running collections of a slice once; stores the finished ones. */
  private async pollRunningCollections(
    sliceId: string,
    family: string,
    department: string,
    onlyRunId: string | null = null,
  ): Promise<{ stillRunning: number; failed: boolean; startedAt: Map<string, string> }> {
    const running = await this.database.query<{ provider: string; external_run_id: string; started_at: unknown }>(
      `select provider, external_run_id, started_at from public.source_collections
       where slice_id = $1::uuid and status = 'running' and ($2::text is null or external_run_id = $2)`,
      [sliceId, onlyRunId],
    );
    let stillRunning = 0;
    let failed = false;
    const startedAt = new Map<string, string>();
    for (const collection of running) {
      const source = this.providers.offerSources.find((candidate) => candidate.name === collection.provider);
      if (!source) {
        await this.completeCollection(sliceId, collection.provider, { status: "failed", errorCode: "unknown_source" });
        failed = true;
        continue;
      }
      const poll = await source.poll(collection.external_run_id);
      if (poll.kind === "running") {
        stillRunning += 1;
        startedAt.set(collection.provider, toIsoTimestamp(collection.started_at));
        continue;
      }
      // Only one worker (the pipeline, the webhook or the maintenance) stores a finished run.
      const claimed = await this.completeCollection(sliceId, source.name, {
        status: poll.kind === "finished" ? "succeeded" : "failed",
        itemCount: poll.kind === "finished" ? poll.offers.length : 0,
        costMicroUsd: poll.costMicroUsd,
        errorCode: poll.kind === "failed" ? poll.code.slice(0, 60) : null,
      });
      if (!claimed) continue;
      await this.spendGuard.record({
        provider: source.name,
        operation: poll.kind === "finished" ? "collect" : "collect_failed",
        costMicroUsd: poll.costMicroUsd,
        units: poll.kind === "finished" ? poll.offers.length : 1,
        sliceId,
      });
      if (poll.kind === "finished") {
        await this.storeOffers(family, department, poll.offers);
      } else {
        failed = true;
      }
    }
    return { stillRunning, failed, startedAt };
  }

  /** Ends a running collection. Returns false when another worker already ended it. */
  private async completeCollection(
    sliceId: string,
    provider: string,
    values: { status: "succeeded" | "failed"; itemCount?: number; costMicroUsd?: number; errorCode?: string | null },
  ): Promise<boolean> {
    const updated = await this.database.query(
      `update public.source_collections
       set status = $3, item_count = $4, cost_micro_usd = cost_micro_usd + $5, error_code = $6, finished_at = now(),
           reserved_micro_usd = 0
       where slice_id = $1::uuid and provider = $2 and status = 'running'
       returning id`,
      [
        sliceId,
        provider,
        values.status,
        values.itemCount ?? 0,
        Math.round(values.costMicroUsd ?? 0),
        values.errorCode ?? null,
      ],
    );
    return updated.length > 0;
  }

  /**
   * Wakes the analyses waiting for an Apify run (called by the Apify webhook at the end of the run).
   *
   * @param runId identifier of the run.
   * @returns number of analyses woken.
   */
  async wakeForRun(runId: string): Promise<number> {
    const woken = await this.database.query(
      `update public.jobs as job set run_after = now(), updated_at = now()
       from public.source_collections as collection
       where collection.external_run_id = $1 and collection.status = 'running'
         and job.status = 'queued' and job.stage = 'market_wait'
         and job.payload->>'sliceId' = collection.slice_id::text
       returning job.id`,
      [runId],
    );
    return woken.length;
  }

  /**
   * Stores the runs that no analysis waits for any more (an analysis stops waiting near its deadline): their results
   * are paid, so they serve the next analyses of the market. Runs older than an hour that never ended are released.
   * Called by the Apify webhook (one run) and by the daily maintenance (all of them).
   *
   * @param runId one run, or null for every abandoned run.
   */
  async harvestCollections(runId: string | null = null): Promise<{ harvested: number; released: number }> {
    const slices = await this.database.query<{ slice_id: string; job_family: string; department_code: string }>(
      `select distinct collection.slice_id::text as slice_id, slice.job_family, slice.department_code
       from public.source_collections as collection
       join public.market_slices as slice on slice.id = collection.slice_id
       where collection.status = 'running' and slice.status <> 'collecting'
         and ($1::text is null or collection.external_run_id = $1)
       limit 50`,
      [runId],
    );
    let harvested = 0;
    let released = 0;
    for (const slice of slices) {
      const before = await queryOne<{ count: number }>(
        this.database,
        `select count(*)::integer as count from public.source_collections where slice_id = $1::uuid and status = 'running'
           and ($2::text is null or external_run_id = $2)`,
        [slice.slice_id, runId],
      );
      const polled = await this.pollRunningCollections(slice.slice_id, slice.job_family, slice.department_code, runId);
      harvested += (before?.count ?? 0) - polled.stillRunning;
      for (const [provider, startedAt] of polled.startedAt) {
        if (this.clock.now().getTime() - Date.parse(startedAt) > 3_600_000) {
          if (await this.completeCollection(slice.slice_id, provider, { status: "failed", errorCode: "abandoned" })) {
            released += 1;
          }
        }
      }
    }
    if (harvested > 0 || released > 0) this.logger.log("info", "collections_harvested", { harvested, released });
    return { harvested, released };
  }

  // Stage: social posts and AI summary ------------------------------------------------------------------------------

  private async social(job: Job, analysis: AnalysisRecord): Promise<StageOutcome> {
    const offer = await this.offerOf(analysis);
    try {
      // Fills the 30 day cache of the company (search and AI summary); finalize reads it in the user's language.
      await this.reputations.reputationFor(
        {
          company: (offer.company as string | null) ?? null,
          jobTitle: String(offer.job_title),
          city: (offer.city as string | null) ?? null,
        },
        analysis.language,
        String(job.payload.snapshotId),
      );
    } catch (error) {
      // The reputation is a bonus: without it the analysis goes on.
      const code = error instanceof ProviderError ? `${error.provider}:${error.code}` : "social_failed";
      this.logger.log("warning", "reputation_skipped", { error: code });
    }
    await this.setProgress(analysis, 80, {});
    return { kind: "next", stage: "compute", payload: job.payload };
  }

  // Stage: computation ---------------------------------------------------------------------------------------------

  private async compute(job: Job, analysis: AnalysisRecord): Promise<StageOutcome> {
    const offer = await this.offerOf(analysis);
    const today = utcDay(this.clock.now());
    const family = String(offer.job_family);
    const department = String(offer.department_code ?? FALLBACK_DEPARTMENT);
    const comparables = await loadComparables(this.database, family, department, today);
    const history = await loadHistory(this.database, family, department, today);
    const displayedSalary = offer.displayed_salary_minimum !== null && offer.displayed_salary_maximum !== null
      ? { minimum: Number(offer.displayed_salary_minimum), maximum: Number(offer.displayed_salary_maximum) }
      : null;
    const partial = job.payload.partial === true;
    const estimate = estimateCompetition({
      target: {
        publishedOn: (offer.published_on as string | null) ?? null,
        displayedSalary,
        contractType: (offer.contract_type as string | null) ?? null,
        workMode: (offer.work_mode as string | null) ?? null,
        displayedApplicantCount: (offer.displayed_applicant_count as number | null) ?? null,
      },
      comparables,
      history,
      today,
      partialSources: partial,
    });
    const scale = salaryScale(comparables, history);
    const estimatedSalary = probableSalary(displayedSalary, comparables);
    const trend = marketTrend(comparables, history, today);
    const regionName = departmentName(department) ?? department;
    const result = {
      score: estimate.score,
      percentileInRegion: estimate.percentileInRegion,
      regionName,
      applicants: { estimate: estimate.estimate, low: estimate.low, high: estimate.high },
      reliability: estimate.reliability,
      estimatedSalary,
      trendThreeMonthsPercent: trend.changeThreeMonthsPercent,
      comparableOfferCount: estimate.comparableOfferCount,
      timingAdvice: timingAdvice((offer.published_on as string | null) ?? null, today),
      internal: {
        reasons: estimate.reasons,
        finalApplicants: Math.round(estimate.finalApplicants),
        marketLevel: Math.round(estimate.marketLevel),
        anchorCount: estimate.anchorCount,
        partial,
        jobFamily: family,
        departmentCode: department,
        computedOn: today,
      },
    };
    const insights = {
      salaryScale: scale,
      salarySourceCount: comparables.filter((entry) => entry.salary !== null).length,
      skills: skillDemand(comparables),
      trend,
    };
    const twins = estimateTwins({
      comparables,
      history,
      today,
      targetScore: estimate.score,
      excludedExternalUrl: (offer.canonical_url as string | null) ?? null,
    });
    const snapshotId = String(job.payload.snapshotId);
    const lifetimeDays = partial ? 1 : this.configuration.cacheDays;
    await this.database.transaction(async (transaction) => {
      await transaction.query(
        `update public.offer_snapshots
         set status = 'ready', degraded = $2, result = $3::jsonb, insights = $4::jsonb, comparable_offer_count = $5,
             salary_source_count = $6, computed_at = $7::timestamptz, expires_at = $8::timestamptz
         where id = $1::uuid`,
        [
          snapshotId,
          partial,
          JSON.stringify(result),
          JSON.stringify(insights),
          estimate.comparableOfferCount,
          insights.salarySourceCount,
          this.clock.now().toISOString(),
          new Date(this.clock.now().getTime() + lifetimeDays * 86_400_000).toISOString(),
        ],
      );
      if (twins.length > 0) {
        await transaction.query(
          `insert into public.snapshot_twins (snapshot_id, collected_offer_id, estimated_applicants, score)
           select $1::uuid, (twin->>'id')::uuid, (twin->>'estimatedApplicants')::integer, (twin->>'score')::integer
           from jsonb_array_elements($2::jsonb) as twin
           on conflict do nothing`,
          [
            snapshotId,
            JSON.stringify(
              twins.slice(0, 200).map((twin) => ({
                id: twin.offer.id,
                estimatedApplicants: twin.estimatedApplicants,
                score: twin.score,
              })),
            ),
          ],
        );
      }
      await upsertMarketStatistics(transaction, family, department, comparables, today);
    });
    await this.setProgress(analysis, 95, {
      comparison: {
        status: "done",
        detail: analysis.language === "fr"
          ? `${formatNumber(estimate.comparableOfferCount, "fr")} offres comparables, ${regionName}`
          : `${formatNumber(estimate.comparableOfferCount, "en")} comparable offers, ${regionName}`,
      },
    });
    return { kind: "next", stage: "finalize", payload: job.payload };
  }

  // Stage: wait for an analysis of the same offer --------------------------------------------------------------------

  private async waitSnapshot(job: Job, analysis: AnalysisRecord): Promise<StageOutcome> {
    const snapshot = await queryOne<{ status: string }>(
      this.database,
      `select status from public.offer_snapshots where id = $1::uuid`,
      [String(job.payload.snapshotId)],
    );
    if (snapshot?.status === "ready") return { kind: "next", stage: "finalize" };
    if (
      !snapshot || snapshot.status === "failed" ||
      (job.deadlineAt && Date.parse(job.deadlineAt) <= this.clock.now().getTime())
    ) {
      return await this.fail(analysis, "internal", null, "snapshot_failed");
    }
    await this.setProgress(analysis, Math.min(analysis.progressPercent + 10, 85), {
      comparison: { status: "running", detail: null },
    });
    return { kind: "wait", stage: "wait_snapshot", seconds: this.settings.snapshotWaitSeconds };
  }

  // Stage: result of the analysis -----------------------------------------------------------------------------------

  private async finalize(analysis: AnalysisRecord): Promise<StageOutcome> {
    const fresh = await findAnalysis(this.database, analysis.id, null);
    if (!fresh?.snapshotId || !fresh.offerId) throw new Error("snapshot_missing");
    const snapshot = await queryOne<{
      result: Record<string, unknown>;
      insights: { salaryScale: SalaryScale | null; skills: { name: string; sharePercent: number }[] } | null;
    }>(
      this.database,
      `select result, insights from public.offer_snapshots where id = $1::uuid and status = 'ready'`,
      [fresh.snapshotId],
    );
    if (!snapshot) throw new Error("snapshot_not_ready");
    const offer = await this.offerOf(fresh);
    const offerBody = {
      jobTitle: String(offer.job_title),
      company: (offer.company as string | null) ?? null,
      city: String(offer.city ?? fresh.areaCity ?? ""),
      contractType: (offer.contract_type as string | null) ?? "other",
      workMode: (offer.work_mode as string | null) ?? null,
      publishedOn: (offer.published_on as string | null) ?? null,
      displayedSalary: offer.displayed_salary_minimum !== null && offer.displayed_salary_maximum !== null
        ? { minimum: Number(offer.displayed_salary_minimum), maximum: Number(offer.displayed_salary_maximum) }
        : null,
      skills: (offer.skills as string[] | null) ?? [],
      sourceUrl: (offer.canonical_url as string | null) ?? fresh.inputUrl,
    };
    const radius = fresh.areaRadiusKilometers ?? DEFAULT_RADIUS_KILOMETERS;
    const center = findCity(fresh.areaCity, fresh.areaDepartmentCode) ?? findCity(String(offer.city ?? ""));
    const twinCount = await queryOne<{ count: number }>(
      this.database,
      `select count(*)::integer as count
       from public.snapshot_twins t join public.collected_offers c on c.id = t.collected_offer_id
       where t.snapshot_id = $1::uuid
         and ($2::float8 is null or (c.latitude is not null and
              6371 * 2 * asin(least(1, sqrt(power(sin(radians(c.latitude - $2::float8) / 2), 2) +
                cos(radians($2::float8)) * cos(radians(c.latitude)) * power(sin(radians(c.longitude - $3::float8) / 2), 2))))
              <= $4::float8))`,
      [fresh.snapshotId, center?.latitude ?? null, center?.longitude ?? null, radius],
    );
    const applicants = (snapshot.result.applicants as { estimate: number }) ?? { estimate: 0 };
    const computed = { ...snapshot.result, twinOfferCount: twinCount?.count ?? 0, twinRadiusKilometers: radius };
    const reputation = await this.reputationOf(offerBody, fresh.language, fresh.snapshotId);
    const advice = await this.adviceFor(
      fresh.language,
      offerBody,
      computed,
      snapshot.insights,
      reputation,
      fresh.snapshotId,
    );
    const result = { ...computed, reputation, advice };
    const steps = this.updateSteps(fresh, {
      position_and_place: { status: "done" },
      skills: { status: "done" },
      comparison: { status: "done" },
      applicants: {
        status: "done",
        detail: fresh.language === "fr"
          ? `≈ ${formatNumber(applicants.estimate, "fr")} candidats`
          : `≈ ${formatNumber(applicants.estimate, "en")} applicants`,
      },
    });
    const searchText = normalizeSearchText(
      [offerBody.jobTitle, offerBody.company, offerBody.city].filter(Boolean).join(" "),
    ).slice(0, 600);
    await this.database.transaction(async (transaction) => {
      await transaction.query(
        `insert into public.analysis_results (analysis_id, offer, result) values ($1::uuid, $2::jsonb, $3::jsonb)
         on conflict (analysis_id) do update set offer = excluded.offer, result = excluded.result`,
        [fresh.id, JSON.stringify(offerBody), JSON.stringify(result)],
      );
      await transaction.query(
        `update public.analyses
         set status = 'completed', progress_percent = 100, steps = $2::jsonb, search_text = $3, completed_at = now(),
             updated_at = now()
         where id = $1::uuid and status = 'processing'`,
        [fresh.id, JSON.stringify(steps), searchText],
      );
    });
    this.logger.log("info", "analysis_completed", {
      inputKind: fresh.inputKind,
      score: Number(snapshot.result.score ?? 0),
      durationMs: this.clock.now().getTime() - Date.parse(toIsoTimestamp(fresh.createdAt)),
    });
    return { kind: "done" };
  }

  /** Reputation of the company in the user's language, from the cache of 30 days filled by the social stage. */
  private async reputationOf(
    offer: { jobTitle: string; company: string | null; city: string },
    language: "fr" | "en",
    snapshotId: string,
  ): Promise<Reputation | null> {
    try {
      // Read only: the social stage of this offer searched within the last 7 days (the company is cached 30 days).
      return await this.reputations.reputationFor(offer, language, snapshotId, true);
    } catch (error) {
      const code = error instanceof ProviderError ? `${error.provider}:${error.code}` : "social_failed";
      this.logger.log("warning", "reputation_skipped", { error: code });
      return null;
    }
  }

  /**
   * Advice of the verdict written by the AI from the computed facts, in the user's language (per analysis: it
   * depends on the twins in the user's radius). Null when the AI is unavailable, over the daily limit, or when no
   * answer passes the checks: the screen then shows the computed timing advice.
   */
  private async adviceFor(
    language: "fr" | "en",
    offer: {
      jobTitle: string;
      company: string | null;
      city: string;
      contractType: string;
      workMode: string | null;
      publishedOn: string | null;
      displayedSalary: { minimum: number; maximum: number } | null;
      skills: string[];
    },
    result: Record<string, unknown>,
    insights: { salaryScale: SalaryScale | null; skills: { name: string; sharePercent: number }[] } | null,
    reputation: Reputation | null,
    snapshotId: string,
  ): Promise<Advice | null> {
    const ai = this.providers.ai;
    try {
      if (!(await this.spendGuard.allows(ai.estimateCostMicroUsd("advice")))) return null;
      const facts = buildAdviceFacts({
        language,
        offer: { ...offer, city: offer.city || null },
        result,
        insights,
        reputation,
        today: utcDay(this.clock.now()),
      });
      const written = await ai.writeAdvice(facts);
      await this.spendGuard.record({
        provider: ai.name,
        operation: "advice",
        costMicroUsd: written.costMicroUsd,
        snapshotId,
        usage: written.usage,
      });
      return written.advice;
    } catch (error) {
      const code = error instanceof ProviderError ? `${error.provider}:${error.code}` : "advice_failed";
      this.logger.log("warning", "advice_skipped", { error: code });
      return null;
    }
  }

  // Helpers ---------------------------------------------------------------------------------------------------------

  private updateSteps(
    analysis: AnalysisRecord,
    changes: Partial<Record<StepKind, { status: ReadingStep["status"]; detail?: string | null }>>,
  ): ReadingStep[] {
    return analysis.steps.map((step) => {
      const change = changes[step.kind];
      if (!change) return step;
      return {
        kind: step.kind,
        status: change.status,
        detail: change.detail === undefined ? step.detail : change.detail,
      };
    });
  }

  private async setProgress(
    analysis: AnalysisRecord,
    percent: number,
    changes: Partial<Record<StepKind, { status: ReadingStep["status"]; detail?: string | null }>>,
  ): Promise<void> {
    const fresh = await findAnalysis(this.database, analysis.id, null) ?? analysis;
    await this.database.query(
      `update public.analyses set progress_percent = greatest(progress_percent, $2), steps = $3::jsonb, updated_at = now()
       where id = $1::uuid and status = 'processing'`,
      [analysis.id, Math.min(percent, 99), JSON.stringify(this.updateSteps(fresh, changes))],
    );
  }

  /** Marks the analysis as failed with the code the user sees, and gives the free analysis back. */
  private async fail(
    analysis: AnalysisRecord,
    code: "unreadable_offer" | "unsupported_link" | "internal",
    recognizedCompany: string | null,
    reason: string,
  ): Promise<StageOutcome> {
    await this.database.transaction(async (transaction) => {
      const updated = await transaction.query(
        `update public.analyses
         set status = 'failed', failure_code = $2, recognized_company = $3, updated_at = now(), completed_at = now()
         where id = $1::uuid and status = 'processing' returning id`,
        [analysis.id, code, recognizedCompany?.slice(0, 200) ?? null],
      );
      if (updated.length > 0 && analysis.quotaWeek) {
        await this.subscriptions.refundFreeAnalysis(transaction, analysis.userId, analysis.quotaWeek);
      }
      await transaction.query(`delete from public.analysis_inputs where analysis_id = $1::uuid`, [analysis.id]);
    });
    this.logger.log("info", "analysis_failed", { code, reason });
    return { kind: "failed", reason };
  }
}
