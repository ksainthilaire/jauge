import type { FetchFunction } from "../providers/live_account_services.ts";

/**
 * A fake of the Apify API v2 and of the job sites, answering with recorded data (`providers/apify/fixtures/`,
 * `providers/offers/fixtures/`). No test ever reaches the network.
 */

/** A call received by the fake. */
export interface RecordedCall {
  readonly method: string;
  readonly url: URL;
  readonly body: unknown;
  readonly headers: Headers;
}

/** How one actor behaves in the fake. */
export interface FakeActor {
  /** Items of the dataset of each run of the actor. */
  readonly items: readonly unknown[];
  /** Polls answered RUNNING before the run ends. */
  readonly runningPolls?: number;
  /** Final status of the runs. */
  readonly finalStatus?: "SUCCEEDED" | "FAILED" | "TIMED-OUT" | "ABORTED";
  /** Price of a result in US dollars (charged event `apify-default-dataset-item`). */
  readonly resultUsd?: number;
}

interface FakeRun {
  readonly id: string;
  readonly actorId: string;
  readonly datasetId: string;
  pollsLeft: number;
  readonly finalStatus: string;
  readonly maximumItems: number;
}

/** The fake server. */
export class FakeApify {
  readonly calls: RecordedCall[] = [];
  readonly runs = new Map<string, FakeRun>();
  /** Answers of the job sites by URL (HTML), or a status code. */
  readonly pages = new Map<string, string | number>();
  /** Status answered by the Apify API before the real answer, for each next call (retries tests). */
  readonly failures: (number | "network")[] = [];
  private counter = 0;

  /** @param actors behavior of each actor, by `owner~name`. */
  constructor(readonly actors: Record<string, FakeActor>) {}

  /** The fetch function to give to the connectors. */
  readonly fetcher: FetchFunction = async (input, init) => {
    const url = new URL(input);
    const method = init?.method ?? "GET";
    const body = typeof init?.body === "string" && init.body.length > 0 ? JSON.parse(init.body) : null;
    this.calls.push({ method, url, body, headers: new Headers(init?.headers) });
    if (url.hostname !== "api.apify.com") return this.page(url);
    const failure = this.failures.shift();
    if (failure === "network") throw new TypeError("connection reset");
    if (failure !== undefined) return new Response("{}", { status: failure });
    return await Promise.resolve(this.apify(method, url));
  };

  /** Calls made to Apify (not to the job sites). */
  get apifyCalls(): RecordedCall[] {
    return this.calls.filter((call) => call.url.hostname === "api.apify.com");
  }

  /** Runs started for an actor. */
  startsOf(actorId: string): RecordedCall[] {
    return this.apifyCalls.filter((call) => call.method === "POST" && call.url.pathname === `/v2/acts/${actorId}/runs`);
  }

  private page(url: URL): Response {
    const answer = this.pages.get(url.toString());
    if (answer === undefined) return new Response("Not found", { status: 404 });
    if (typeof answer === "number") return new Response("", { status: answer });
    return new Response(answer, { status: 200, headers: { "content-type": "text/html; charset=utf-8" } });
  }

  private apify(method: string, url: URL): Response {
    const start = /^\/v2\/acts\/([^/]+)\/runs$/.exec(url.pathname);
    if (method === "POST" && start) {
      const actorId = decodeURIComponent(start[1]);
      const actor = this.actors[actorId];
      if (!actor) return new Response("{}", { status: 404 });
      this.counter += 1;
      const run: FakeRun = {
        id: `run${String(this.counter).padStart(8, "0")}`,
        actorId,
        datasetId: `dataset${String(this.counter).padStart(8, "0")}`,
        pollsLeft: actor.runningPolls ?? 1,
        finalStatus: actor.finalStatus ?? "SUCCEEDED",
        maximumItems: Number(url.searchParams.get("maxItems") ?? "1000"),
      };
      this.runs.set(run.id, run);
      // `waitForFinish`: the run ends during the request when it needs no poll.
      const waits = Number(url.searchParams.get("waitForFinish") ?? "0") > 0;
      if (waits && run.pollsLeft === 0) return this.json(this.runObject(run, true), 201);
      return this.json(this.runObject(run, false), 201);
    }
    const read = /^\/v2\/actor-runs\/([^/]+)$/.exec(url.pathname);
    if (method === "GET" && read) {
      const run = this.runs.get(read[1]);
      if (!run) return new Response("{}", { status: 404 });
      if (run.pollsLeft > 0) {
        run.pollsLeft -= 1;
        return this.json(this.runObject(run, false));
      }
      return this.json(this.runObject(run, true));
    }
    const abort = /^\/v2\/actor-runs\/([^/]+)\/abort$/.exec(url.pathname);
    if (method === "POST" && abort) {
      const run = this.runs.get(abort[1]);
      if (!run) return new Response("{}", { status: 404 });
      run.pollsLeft = 0;
      return this.json(this.runObject(run, true));
    }
    const items = /^\/v2\/datasets\/([^/]+)\/items$/.exec(url.pathname);
    if (method === "GET" && items) {
      const run = [...this.runs.values()].find((candidate) => candidate.datasetId === items[1]);
      if (!run) return new Response("{}", { status: 404 });
      return this.json(this.itemsOf(run).slice(0, Number(url.searchParams.get("limit") ?? "1000")));
    }
    return new Response("{}", { status: 404 });
  }

  private itemsOf(run: FakeRun): unknown[] {
    return this.actors[run.actorId].items.slice(0, run.maximumItems);
  }

  private runObject(run: FakeRun, ended: boolean): Record<string, unknown> {
    const actor = this.actors[run.actorId];
    const resultUsd = actor.resultUsd ?? 0.001;
    const itemCount = ended ? this.itemsOf(run).length : 0;
    return {
      data: {
        id: run.id,
        actId: run.actorId,
        status: ended ? run.finalStatus : "RUNNING",
        defaultDatasetId: run.datasetId,
        chargedEventCounts: { "apify-actor-start": 1, "apify-default-dataset-item": itemCount },
        pricingInfo: {
          pricingModel: "PAY_PER_EVENT",
          pricingPerEvent: {
            actorChargeEvents: {
              "apify-default-dataset-item": { eventPriceUsd: resultUsd },
              "apify-actor-start": { eventPriceUsd: 0.00005 },
            },
          },
        },
        usageTotalUsd: 0,
      },
    };
  }

  private json(value: unknown, status = 200): Response {
    return new Response(JSON.stringify(value), { status, headers: { "content-type": "application/json" } });
  }
}

/** Reads a fixture file next to the code (tests run with `--allow-read`). */
export async function readFixture(relativePath: string): Promise<string> {
  return await Deno.readTextFile(new URL(`../providers/${relativePath}`, import.meta.url));
}

/** Reads a JSON fixture. */
export async function readJsonFixture<Value = unknown>(relativePath: string): Promise<Value> {
  return JSON.parse(await readFixture(relativePath)) as Value;
}
