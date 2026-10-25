import { type Application, createApplication } from "../application.ts";
import { FakeTokenVerifier } from "../authentication/token_verifier.ts";
import { type Configuration, loadConfiguration } from "../configuration.ts";
import { systemClock } from "../operations/clock.ts";
import { MemoryLogger } from "../operations/logger.ts";
import type { Providers } from "../providers/provider_types.ts";
import type { PipelineSettings } from "../queue/analysis_pipeline.ts";
import { createSimulatedProviders, RecordingAccountServices } from "../providers/simulated_providers.ts";
import { createTestDatabase, type TestDatabase } from "./test_database.ts";

/** Secret of the internal routes in the tests. */
export const TEST_WEBHOOK_SECRET = "test-webhook-secret-test-webhook-secret";
/** Authorization header of the RevenueCat webhook in the tests. */
export const TEST_REVENUECAT_AUTHORIZATION = "Bearer revenuecat-test-authorization";

/** An API wired to an in-memory database and simulated providers. */
export interface TestApplication {
  readonly application: Application;
  readonly test: TestDatabase;
  readonly accountServices: RecordingAccountServices;
  readonly logger: MemoryLogger;
  readonly configuration: Configuration;
  /** Sends a request as a user (or without token when `userId` is null). */
  request(
    method: string,
    path: string,
    options?: { userId?: string | null; body?: unknown; headers?: Record<string, string>; rawBody?: BodyInit },
  ): Promise<{ status: number; body: Record<string, unknown>; headers: Headers }>;
  /** Runs the worker until the queue is empty. */
  runWorker(): Promise<void>;
  /** Gives Jauge Plus to a user (as the RevenueCat webhook would). */
  makePlus(userId: string): Promise<void>;
  close(): Promise<void>;
}

/**
 * Builds a test API.
 *
 * @param options extra environment, other providers.
 */
export async function createTestApplication(options: {
  environment?: Record<string, string>;
  providers?: (accountServices: RecordingAccountServices) => Providers;
  pipelineSettings?: Partial<PipelineSettings>;
} = {}): Promise<TestApplication> {
  const test = await createTestDatabase();
  const accountServices = new RecordingAccountServices();
  const logger = new MemoryLogger();
  const environment: Record<string, string> = {
    JAUGE_ENVIRONMENT: "test",
    JAUGE_WEBHOOK_SECRET: TEST_WEBHOOK_SECRET,
    REVENUECAT_WEBHOOK_AUTHORIZATION: TEST_REVENUECAT_AUTHORIZATION,
    JAUGE_IP_HASH_SALT: "salt",
    ...options.environment,
  };
  const configuration = loadConfiguration((name) => environment[name]);
  const background: Promise<unknown>[] = [];
  const application = createApplication({
    configuration,
    database: test.database,
    tokenVerifier: new FakeTokenVerifier(),
    providers: options.providers ? options.providers(accountServices) : createSimulatedProviders(accountServices),
    clock: systemClock,
    logger,
    runInBackground: (work) => background.push(work),
    pipelineSettings: {
      collectionPollSeconds: 0,
      snapshotWaitSeconds: 0,
      offersPerSource: 60,
      computeMarginSeconds: 15,
      thinMarketOffers: 25,
      ...options.pipelineSettings,
    },
    workerSettings: {
      batchSize: 4,
      leaseSeconds: 60,
      budgetMilliseconds: 20_000,
      maximumStagesPerClaim: 12,
      maximumIdleMilliseconds: 0,
    },
    sleep: () => Promise.resolve(),
  });
  return {
    application,
    test,
    accountServices,
    logger,
    configuration,
    async request(method, path, requestOptions = {}) {
      const headers = new Headers(requestOptions.headers ?? {});
      if (requestOptions.userId) headers.set("Authorization", `Bearer test-token.${requestOptions.userId}`);
      let body: BodyInit | undefined = requestOptions.rawBody;
      if (requestOptions.body !== undefined) {
        headers.set("Content-Type", "application/json");
        body = JSON.stringify(requestOptions.body);
      }
      const response = await application.handle(new Request(`https://api.jauge.io${path}`, { method, headers, body }));
      const text = await response.text();
      return { status: response.status, body: text ? JSON.parse(text) : {}, headers: response.headers };
    },
    async runWorker() {
      await Promise.all(background.splice(0));
      for (let round = 0; round < 10; round++) {
        await application.worker.run();
        const due = await test.database.query(`select 1 from public.jobs where status in ('queued', 'running')`);
        if (due.length === 0) return;
      }
    },
    async makePlus(userId) {
      await test.database.query(
        `insert into public.subscriptions (user_id, status, expires_at, will_renew) values ($1::uuid, 'active', now() + interval '30 days', true)
         on conflict (user_id) do update set status = 'active', expires_at = now() + interval '30 days'`,
        [userId],
      );
    },
    async close() {
      await Promise.all(background.splice(0));
      await test.close();
    },
  };
}
