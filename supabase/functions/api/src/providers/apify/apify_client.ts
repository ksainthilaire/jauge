import type { FetchFunction } from "../live_account_services.ts";
import { ProviderError } from "../provider_types.ts";

/**
 * Small client of the Apify API v2 (https://docs.apify.com/api/v2): start an actor run, read a run, read the items of
 * its dataset, abort it. The token goes in the Authorization header only, never in a URL or a log.
 *
 * Retries: reads (GET) are retried twice on network errors, 429 and 5xx, with a short backoff. A start is retried only
 * on 429 and 5xx (Apify refused it before creating the run); never after a network error, which could have created a
 * paid run already.
 */

/** Status of an actor run, as Apify writes it. */
export type ApifyRunStatus =
  | "READY"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "TIMING-OUT"
  | "TIMED-OUT"
  | "ABORTING"
  | "ABORTED";

/** The fields of a run that Jauge uses. */
export interface ApifyRun {
  readonly id: string;
  readonly actorId: string | null;
  readonly status: ApifyRunStatus;
  readonly defaultDatasetId: string | null;
  /** Events charged so far by a pay per event actor (`{"apify-default-dataset-item": 60}`). */
  readonly chargedEventCounts: Readonly<Record<string, number>> | null;
  /** Prices of the actor at the time of the run (pay per event), when Apify gives them. */
  readonly pricingInfo: unknown;
  /** Platform usage paid by the user, in US dollars (0 for pay per event actors). */
  readonly usageTotalUsd: number | null;
}

/** True when a run will not change any more. */
export function isTerminal(status: ApifyRunStatus): boolean {
  return status === "SUCCEEDED" || status === "FAILED" || status === "TIMED-OUT" || status === "ABORTED";
}

/** Options of a run start. */
export interface RunOptions {
  /** Largest number of results charged (pay per result and pay per event actors stop charging past it). */
  readonly maximumItems: number;
  /** Largest amount the run may charge, in US dollars (hard cap of a pay per event run). */
  readonly maximumTotalChargeUsd: number;
  /** Seconds after which Apify stops the run. */
  readonly timeoutSeconds: number;
  /** Memory of the run in megabytes (the start event of some actors is charged per gigabyte). */
  readonly memoryMegabytes: number;
  /** Seconds the start request waits for the run to finish (0 to 60). */
  readonly waitForFinishSeconds?: number;
  /** Ad hoc webhook called by Apify when the run ends. */
  readonly webhook?: { readonly url: string; readonly secret: string } | null;
}

/** Settings of the client. */
export interface ApifyClientSettings {
  readonly baseUrl: string;
  /** Timeout of one HTTP request, in milliseconds (a start that waits adds its own wait). */
  readonly requestTimeoutMilliseconds: number;
  /** Retries of a request after the first attempt. */
  readonly retries: number;
  /** First backoff in milliseconds (doubled at each retry). */
  readonly backoffMilliseconds: number;
}

/** Default settings. */
export const DEFAULT_APIFY_CLIENT_SETTINGS: ApifyClientSettings = {
  baseUrl: "https://api.apify.com/v2",
  requestTimeoutMilliseconds: 10_000,
  retries: 2,
  backoffMilliseconds: 500,
};

/** Name of the provider in errors and logs. */
export const APIFY_PROVIDER = "apify";

const TERMINAL_EVENTS = ["ACTOR.RUN.SUCCEEDED", "ACTOR.RUN.FAILED", "ACTOR.RUN.TIMED_OUT", "ACTOR.RUN.ABORTED"];

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** Reads a run object of the API. */
export function parseRun(value: unknown): ApifyRun {
  const data = isRecord(value) && isRecord(value.data) ? value.data : value;
  if (!isRecord(data) || typeof data.id !== "string" || typeof data.status !== "string") {
    throw new ProviderError(APIFY_PROVIDER, "invalid_run", true);
  }
  const counts = isRecord(data.chargedEventCounts)
    ? Object.fromEntries(
      Object.entries(data.chargedEventCounts).filter(([, count]) => typeof count === "number" && count >= 0),
    ) as Record<string, number>
    : null;
  return {
    id: data.id,
    actorId: typeof data.actId === "string" ? data.actId : null,
    status: data.status as ApifyRunStatus,
    defaultDatasetId: typeof data.defaultDatasetId === "string" ? data.defaultDatasetId : null,
    chargedEventCounts: counts,
    pricingInfo: data.pricingInfo ?? null,
    usageTotalUsd: typeof data.usageTotalUsd === "number" ? data.usageTotalUsd : null,
  };
}

/** Client of the Apify API. */
export class ApifyClient {
  private readonly settings: ApifyClientSettings;

  /**
   * @param token API token of the Apify account (secret `APIFY_TOKEN`).
   * @param fetcher fetch function (recorded answers in the tests).
   * @param settings settings.
   * @param sleep waits between retries (injected so that tests do not wait).
   */
  constructor(
    private readonly token: string,
    private readonly fetcher: FetchFunction = fetch,
    settings: Partial<ApifyClientSettings> = {},
    private readonly sleep: (milliseconds: number) => Promise<void> = (milliseconds) =>
      new Promise((resolve) => setTimeout(resolve, milliseconds)),
  ) {
    this.settings = { ...DEFAULT_APIFY_CLIENT_SETTINGS, ...settings };
  }

