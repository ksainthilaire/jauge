import type {
  CollectedOfferInput,
  CollectionPoll,
  CollectionRequest,
  CollectionStart,
  ExtractedOffer,
  OfferSource,
} from "../provider_types.ts";
import { ProviderError } from "../provider_types.ts";
import type { DatasetItem, MarketActor, OfferActor } from "./actor_catalog.ts";
import { type ApifyClient, type ApifyRun, isTerminal } from "./apify_client.ts";
import { estimateRunCostMicroUsd, type PricingTier, runCostMicroUsd } from "./apify_pricing.ts";

/** Settings shared by the Apify connectors. */
export interface ApifySourceSettings {
  /** Discount tier of the Apify account (Starter plan: BRONZE). */
  readonly tier: PricingTier;
  /** Webhook called by Apify at the end of each market run (wakes the waiting analysis), or null to poll only. */
  readonly webhook: { readonly url: string; readonly secret: string } | null;
  /** Date of today (ISO), for the relative dates of the results. */
  readonly today: () => string;
}

function isRecord(value: unknown): value is DatasetItem {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** Maps the items of a run, skipping unusable ones and repeated identifiers. */
export function mapItems(actor: MarketActor, items: readonly unknown[], today: string): CollectedOfferInput[] {
  const seen = new Set<string>();
  const offers: CollectedOfferInput[] = [];
  for (const item of items) {
    if (!isRecord(item)) continue;
    let offer: CollectedOfferInput | null;
    try {
      offer = actor.mapItem(item, { today });
    } catch {
      // A malformed item never breaks a whole collection.
      offer = null;
    }
    if (!offer || seen.has(offer.externalId)) continue;
    seen.add(offer.externalId);
    offers.push(offer);
  }
  return offers;
}

/**
 * A market source backed by an Apify actor. `start` launches an asynchronous run (never awaited: the work queue polls
 * it every few seconds, or the Apify webhook wakes it), capped by `maxItems` and `maxTotalChargeUsd` so that a run
 * can never cost more than its estimate. `poll` reads the run and, once it has ended, the items of its dataset.
 */
export class ApifyOfferSource implements OfferSource {
  readonly paid = true;
  readonly name: string;

  /**
   * @param actor actor of the source.
   * @param client Apify client.
   * @param settings tier, webhook, today.
   * @param complementary true for a source started only when the main sources found a thin market.
   */
  constructor(
    private readonly actor: MarketActor,
    private readonly client: ApifyClient,
    private readonly settings: ApifySourceSettings,
    readonly complementary = false,
  ) {
    this.name = actor.source;
  }

  private limit(request: CollectionRequest): number {
    return Math.max(1, Math.min(request.maximumOffers, this.actor.maximumOffers));
  }

  /** @inheritdoc */
  estimateCostMicroUsd(request: CollectionRequest): number {
    return estimateRunCostMicroUsd(
      this.actor.prices,
      this.settings.tier,
      this.limit(request),
      this.actor.memoryMegabytes,
    );
  }

  /** @inheritdoc */
  async start(request: CollectionRequest): Promise<CollectionStart> {
    const limit = this.limit(request);
    const run = await this.client.startRun(this.actor.actorId, this.actor.buildInput(request, limit), {
      maximumItems: limit,
      maximumTotalChargeUsd: this.estimateCostMicroUsd(request) / 1_000_000,
      timeoutSeconds: this.actor.timeoutSeconds,
      memoryMegabytes: this.actor.memoryMegabytes,
      webhook: this.settings.webhook,
    });
    return { kind: "started", runId: run.id };
  }

  /** @inheritdoc */
  async poll(runId: string): Promise<CollectionPoll> {
    let run: ApifyRun;
    try {
      run = await this.client.getRun(runId);
    } catch (error) {
      // A network hiccup is not the end of the run: the next poll tries again (the deadline still applies).
      if (error instanceof ProviderError && error.retryable) return { kind: "running" };
      return { kind: "failed", code: error instanceof ProviderError ? error.code : "poll_failed", costMicroUsd: 0 };
    }
    if (!isTerminal(run.status)) return { kind: "running" };
    let items: unknown[] = [];
    if (run.defaultDatasetId) {
      try {
        items = await this.client.getDatasetItems(run.defaultDatasetId, {
          limit: this.actor.maximumOffers,
          fields: this.actor.fields,
        });
      } catch (error) {
        if (error instanceof ProviderError && error.retryable) return { kind: "running" };
        items = [];
      }
    }
    const offers = mapItems(this.actor, items, this.settings.today());
    const costMicroUsd = runCostMicroUsd(
      run,
      this.actor.prices,
      this.settings.tier,
      items.length,
      this.actor.memoryMegabytes,
    );
    // A run stopped by its timeout or its charge cap keeps (and bills) what it found: use it.
    if (run.status === "SUCCEEDED" || offers.length > 0) return { kind: "finished", offers, costMicroUsd };
    return { kind: "failed", code: `run_${run.status.toLowerCase().replace("-", "_")}`, costMicroUsd };
  }
}

/** Result of the reading of one offer by an actor. */
export interface ActorReading {
  readonly offer: ExtractedOffer | null;
  readonly costMicroUsd: number;
}

/**
 * Reads one offer with an actor, for the sites whose pages refuse direct reading (Indeed). The run is started with
 * `waitForFinish` (the request waits for the end of the run on Apify's side, within the worker, never within a
 * request of the app), checked once more, then aborted when it is still running.
 */
export class ApifyOfferActorReader {
  /**
   * @param actor actor reading one offer.
   * @param client Apify client.
   * @param tier discount tier of the account.
   * @param waits seconds to wait at the start, then at the check.
   */
  constructor(
    private readonly actor: OfferActor,
    private readonly client: ApifyClient,
    private readonly tier: PricingTier,
    private readonly waits: { readonly start: number; readonly check: number } = { start: 30, check: 10 },
  ) {}

  /** Highest cost of a reading, in micro US dollars. */
  estimateCostMicroUsd(): number {
    return estimateRunCostMicroUsd(this.actor.prices, this.tier, 1, this.actor.memoryMegabytes);
  }

  /**
   * Reads the offer of a link.
   *
   * @param url canonical link.
   * @param today date of today (ISO).
   */
  async read(url: string, today: string): Promise<ActorReading> {
    let run = await this.client.startRun(this.actor.actorId, this.actor.buildInput(url), {
      maximumItems: 1,
      maximumTotalChargeUsd: this.estimateCostMicroUsd() / 1_000_000,
      timeoutSeconds: this.actor.timeoutSeconds,
      memoryMegabytes: this.actor.memoryMegabytes,
      waitForFinishSeconds: this.waits.start,
    });
    if (!isTerminal(run.status)) run = await this.client.getRun(run.id, this.waits.check);
    if (!isTerminal(run.status)) {
      try {
        await this.client.abortRun(run.id);
      } catch {
        // The run timeout stops it anyway.
      }
      return { offer: null, costMicroUsd: this.estimateCostMicroUsd() };
    }
    const items = run.defaultDatasetId
      ? await this.client.getDatasetItems(run.defaultDatasetId, { limit: 1, fields: this.actor.fields })
      : [];
    const first = items.find(isRecord);
    let offer: ExtractedOffer | null = null;
    try {
      offer = first ? this.actor.mapItem(first, { today }) : null;
    } catch {
      offer = null;
    }
    return {
      offer,
      costMicroUsd: runCostMicroUsd(run, this.actor.prices, this.tier, items.length, this.actor.memoryMegabytes),
    };
  }
}
