// Jauge API: single Edge Function that serves the whole contract of docs/openapi.yaml (routes under /v1), the
// health check, the public view of shared analyses, the RevenueCat webhook and the internal routes woken by the
// database (worker of the queue, daily maintenance). See docs/backend/ARCHITECTURE.md.

import { createApplication } from "./src/application.ts";
import { FakeTokenVerifier, SupabaseTokenVerifier, type TokenVerifier } from "./src/authentication/token_verifier.ts";
import { loadConfiguration } from "./src/configuration.ts";
import { PostgresDatabase } from "./src/database/postgres_database.ts";
import { systemClock } from "./src/operations/clock.ts";
import { JsonLogger } from "./src/operations/logger.ts";
import { createProviders } from "./src/providers/provider_registry.ts";

declare const EdgeRuntime: { waitUntil(promise: Promise<unknown>): void } | undefined;

const configuration = loadConfiguration((name) => Deno.env.get(name));
// deno-lint-ignore no-console
const logger = new JsonLogger((line) => console.log(line));

if (!configuration.databaseUrl) throw new Error("SUPABASE_DB_URL is missing");
const tokenVerifier: TokenVerifier = configuration.supabaseUrl
  ? new SupabaseTokenVerifier(configuration.supabaseUrl, configuration.legacyJwtSecret)
  : configuration.environment === "development"
  ? new FakeTokenVerifier()
  : (() => {
    throw new Error("SUPABASE_URL is missing");
  })();

const providers = createProviders(configuration);
if (configuration.environment === "production" && providers.mode === "simulated") {
  logger.log("warning", "simulated_providers_in_production", {});
}

const application = createApplication({
  configuration,
  database: PostgresDatabase.connect(configuration.databaseUrl),
  tokenVerifier,
  providers,
  clock: systemClock,
  logger,
  runInBackground: (work) => {
    if (typeof EdgeRuntime !== "undefined") EdgeRuntime.waitUntil(work);
  },
});

Deno.serve((request) => application.handle(request));