  /**
   * Starts an actor run (`POST /acts/{actorId}/runs`).
   *
   * @param actorId actor, as `owner~name`.
   * @param input input of the actor.
   * @param options limits of the run.
   */
  async startRun(actorId: string, input: Record<string, unknown>, options: RunOptions): Promise<ApifyRun> {
    const parameters = new URLSearchParams({
      timeout: String(Math.round(options.timeoutSeconds)),
      memory: String(options.memoryMegabytes),
      maxItems: String(Math.max(1, Math.round(options.maximumItems))),
      maxTotalChargeUsd: (Math.ceil(options.maximumTotalChargeUsd * 1_000_000) / 1_000_000).toFixed(6),
    });
    const wait = Math.min(60, Math.max(0, Math.round(options.waitForFinishSeconds ?? 0)));
    if (wait > 0) parameters.set("waitForFinish", String(wait));
    if (options.webhook) {
      parameters.set(
        "webhooks",
        btoa(JSON.stringify([{
          eventTypes: TERMINAL_EVENTS,
          requestUrl: options.webhook.url,
          headersTemplate: JSON.stringify({ "x-jauge-apify-secret": options.webhook.secret }),
        }])),
      );
    }
    const body = await this.request(
      "POST",
      `/acts/${encodeURIComponent(actorId)}/runs?${parameters}`,
      input,
      wait * 1000,
      false,
    );
    return parseRun(body);
  }

  /**
   * Reads a run (`GET /actor-runs/{runId}`).
   *
   * @param runId identifier of the run.
   * @param waitForFinishSeconds seconds Apify may wait for the end of the run before answering (0 to 60).
   */
  async getRun(runId: string, waitForFinishSeconds = 0): Promise<ApifyRun> {
    const wait = Math.min(60, Math.max(0, Math.round(waitForFinishSeconds)));
    const query = wait > 0 ? `?waitForFinish=${wait}` : "";
    return parseRun(await this.request("GET", `/actor-runs/${encodeURIComponent(runId)}${query}`, null, wait * 1000));
  }

  /**
   * Reads the items of a dataset (`GET /datasets/{datasetId}/items`), only the given fields, at most `limit`.
   *
   * @param datasetId dataset of the run.
   * @param options largest number of items and fields to keep (smaller answers: descriptions are long).
   */
  async getDatasetItems(datasetId: string, options: { limit: number; fields?: readonly string[] }): Promise<unknown[]> {
    const parameters = new URLSearchParams({
      clean: "true",
      format: "json",
      limit: String(Math.max(1, options.limit)),
    });
    if (options.fields && options.fields.length > 0) parameters.set("fields", options.fields.join(","));
    const body = await this.request("GET", `/datasets/${encodeURIComponent(datasetId)}/items?${parameters}`, null, 0);
    if (!Array.isArray(body)) throw new ProviderError(APIFY_PROVIDER, "invalid_items", true);
    return body;
  }

  /**
   * Aborts a run (`POST /actor-runs/{runId}/abort`). Results already charged stay charged.
   *
   * @param runId identifier of the run.
   */
  async abortRun(runId: string): Promise<void> {
    await this.request("POST", `/actor-runs/${encodeURIComponent(runId)}/abort`, null, 0, true);
  }

  private async request(
    method: "GET" | "POST",
    path: string,
    body: unknown,
    extraTimeoutMilliseconds: number,
    retryOnNetworkError = method === "GET",
  ): Promise<unknown> {
    let lastError: ProviderError | null = null;
    for (let attempt = 0; attempt <= this.settings.retries; attempt++) {
      if (attempt > 0) await this.sleep(this.settings.backoffMilliseconds * 2 ** (attempt - 1));
      let response: Response;
      try {
        response = await this.fetcher(`${this.settings.baseUrl}${path}`, {
          method,
          headers: {
            Authorization: `Bearer ${this.token}`,
            ...(body !== null ? { "Content-Type": "application/json" } : {}),
          },
          body: body !== null ? JSON.stringify(body) : undefined,
          signal: AbortSignal.timeout(this.settings.requestTimeoutMilliseconds + extraTimeoutMilliseconds),
        });
      } catch (error) {
        const code = error instanceof DOMException && error.name === "TimeoutError" ? "timeout" : "network";
        lastError = new ProviderError(APIFY_PROVIDER, code, true);
        if (retryOnNetworkError) continue;
        throw lastError;
      }
      if (response.ok) {
        try {
          return await response.json();
        } catch {
          throw new ProviderError(APIFY_PROVIDER, "invalid_json", true);
        }
      }
      await response.body?.cancel();
      lastError = errorOfStatus(response.status);
      if (!lastError.retryable) throw lastError;
    }
    throw lastError ?? new ProviderError(APIFY_PROVIDER, "unknown", true);
  }
}

/** Error of an HTTP status of the Apify API. */
function errorOfStatus(status: number): ProviderError {
  // 401, 403: wrong or revoked token. 402: no credit left or monthly usage limit of the account reached.
  if (status === 401 || status === 403) return new ProviderError(APIFY_PROVIDER, "not_authorized", false);
  if (status === 402) return new ProviderError(APIFY_PROVIDER, "payment_required", false);
  if (status === 404) return new ProviderError(APIFY_PROVIDER, "not_found", false);
  if (status === 429) return new ProviderError(APIFY_PROVIDER, "rate_limited", true);
  if (status >= 500) return new ProviderError(APIFY_PROVIDER, `http_${status}`, true);
  return new ProviderError(APIFY_PROVIDER, `http_${status}`, false);
}
