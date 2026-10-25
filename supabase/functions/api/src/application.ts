import type { AuthenticatedUser, TokenVerifier } from "./authentication/token_verifier.ts";
import type { Configuration } from "./configuration.ts";
import type { Database } from "./database/database.ts";
import { ApiError, notFound, validationFailed } from "./http/api_error.ts";
import { emptyResponse, errorResponse, jsonResponse } from "./http/responses.ts";
import { Router, stripFunctionPrefix } from "./http/router.ts";
import {
  isObject,
  optionalBoolean,
  readJsonObject,
  readLimitedText,
  rejectUnknownFields,
  requiredChoice,
  requiredInteger,
  resourceIdentifier,
  UUID_PATTERN,
} from "./http/validation.ts";
import type { Clock } from "./operations/clock.ts";
import type { Logger } from "./operations/logger.ts";
import { Operations } from "./operations/maintenance.ts";
import { hashAddress, RATE_LIMITS, RateLimiter, secretsMatch } from "./operations/rate_limiter.ts";
import { SpendGuard } from "./operations/spend_guard.ts";
import type { Providers } from "./providers/provider_types.ts";
import { AnalysisPipeline, DEFAULT_PIPELINE_SETTINGS, type PipelineSettings } from "./queue/analysis_pipeline.ts";
import { DEFAULT_WORKER_SETTINGS, Worker, type WorkerSettings } from "./queue/worker.ts";
import { idempotencyKey, parseJsonAnalysisRequest, parseMultipartAnalysisRequest } from "./routes/analysis_requests.ts";
import { AccountService } from "./services/account_service.ts";
import { AlertService } from "./services/alert_service.ts";
import { presentAnalysis, presentSummary } from "./services/analysis_records.ts";
import { AnalysisService } from "./services/analysis_service.ts";
import { parseAreaParts } from "./services/area.ts";
import { InsightService, type TwinSort } from "./services/insight_service.ts";
import { RevenueCatWebhookService } from "./services/revenuecat_webhook.ts";
import { ShareService } from "./services/share_service.ts";
import { SubscriptionService } from "./services/subscription_service.ts";
import { type Language, languageFromHeader } from "./scoring/methodology_texts.ts";

/** Everything the application needs from outside. */
export interface ApplicationDependencies {
  readonly configuration: Configuration;
  readonly database: Database;
  readonly tokenVerifier: TokenVerifier;
  readonly providers: Providers;
  readonly clock: Clock;
  readonly logger: Logger;
  /** Keeps background work alive after the answer (`EdgeRuntime.waitUntil` in production). */
  readonly runInBackground: (work: Promise<unknown>) => void;
  readonly pipelineSettings?: PipelineSettings;
  readonly workerSettings?: WorkerSettings;
  readonly sleep?: (milliseconds: number) => Promise<void>;
}

/** What a handler receives. */
interface HandlerContext {
  readonly request: Request;
  readonly url: URL;
  readonly parameters: Readonly<Record<string, string>>;
  readonly language: Language;
  /** Signed in user (routes under `/v1`). */
  readonly user: AuthenticatedUser;
}

type Handler = (context: HandlerContext) => Promise<Response>;

/** Kinds of routes: what each one checks before its handler. */
type Access = "user" | "public" | "internal" | "webhook";

interface RouteEntry {
  readonly access: Access;
  readonly handler: Handler;
}

/** The API, ready to serve requests. */
export interface Application {
  /** Serves one request. */
  handle(request: Request): Promise<Response>;
  /** The worker of the queue (run by `/internal/worker`, and by the tests). */
  readonly worker: Worker;
  /** Daily maintenance and health. */
  readonly operations: Operations;
}

const ANONYMOUS_USER: AuthenticatedUser = { userId: "", isAnonymous: true };
const PUBLIC_ORIGIN = "https://jauge.io";

function queryParameter(url: URL, name: string): string | null {
  const values = url.searchParams.getAll(name);
  if (values.length > 1) throw validationFailed(`${name} is repeated`);
  return values[0] ?? null;
}

function integerParameter(
  url: URL,
  name: string,
  minimum: number,
  maximum: number,
  defaultValue: number | null,
): number | null {
  const value = queryParameter(url, name);
  if (value === null || value === "") return defaultValue;
  if (!/^\d{1,4}$/.test(value)) throw validationFailed(`${name} must be an integer`);
  const number = Number(value);
  if (number < minimum || number > maximum) throw validationFailed(`${name} is out of range`);
  return number;
}

