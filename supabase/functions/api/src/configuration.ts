/**
 * Configuration of the API, read once from the environment (Supabase secrets). No value has a secret default: a
 * missing secret disables what depends on it (simulated providers, refused webhooks), it never weakens a check.
 */
export interface Configuration {
  /** `production`, `development` or `test`. */
  readonly environment: "production" | "development" | "test";
  /** URL of the Supabase project (`https://<ref>.supabase.co`). */
  readonly supabaseUrl: string | null;
  /** Service role key (Auth admin calls). */
  readonly serviceRoleKey: string | null;
  /** Connection string of the database (provided by Supabase to the functions). */
  readonly databaseUrl: string | null;
  /** HS256 secret of the legacy JWT of the project, when the project still signs tokens with it. */
  readonly legacyJwtSecret: string | null;
  /** Secret shared with the database (pg_net) for the internal routes. */
  readonly webhookSecret: string | null;
  /** Secret shared with the Cloudflare Worker: when it matches, the `X-Jauge-Client-Ip` header it adds is trusted. */
  readonly proxySecret: string | null;
  /** Salt of the hashes of IP addresses used by the rate limits. */
  readonly ipHashSalt: string;
  /** `simulated` (deterministic fake providers) or `live` (Apify, AI, social search). */
  readonly providerMode: "simulated" | "live";
  /** Money the paid providers may spend per UTC day, in US dollars. Over it, only the cache and the database serve. */
  readonly dailySpendLimitUsd: number;
  /** Share of the daily limit at which a warning is sent. */
  readonly spendWarningRatio: number;
  /** Free complete analyses per week (Monday, Paris time). */
  readonly freeAnalysesPerWeek: number;
  /** Fair use limit of Jauge Plus: analyses per day. */
  readonly plusAnalysesPerDay: number;
  /** Days an offer snapshot is reused (7 by default). */
  readonly cacheDays: number;
  /** Seconds an analysis may take before it is completed with the data at hand. */
  readonly analysisDeadlineSeconds: number;
  /** When true, every `/v1` route answers `503 maintenance`. */
  readonly maintenance: boolean;
  /** When true, the API runs the worker in the background after creating a job (no pg_net needed). */
  readonly inlineWorker: boolean;
  /** Optional URL that receives cost and health alerts (ntfy, Slack incoming webhook...). */
  readonly alertWebhookUrl: string | null;
  /** Value of the Authorization header that RevenueCat sends with its webhooks. */
  readonly revenueCatWebhookAuthorization: string | null;
  /** Secret API key of RevenueCat (`sk_...`), server side only. */
  readonly revenueCatSecretApiKey: string | null;
  /** Sign in with Apple: team, client (bundle identifier), key identifier and private key (.p8). */
  readonly apple: {
    readonly teamId: string | null;
    readonly clientId: string | null;
    readonly keyId: string | null;
    readonly privateKey: string | null;
  };
  /** Version of the API, shown by the health check. */
  readonly version: string;
}

/** Reads environment variables (injected so that tests do not depend on the process). */
export type EnvironmentReader = (name: string) => string | undefined;

/**
 * Builds the configuration from the environment.
 *
 * @param read reads one variable (`Deno.env.get` in production).
 */
export function loadConfiguration(read: EnvironmentReader): Configuration {
  const text = (name: string): string | null => {
    const value = read(name)?.trim();
    return value ? value : null;
  };
  const number = (name: string, defaultValue: number, minimum: number, maximum: number): number => {
    const value = Number(text(name) ?? defaultValue);
    if (!Number.isFinite(value)) return defaultValue;
    return Math.min(Math.max(value, minimum), maximum);
  };
  const flag = (name: string, defaultValue: boolean): boolean => {
    const value = text(name)?.toLowerCase();
    if (value === undefined || value === null) return defaultValue;
    return value === "true" || value === "1" || value === "yes";
  };
  const environmentName = text("JAUGE_ENVIRONMENT");
  const environment = environmentName === "development" || environmentName === "test" ? environmentName : "production";
  const webhookSecret = text("JAUGE_WEBHOOK_SECRET");
  return {
    environment,
    supabaseUrl: text("SUPABASE_URL"),
    serviceRoleKey: text("SUPABASE_SERVICE_ROLE_KEY"),
    databaseUrl: text("SUPABASE_DB_URL"),
    legacyJwtSecret: text("SUPABASE_JWT_SECRET"),
    webhookSecret: webhookSecret && webhookSecret.length >= 32 ? webhookSecret : null,
    proxySecret: text("JAUGE_PROXY_SECRET"),
    ipHashSalt: text("JAUGE_IP_HASH_SALT") ?? "",
    providerMode: text("JAUGE_PROVIDER_MODE") === "live" ? "live" : "simulated",
    dailySpendLimitUsd: number("JAUGE_DAILY_SPEND_LIMIT_USD", 3, 0, 1000),
    spendWarningRatio: number("JAUGE_SPEND_WARNING_RATIO", 0.8, 0.1, 1),
    freeAnalysesPerWeek: Math.round(number("JAUGE_FREE_ANALYSES_PER_WEEK", 1, 0, 100)),
    plusAnalysesPerDay: Math.round(number("JAUGE_PLUS_ANALYSES_PER_DAY", 30, 1, 1000)),
    cacheDays: number("JAUGE_CACHE_DAYS", 7, 1, 60),
    analysisDeadlineSeconds: number("JAUGE_ANALYSIS_DEADLINE_SECONDS", 75, 20, 600),
    maintenance: flag("JAUGE_MAINTENANCE", false),
    inlineWorker: flag("JAUGE_INLINE_WORKER", false),
    alertWebhookUrl: text("JAUGE_ALERT_WEBHOOK_URL"),
    revenueCatWebhookAuthorization: text("REVENUECAT_WEBHOOK_AUTHORIZATION"),
    revenueCatSecretApiKey: text("REVENUECAT_SECRET_API_KEY"),
    apple: {
      teamId: text("APPLE_TEAM_ID"),
      clientId: text("APPLE_CLIENT_ID"),
      keyId: text("APPLE_KEY_ID"),
      privateKey: text("APPLE_PRIVATE_KEY_P8"),
    },
    version: text("JAUGE_API_VERSION") ?? "1.0.0",
  };
}
