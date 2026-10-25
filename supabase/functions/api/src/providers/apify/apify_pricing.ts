import type { ApifyRun } from "./apify_client.ts";

/**
 * Prices of the Apify actors used by Jauge (pay per event: one event per result, sometimes one per start), the cost
 * estimate checked against the daily limit before a run, and the real cost of a finished run.
 *
 * Every actor of Jauge is "pay per event": the platform usage (compute, proxies) is included in the event prices, so
 * the cost of a run is the sum of its charged events. The run caps (`maxItems`, `maxTotalChargeUsd`) make the
 * estimate a hard ceiling: Apify stops charging past it.
 */

/** Discount tiers of Apify (the tier follows the plan: Free = FREE, Starter = BRONZE, Scale = SILVER...). */
export type PricingTier = "FREE" | "BRONZE" | "SILVER" | "GOLD" | "PLATINUM" | "DIAMOND";

/** Tiers in order. */
export const PRICING_TIERS: readonly PricingTier[] = ["FREE", "BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND"];

/** Prices of one actor in US dollars, as published on its Apify page. */
export interface ActorPrices {
  /** Event charged per result (`apify-default-dataset-item` for most actors). */
  readonly resultEvent: string;
  /** Price of a result per tier; a missing tier takes the price of the closest lower tier. */
  readonly resultUsd: Readonly<Partial<Record<PricingTier, number>>> & { readonly FREE: number };
  /** Price of the start event, per gigabyte of memory (`apify-actor-start`), 0 when the actor has none. */
  readonly startUsdPerGigabyte: number;
}

/** Name of the start event of pay per event actors. */
export const START_EVENT = "apify-actor-start";

/** Price of a result for a tier. */
export function resultPriceUsd(prices: ActorPrices, tier: PricingTier): number {
  const index = PRICING_TIERS.indexOf(tier);
  for (let position = index; position >= 0; position--) {
    const price = prices.resultUsd[PRICING_TIERS[position]];
    if (price !== undefined) return price;
  }
  return prices.resultUsd.FREE;
}

/** Number of start events of a run: one per gigabyte of memory, one at least. */
export function startEvents(memoryMegabytes: number): number {
  return Math.max(1, Math.ceil(memoryMegabytes / 1024));
}

/**
 * Highest cost of a run, in micro US dollars: start events plus `maximumItems` results.
 *
 * @param prices prices of the actor.
 * @param tier tier of the account.
 * @param maximumItems results asked.
 * @param memoryMegabytes memory of the run.
 */
export function estimateRunCostMicroUsd(
  prices: ActorPrices,
  tier: PricingTier,
  maximumItems: number,
  memoryMegabytes: number,
): number {
  const usd = prices.startUsdPerGigabyte * startEvents(memoryMegabytes) + resultPriceUsd(prices, tier) * maximumItems;
  return Math.ceil(usd * 1_000_000);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** Price of an event in the `pricingInfo` of a run (pay per event), for a tier, or null. */
function eventPriceFromRun(pricingInfo: unknown, event: string, tier: PricingTier): number | null {
  if (!isRecord(pricingInfo) || !isRecord(pricingInfo.pricingPerEvent)) return null;
  const events = pricingInfo.pricingPerEvent.actorChargeEvents;
  if (!isRecord(events) || !isRecord(events[event])) return null;
  const definition = events[event] as Record<string, unknown>;
  if (typeof definition.eventPriceUsd === "number") return definition.eventPriceUsd;
  if (isRecord(definition.eventTieredPricingUsd)) {
    const tiers = definition.eventTieredPricingUsd;
    const index = PRICING_TIERS.indexOf(tier);
    for (let position = index; position >= 0; position--) {
      const entry = tiers[PRICING_TIERS[position]];
      if (isRecord(entry) && typeof entry.tieredEventPriceUsd === "number") return entry.tieredEventPriceUsd;
    }
  }
  return null;
}

/**
 * Real cost of a run in micro US dollars: the charged events at the prices of the run (or of the catalog when the run
 * does not carry them). Without event counts (older API answers), the results read and one start are counted.
 *
 * @param run run as read from Apify.
 * @param prices prices of the actor in the catalog.
 * @param tier tier of the account.
 * @param itemCount results read from the dataset (fallback).
 * @param memoryMegabytes memory of the run (fallback).
 */
export function runCostMicroUsd(
  run: ApifyRun,
  prices: ActorPrices,
  tier: PricingTier,
  itemCount: number,
  memoryMegabytes: number,
): number {
  let usd = 0;
  if (run.chargedEventCounts && Object.keys(run.chargedEventCounts).length > 0) {
    for (const [event, count] of Object.entries(run.chargedEventCounts)) {
      const price = eventPriceFromRun(run.pricingInfo, event, tier) ??
        (event === prices.resultEvent
          ? resultPriceUsd(prices, tier)
          : event === START_EVENT
          ? prices.startUsdPerGigabyte
          : 0);
      usd += price * count;
    }
  } else {
    usd = prices.startUsdPerGigabyte * startEvents(memoryMegabytes) + resultPriceUsd(prices, tier) * itemCount;
  }
  return Math.round(usd * 1_000_000);
}