function searchParameter(url: URL): string | null {
  const query = queryParameter(url, "query");
  if (query !== null && query.length > 100) throw validationFailed("query is longer than 100 characters");
  return query;
}

function errorDetails(error: unknown): Record<string, string> {
  const details: Record<string, string> = { error: error instanceof Error ? error.name : "unknown" };
  if (error && typeof error === "object" && "code" in error && typeof error.code === "string") {
    details.databaseCode = error.code.slice(0, 10);
  }
  return details;
}

/**
 * Builds the API: services, worker and routes of docs/openapi.yaml, plus `/health`, `/public/analyses/{id}`,
 * `/webhooks/revenuecat` and the internal routes called by the database (`/internal/worker`, `/internal/maintenance`).
 */
export function createApplication(dependencies: ApplicationDependencies): Application {
  const { configuration, database, providers, clock, logger } = dependencies;
  const subscriptions = new SubscriptionService(database, clock, configuration.freeAnalysesPerWeek);
  const spendGuard = new SpendGuard(
    database,
    clock,
    {
      dailyLimitUsd: configuration.dailySpendLimitUsd,
      warningRatio: configuration.spendWarningRatio,
      simulated: providers.mode === "simulated",
    },
    providers.operationsAlerter,
    logger,
  );
  const analyses = new AnalysisService(database, subscriptions, configuration, clock);
  const insights = new InsightService(database, clock);
  const alerts = new AlertService(database, subscriptions, insights, logger);
  const shares = new ShareService(database);
  const accounts = new AccountService(database, providers, logger);
  const revenueCat = new RevenueCatWebhookService(database, logger);
  const rateLimiter = new RateLimiter(database);
  const pipeline = new AnalysisPipeline(
    database,
    providers,
    spendGuard,
    subscriptions,
    configuration,
    clock,
    logger,
    dependencies.pipelineSettings ?? DEFAULT_PIPELINE_SETTINGS,
  );
  const evaluateAlertsHandler = {
    runStage: async () => {
      await alerts.evaluate();
      return { kind: "done" as const };
    },
    giveUp: () => Promise.resolve(),
  };
  const worker = new Worker(
    database,
    { analyze_offer: pipeline, delete_account: accounts, evaluate_alerts: evaluateAlertsHandler },
    clock,
    logger,
    dependencies.workerSettings ?? DEFAULT_WORKER_SETTINGS,
    dependencies.sleep,
  );
  const operations = new Operations(database, configuration, providers, spendGuard, alerts, clock, logger);

  /** Runs the worker after the answer when the database does not wake it (no pg_net). */
  const wakeWorker = () => {
    if (configuration.inlineWorker) {
      dependencies.runInBackground(
        worker.run().catch((error) => logger.log("error", "inline_worker_failed", errorDetails(error))),
      );
    }
  };

  const router = new Router<RouteEntry>();
  const user = (handler: Handler): RouteEntry => ({ access: "user", handler });

  // Analyses ------------------------------------------------------------------------------------------------------
  router.add(
    "POST",
    "/v1/analyses",
    user(async ({ request, user, language }) => {
      await rateLimiter.hit(RATE_LIMITS.analysesPerMinute, user.userId);
      const contentType = (request.headers.get("content-type") ?? "").toLowerCase();
      const input = contentType.startsWith("multipart/form-data")
        ? await parseMultipartAnalysisRequest(request)
        : parseJsonAnalysisRequest(await readJsonObject(request));
      const record = await analyses.create(user.userId, input, { idempotencyKey: idempotencyKey(request), language });
      if (record.status === "processing") wakeWorker();
      return jsonResponse(presentAnalysis(record), 202, { Location: `/v1/analyses/${record.id}` });
    }),
  );
  router.add(
    "GET",
    "/v1/analyses",
    user(async ({ url, user }) => {
      const page = await analyses.list(user.userId, {
        cursor: queryParameter(url, "cursor"),
        limit: integerParameter(url, "limit", 1, 100, 20)!,
        query: searchParameter(url),
      });
      return jsonResponse({ items: page.items.map(presentSummary), nextCursor: page.nextCursor });
    }),
  );
  router.add(
    "GET",
    "/v1/analyses/:id",
    user(async ({ parameters, user }) => {
      const record = await analyses.get(user.userId, resourceIdentifier(parameters.id, "Analysis"));
      // While processing, the app polls every second: help the scheduler when pg_net is absent.
      if (record.status === "processing" && configuration.inlineWorker) wakeWorker();
      return jsonResponse(presentAnalysis(record));
    }),
  );
  router.add(
    "DELETE",
    "/v1/analyses/:id",
    user(async ({ parameters, user }) => {
      await analyses.delete(user.userId, resourceIdentifier(parameters.id, "Analysis"));
      return emptyResponse();
    }),
  );
  router.add(
    "PUT",
    "/v1/analyses/:id/favorite",
    user(async ({ parameters, user }) => {
      await analyses.setFavorite(user.userId, resourceIdentifier(parameters.id, "Analysis"), true);
      return emptyResponse();
    }),
  );
  router.add(
    "DELETE",
    "/v1/analyses/:id/favorite",
    user(async ({ parameters, user }) => {
      await analyses.setFavorite(user.userId, resourceIdentifier(parameters.id, "Analysis"), false);
      return emptyResponse();
    }),
  );

  // Insights ------------------------------------------------------------------------------------------------------
  const completed = (context: HandlerContext) =>
    analyses.getCompleted(context.user.userId, resourceIdentifier(context.parameters.id, "Analysis"));
  router.add(
    "GET",
    "/v1/analyses/:id/salary",
    user(async (context) => {
      const area = parseAreaParts(queryParameter(context.url, "city"), queryParameter(context.url, "department"), null);
      return jsonResponse(await insights.salary(await completed(context), area));
    }),
  );
  router.add(
    "GET",
    "/v1/analyses/:id/skills",
    user(async (context) => jsonResponse(await insights.skills(await completed(context)))),
  );
  router.add(
    "GET",
    "/v1/analyses/:id/trend",
    user(async (context) => jsonResponse(await insights.trend(await completed(context)))),
  );
  router.add(
    "GET",
    "/v1/analyses/:id/twins",
    user(async (context) => {
      const { url } = context;
      const sortValue = queryParameter(url, "sort") ?? "least_disputed";
      const sort = requiredChoice(
        sortValue,
        "sort",
        ["least_disputed", "best_paid", "most_recent"] as const,
      ) as TwinSort;
      const radius = integerParameter(url, "radius", 1, 200, null);
      const area = parseAreaParts(queryParameter(url, "city"), queryParameter(url, "department"), radius ?? undefined);
      return jsonResponse(
        await insights.twins(await completed(context), { area, radius, sort, query: searchParameter(url) }),
      );
    }),
  );
  router.add(
    "POST",
    "/v1/analyses/:id/feedback",
    user(async (context) => {
      const body = await readJsonObject(context.request);
      rejectUnknownFields(body, ["actualApplicants"]);
      const actualApplicants = requiredInteger(body.actualApplicants, "actualApplicants", 1, 100_000);
      await analyses.refuseWhenDeleting(context.user.userId);
      await insights.saveFeedback(context.user.userId, await completed(context), actualApplicants);
      return emptyResponse();
    }),
  );
  router.add(
    "GET",
    "/v1/methodology",
    user(async ({ url, user, language }) => {
      const identifier = resourceIdentifier(queryParameter(url, "analysisId"), "Analysis");
      return jsonResponse(await insights.methodology(await analyses.getCompleted(user.userId, identifier), language));
    }),
  );

  // Alerts --------------------------------------------------------------------------------------------------------
  router.add("GET", "/v1/alerts", user(async ({ user }) => jsonResponse(await alerts.list(user.userId))));
  router.add(
    "POST",
    "/v1/alerts",
    user(async ({ request, user }) => {
      const body = await readJsonObject(request);
      rejectUnknownFields(body, ["analysisId", "threshold"]);
      const analysisId = resourceIdentifier(typeof body.analysisId === "string" ? body.analysisId : null, "Analysis");
      const threshold = body.threshold === undefined ? 30 : requiredInteger(body.threshold, "threshold", 1, 100);
      await analyses.refuseWhenDeleting(user.userId);
      return jsonResponse(await alerts.create(user.userId, analysisId, threshold), 201);
    }),
  );
  router.add(
    "DELETE",
    "/v1/alerts/:id",
    user(async ({ parameters, user }) => {
      await alerts.delete(user.userId, resourceIdentifier(parameters.id, "Alert"));
      return emptyResponse();
    }),
  );

  // Shares --------------------------------------------------------------------------------------------------------
  router.add("GET", "/v1/shares", user(async ({ user }) => jsonResponse(await shares.list(user.userId))));
  router.add(
    "POST",
    "/v1/shares",
    user(async ({ request, user }) => {
      const body = await readJsonObject(request);
      rejectUnknownFields(body, ["analysisId", "format", "hideCompany", "showCalmTwin"]);
      const analysisId = resourceIdentifier(typeof body.analysisId === "string" ? body.analysisId : null, "Analysis");
      await analyses.refuseWhenDeleting(user.userId);
      return jsonResponse(
        await shares.create(user.userId, {
          analysisId,
          format: requiredChoice(body.format, "format", ["story", "square", "link"] as const),
          hideCompany: optionalBoolean(body.hideCompany, "hideCompany", false),
          showCalmTwin: optionalBoolean(body.showCalmTwin, "showCalmTwin", true),
        }),
        201,
      );
    }),
  );
  router.add(
    "DELETE",
    "/v1/shares/:id",
    user(async ({ parameters, user }) => {
      await shares.delete(user.userId, parameters.id ?? "");
      return emptyResponse();
    }),
  );

  // Account and subscription ----------------------------------------------------------------------------------------
  router.add("GET", "/v1/account", user(async ({ user }) => jsonResponse(await accounts.get(user.userId))));
  router.add(
    "PATCH",
    "/v1/account/consents",
    user(async ({ request, user }) => {
      const body = await readJsonObject(request);
      rejectUnknownFields(body, ["marketingEmails"]);
      if (typeof body.marketingEmails !== "boolean") throw validationFailed("marketingEmails must be a boolean", 422);
      await analyses.refuseWhenDeleting(user.userId);
      return jsonResponse(await accounts.updateConsents(user.userId, body.marketingEmails));
    }),
  );
  router.add(
    "DELETE",
    "/v1/account",
    user(async ({ request, user }) => {
      let appleAuthorizationCode: string | null = null;
      const text = await readLimitedText(request, 4096);
      if (text.trim() !== "") {
        let body: unknown;
        try {
          body = JSON.parse(text);
        } catch {
          throw validationFailed("Body is not valid JSON");
        }
        if (!isObject(body)) throw validationFailed("Body must be a JSON object");
        rejectUnknownFields(body, ["appleAuthorizationCode"]);
        if (body.appleAuthorizationCode !== undefined && body.appleAuthorizationCode !== null) {
          if (
            typeof body.appleAuthorizationCode !== "string" ||
            !/^[A-Za-z0-9._-]{10,512}$/.test(body.appleAuthorizationCode)
          ) {
            throw validationFailed("appleAuthorizationCode is badly formed", 422);
          }
          appleAuthorizationCode = body.appleAuthorizationCode;
        }
      }
      await accounts.requestDeletion(user.userId, appleAuthorizationCode);
      wakeWorker();
      return emptyResponse(202);
    }),
  );
  router.add(
    "GET",
    "/v1/subscription",
    user(async ({ user }) => jsonResponse(await subscriptions.present(user.userId))),
  );

  // Public, webhooks, internal -----------------------------------------------------------------------------------------
  router.add("GET", "/health", {
    access: "public",
    handler: async ({ request }) => {
      const detailed = secretsMatch(request.headers.get("x-jauge-webhook-secret"), configuration.webhookSecret);
      const report = await operations.health(detailed);
      return jsonResponse(report, report.status === "down" ? 503 : 200);
    },
  });
  router.add("GET", "/public/analyses/:id", {
    access: "public",
    handler: async ({ parameters }) => {
      const view = await shares.publicView(resourceIdentifier(parameters.id, "Shared analysis"));
      return jsonResponse(view, 200, {
        "Access-Control-Allow-Origin": PUBLIC_ORIGIN,
        "Cache-Control": "public, max-age=300",
      });
    },
  });
  router.add("POST", "/webhooks/revenuecat", {
    access: "webhook",
    handler: async ({ request }) => {
      const body = await readJsonObject(request, 64 * 1024);
      const outcome = await revenueCat.handle(body);
      return jsonResponse({ status: outcome });
    },
  });
  router.add("POST", "/internal/worker", {
    access: "internal",
    // Answers at once and works in the background: pg_net gives up waiting after 5 s, the work goes on (50 s budget).
    handler: () => {
      dependencies.runInBackground(
        worker.run().catch((error) => logger.log("error", "worker_failed", errorDetails(error))),
      );
      return Promise.resolve(jsonResponse({ status: "started" }, 202));
    },
  });
  router.add("POST", "/internal/maintenance", {
    access: "internal",
    handler: async () => jsonResponse(await operations.runMaintenance()),
  });

  async function clientSubject(request: Request): Promise<string> {
    const throughProxy = secretsMatch(request.headers.get("x-jauge-proxy-secret"), configuration.proxySecret);
    const address = (throughProxy ? request.headers.get("x-jauge-client-ip") : null) ??
      request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ??
      request.headers.get("x-real-ip") ??
      "unknown";
    return await hashAddress(address, configuration.ipHashSalt);
  }

  async function authenticate(request: Request): Promise<AuthenticatedUser> {
    const header = request.headers.get("authorization") ?? "";
    const match = /^Bearer\s+([A-Za-z0-9_\-.]+)$/.exec(header);
    if (!match) throw new ApiError("unauthorized", "Missing bearer token");
    const authenticated = await dependencies.tokenVerifier.verify(match[1]);
    if (!authenticated || !UUID_PATTERN.test(authenticated.userId)) {
      throw new ApiError("unauthorized", "Invalid or expired token");
    }
    return authenticated;
  }

  async function dispatch(
    request: Request,
    url: URL,
    path: string,
    trace: { template: string },
  ): Promise<{ response: Response; template: string }> {
    if (request.method === "OPTIONS" && path.startsWith("/public/")) {
      return {
        template: "OPTIONS /public",
        response: new Response(null, {
          status: 204,
          headers: {
            "Access-Control-Allow-Origin": PUBLIC_ORIGIN,
            "Access-Control-Allow-Methods": "GET",
            "Access-Control-Max-Age": "86400",
          },
        }),
      };
    }
    const lookup = router.lookup(request.method, path);
    if (lookup.kind === "not_found") throw notFound("Route");
    if (lookup.kind === "method_not_allowed") {
      throw new ApiError("validation_failed", "Method not allowed", { status: 405 });
    }
    const { route, match } = lookup;
    const entry = route.handler;
    const template = `${route.method} ${match.template}`;
    trace.template = template;
    let authenticated = ANONYMOUS_USER;
    if (entry.access === "internal") {
      if (!secretsMatch(request.headers.get("x-jauge-webhook-secret"), configuration.webhookSecret)) {
        throw new ApiError("unauthorized", "Internal route");
      }
    } else if (entry.access === "webhook") {
      if (!secretsMatch(request.headers.get("authorization"), configuration.revenueCatWebhookAuthorization)) {
        throw new ApiError("unauthorized", "Webhook authorization does not match");
      }
    } else {
      const subject = await clientSubject(request);
      if (entry.access === "public") {
        if (path !== "/health") await rateLimiter.hit(RATE_LIMITS.publicPerMinute, subject);
      } else {
        if (configuration.maintenance) {
          throw new ApiError("maintenance", "Planned maintenance", { retryAfterSeconds: 300 });
        }
        await rateLimiter.hit(RATE_LIMITS.ipPerMinute, subject);
        authenticated = await authenticate(request);
        await rateLimiter.hit(RATE_LIMITS.userPerMinute, authenticated.userId);
      }
    }
    const response = await entry.handler({
      request,
      url,
      parameters: match.parameters,
      language: languageFromHeader(request.headers.get("accept-language")),
      user: authenticated,
    });
    return { response, template };
  }

  async function handle(request: Request): Promise<Response> {
    const startedAt = Date.now();
    const requestId = crypto.randomUUID();
    const url = new URL(request.url);
    const path = stripFunctionPrefix(url.pathname);
    let response: Response;
    const trace = { template: "unknown" };
    try {
      const result = await dispatch(request, url, path, trace);
      response = result.response;
      trace.template = result.template;
    } catch (error) {
      if (error instanceof ApiError) {
        response = errorResponse(error);
      } else {
        logger.log("error", "unhandled_error", { requestId, ...errorDetails(error) });
        response = errorResponse(new ApiError("internal", "Internal error"));
      }
    }
    response.headers.set("X-Request-Id", requestId);
    logger.log(response.status >= 500 ? "error" : "info", "request", {
      requestId,
      method: request.method,
      route: trace.template,
      status: response.status,
      durationMs: Date.now() - startedAt,
    });
    return response;
  }

  return { handle, worker, operations };
}
