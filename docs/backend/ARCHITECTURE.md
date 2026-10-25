# Jauge backend: architecture

Read this before touching the API. The contract is `docs/api.md` and `docs/openapi.yaml`; the estimation is explained
in `docs/backend/score.md`; the sources of offers (Apify, page reading, costs) in `docs/backend/sources.md`; putting it
online is `docs/backend/DEPLOIEMENT.md` (in French, for the owner).

## Choices

| Need | Choice | Why |
|---|---|---|
| Database, auth, push | **Supabase Postgres 17** (project `ynvaaswsenerrummanrs`) | Already used by the app (auth, devices, notifications); free plan to start, Pro later without code change. |
| API | **One Edge Function `api`** (TypeScript, Deno 2), a small router | Same platform as the database (no extra host, no extra bill), cold start of a few hundred ms, one deployment. |
| Long work (Apify runs take minutes) | **Work queue in Postgres** (`jobs`), woken by **pg_net** and **pg_cron** | No HTTP request ever waits for a source; retries, leases and deadlines survive crashes. No Redis, no paid queue. |
| Public URL | **Cloudflare Worker** `api.jauge.io` -> Supabase function URL | Free (100 000 requests per day), stable URL, hides internal routes, gives the client address to the rate limits. |
| Database driver | `postgres` (postgres.js) on `SUPABASE_DB_URL`, prepared statements off | Real SQL, transactions and `FOR UPDATE SKIP LOCKED`; works through the Supavisor pooler. |
| Tests | Deno test + **PGlite** (Postgres 17 compiled to WebAssembly) | Every migration, RLS policy and SQL query is tested for real, in memory, without Docker. |

## Components

```
app (Android, iOS)                     RevenueCat              jauge.io/a/{id} (web page)
   | Bearer <Supabase access token>        | webhook                 | GET /public/analyses/{id}
   v                                       v                         v
Cloudflare Worker  api.jauge.io  (cloudflare/api-proxy: allow list of routes, X-Jauge-Client-Ip + proxy secret)
   |
   v
Edge Function "api"  (supabase/functions/api)
   index.ts -> src/application.ts : router, authentication (JWKS), rate limits, validation, typed errors, logs
   src/services/*   analyses, insights, alerts, shares, account, subscription, RevenueCat webhook
   src/queue/*      worker (claims jobs) + analysis pipeline (stages)
   src/scoring/*    score, applicants, salary, trend, twins, skills, timing (pure functions)
   src/providers/*  interfaces + simulated providers + live account services (RevenueCat, Apple, Supabase Auth)
                    apify/ (client, actor catalog, market sources) + offers/ (page and JSON-LD reader, parsers)
   src/operations/* spend guard, rate limiter, health, maintenance, structured logger
   |
   v
Postgres (supabase/migrations)
   user tables       profiles, analyses, analysis_results, analysis_inputs (24 h), favorites, applicant_feedback,
                     alerts, alert_matches, shares, weekly_quotas, subscriptions, revenuecat_events
   cache and market  offers (normalized), offer_snapshots (7 day cache), market_slices (7 day market collections),
                     source_collections, collected_offers, snapshot_twins, market_statistics
   operations        jobs, provider_costs, spend_alerts, rate_limit_counters
   from phase 1      devices, notifications (+ trigger -> Edge Function send-notification)
   pg_net            jobs insert -> POST /internal/worker      pg_cron  every 15 s: wake the worker if jobs are due
                                                                         hourly: api_purge_expired()
                                                                         daily: POST /internal/maintenance (alerts)
```

Routes (all in `src/application.ts`): the 22 operations of the contract under `/v1`, plus `GET /health`,
`GET /public/analyses/{id}`, `POST /webhooks/revenuecat`, `POST /webhooks/apify` (header `x-jauge-apify-secret`, called
by Apify at the end of a run, directly on the Supabase URL), and `POST /internal/worker`, `POST /internal/maintenance`
(header `x-jauge-webhook-secret`, never exposed by the proxy). A test (`src/contract_test.ts`) reads
`docs/openapi.yaml` and fails if an operation is not routed.

## Life of an analysis

```
POST /v1/analyses (link | manual | text | image)
  1. validation (strict: unknown fields refused, sizes, link syntax, image type sniffed from its bytes)
  2. Idempotency-Key or same input within 10 min -> the existing analysis (no quota used)
  3. one transaction: plan (Plus: fair use 30 per day; free: api_consume_weekly_quota, 1 per Paris week)
                      insert analyses (status processing, 4 steps) + analysis_inputs (text or image, 24 h max)
                      insert jobs (analyze_offer, deadline = now + 75 s)
     -> trigger: pg_net POST /internal/worker (or JAUGE_INLINE_WORKER: run after the answer)
  4. 202 + Analysis (processing). The app polls GET /v1/analyses/{id} every second.

worker (POST /internal/worker, answers 202 at once and works up to 50 s in the background): api_claim_jobs (SKIP LOCKED, lease 90 s), runs stages, saves the stage after each one
  read_offer    link: canonical URL (tracking removed, job identifier kept) -> key link:sha256
                manual: job family + city -> key manual:sha256 ; text/image: AI extraction -> key text:sha256
                input deleted right after reading. Unreadable / unsupported -> failed + free analysis given back.
                Steps "Poste et lieu", "Compétences" done; publishedDaysAgo, hasDisplayedSalary visible to the app.
  cache         offer_snapshots ready and not expired (7 days) -> finalize at once (from_cache)
                another analysis computing the same offer -> wait_snapshot (no second collection)
  market        market_slices (job family x department) ready and not expired (7 days) -> reuse
                otherwise start the main sources (LinkedIn, HelloWork) under the daily spending limit; the estimate
                of each run (also its cap on Apify) is reserved until the run ends
  market_wait   poll the runs every 5 s (the Apify webhook wakes the job at once); store the offers
                (collected_offers, upsert), journal the real cost; fewer than 25 offers -> start the complementary
                sources (Indeed) once; stop waiting 15 s before the deadline and go on with the data at hand
                (partial: lower reliability, slice kept 1 day); late runs are stored by the webhook or the maintenance
  social        reputation of the company: 30 day cache per company, else social search (Reddit, X through Apify)
                + AI summary in French and English (internal context of the advice, never sent to the app, never
                used by the score; never blocks the analysis, docs/backend/ia.md)
  compute       comparables (same family and department, 12 months) + market_statistics history
                -> score, applicants, reliability, salary scale, probable salary, trend, skills, timing, twins
                -> offer_snapshots ready (7 days, 1 day when partial), snapshot_twins, market_statistics
  finalize      analysis_results (offer + result frozen for the history, twins counted in the user's radius,
                advice written by the AI from the computed facts and the reputation as internal context; the
                reputation itself is not stored in the result),
                status completed, progress 100, search_text for the list search
errors          retryable (network, 5xx, database) -> backoff 2^attempt s (max 60 s), up to 6 attempts and the
                deadline; then giveUp: analysis failed "internal", free analysis given back, snapshot released
```

Polling: the app reads `status`, `progressPercent`, `steps`. The worker writes them after each stage, so the
screen "Lecture" moves while sources are collected. A cached offer often answers `completed` in the 202 itself.

## Cache, deduplication, idempotency

- **Offer cache (7 days)**: key = normalized offer (`offers.normalized_key`). Same LinkedIn job with other tracking
  parameters, `http` or `https`, `www` or not: same key. Manual entries use job family + city.
- **Market cache (7 days)**: one collection per job family and department serves every offer of that market. This
  is the main saving: most analyses only cost the reading of the offer.
- At most one collection at a time per offer and per market (partial unique indexes); abandoned ones (10 min) are
  released.
- `Idempotency-Key` header (24 h) and double taps (same input, 10 min) return the first analysis.
- Jobs have a unique `idempotency_key` (`analysis:<id>`, `delete_account:<user>`).

## Quotas, limits, plans

| Limit | Value | Where |
|---|---|---|
| Free analyses | 1 per week (Monday 00:00 Paris), unreadable / unsupported / failed ones given back | `weekly_quotas`, `api_consume_weekly_quota` |
| Jauge Plus fair use | 30 analyses per 24 h | `JAUGE_PLUS_ANALYSES_PER_DAY` |
| Calm alerts | Plus only (402 for free users), 20 per user, 3 pushes per week | `AlertService`, send-notification |
| Requests per IP (hashed) | 300 per minute (public routes: 60) | `api_hit_rate_limit` |
| Requests per user | 240 per minute (polling is 60) | idem |
| New analyses per user | 6 per minute | idem |
| Daily spending | `JAUGE_DAILY_SPEND_LIMIT_USD` (3 USD by default) | `SpendGuard` |

Over the daily limit, paid providers are skipped (`source_collections.status = skipped`) and analyses are computed
from the cache and the market data already in the database, with a lower reliability. The owner gets one message at
80 % and one at 100 % (`JAUGE_ALERT_WEBHOOK_URL`, ntfy or Slack). Simulated costs are journaled but never count.

## Security and privacy

- Authentication: Supabase access token verified locally (JWKS of the project, issuer, audience, expiry; legacy
  HS256 secret supported). Write routes refuse a user whose account deletion has started.
- RLS enabled on every table; the app (anon, authenticated) can only `select` its own rows of the user tables and
  nothing else. All writes go through the API (table owner). Every `api_*` function is `SECURITY DEFINER`, has
  `search_path = ''` and is executable by `service_role` only (checked by `src/database/migrations_test.ts`).
- Identifiers: analyses are random UUIDs (the public link `jauge.io/a/{analysisId}` only works while a share exists),
  shares have 128 random bits.
- Inputs: images and texts are kept until they are read (seconds), 24 hours at most (`analysis_inputs`, purge).
  The text read on the phone (`type: "text"`) is the default way: the image never leaves the phone.
- Location: a city and a department only, never a position.
- Logs: one JSON line per request (`requestId`, route template, status, duration) and per job step. No user
  identifier, no IP address (hashed with a salt for the rate limits, never logged), no offer text, no query string.
- Internal routes need `x-jauge-webhook-secret` (constant time comparison) and are not reachable through the proxy.
  The RevenueCat webhook needs the Authorization value configured in RevenueCat (RevenueCat's own mechanism: a
  shared header, compared in constant time; events applied once and in order).
- Account deletion: one transaction deletes every row (`api_delete_user_data`), then a job deletes the RevenueCat
  customer, revokes Sign in with Apple (with a fresh authorization code sent by the app) and deletes the auth user,
  with retries and an alert to the owner if it cannot finish. The Apple code is wiped from the job when it ends.

## Providers (interfaces in `src/providers/provider_types.ts`)

| Interface | Simulated (tests, `JAUGE_PROVIDER_MODE=simulated`) | Live (phase 2) |
|---|---|---|
| `OfferReader.readLink`, `readText` | mockup offer, `unreadable` / `unsupported` in the URL simulate failures | `LiveOfferReader`: JSON-LD `JobPosting` of the page (free), Apify actor of the site when the page refuses (Indeed), AI on the visible text otherwise; plain facts of the text fill what the AI left empty (`docs/backend/sources.md`) |
| `OfferSource` (start, poll) | LinkedIn (asynchronous run, shows applicants), Hellowork | `ApifyOfferSource`: LinkedIn Jobs Scraper (applicant counts, the anchors) and HelloWork for every new market, Indeed for thin markets, Glassdoor and Welcome to the Jungle off by default |
| `SocialSearch` | a few posts per company | `ApifySocialSearch`: Reddit (`trudax/reddit-scraper-lite`) and X (Apify pay per result actor), see `docs/backend/ia.md` |
| `AiProvider` | rules and templates | `LanguageModelAiProvider` on Gemini 3.1 Flash-Lite (default) or Mistral Small 4 (EU), `AI_PROVIDER` |
| `SubscriptionStore`, `AppleTokenRevoker`, `AuthAdministration`, `OperationsAlerter` | recording fakes | already implemented (`live_account_services.ts`), on as soon as their secrets exist |

Connecting a live provider is one line in `createProviders` (`src/providers/provider_registry.ts`, `LiveConnectors`).
The offer reader and the Apify sources are built from the configuration by `createOfferConnectors`. Only
`JAUGE_PROVIDER_MODE=live` turns the live providers on; keys alone never do.
Rules for connectors: return `ProviderError(retryable)` for network and 5xx errors, `ProviderError(not retryable)`
for configuration errors; report the real cost of each call in micro dollars; never log the content.

## Costs

Prices to check (October 2026 public prices, may change): Apify pay per event actors on the Starter plan, 1 USD per
1 000 LinkedIn offers, 0.8 USD per 1 000 HelloWork offers, 5 USD per 1 000 Indeed offers (3 USD on Business), detail
in `docs/backend/sources.md`; Gemini 3.1 Flash-Lite 0.25 USD per million input tokens and 1.50 USD per million output
tokens (checked 25/10/2026, details in `docs/backend/ia.md`).

Per analysis:

| Case | What is paid | Estimate |
|---|---|---|
| Offer and market in cache | nothing | 0 USD |
| Offer new, market in cache (most common) | reading of the offer (page: free; Indeed link: 0.005; AI on a text: about 0.001) + social search and summary | 0 to 0.04 USD |
| Offer and market new | + market collection: LinkedIn 60 offers (0.060) + HelloWork 60 (0.048), Indeed 30 (0.15) only when thin | 0.05 to 0.20 USD |

Per month for 1 000 users (assumptions: 40 % active each week, 1.5 analyses per active user per week, so about
2 600 analyses; about 1 500 market collections as the markets repeat, offers reused 15 %):

| Item | Estimate per month |
|---|---|
| Market collections (Apify) | about 210 USD, capped by the daily limit (3 USD per day = 90 USD per month by default) |
| AI (extraction, advice, reputation) | 3 to 5 USD |
| Social search | 20 to 40 USD (one search per company every 30 days; under 5 USD with X only) |
| Supabase | 0 USD (free) then 25 USD (Pro, recommended at launch for backups and 8 GB) |
| Cloudflare Worker, pg_cron, pg_net | 0 USD |
| Apify plan | Starter 39 USD per month includes 39 USD of usage |

The daily limit is the safety net: the bill cannot go over `JAUGE_DAILY_SPEND_LIMIT_USD` x 30 for the sources, AI
and social search together; past it, Jauge still answers from the cache and the database.

## Free plan of Supabase and how we stay in it

| Limit (free plan) | Our use for 1 000 users | How |
|---|---|---|
| 500 000 Edge Function invocations per month | about 150 000 (polling about 25 per analysis, screens, worker wakes) | worker woken only when jobs are due; cache hits complete without polling |
| 500 MB database | market data grows about 60 to 100 MB per month | `collected_offers` purged after 6 months, history kept in `market_statistics`, inputs after 24 h, jobs after 14 days |
| 150 s per invocation, 2 s CPU | worker budget 50 s, stages short, waits are I/O | long collections are polled across invocations, never awaited |
| 5 GB egress | JSON answers of a few KB | images are not served by the API |
| Pause after 7 days without activity, no backup | pg_cron keeps it busy | move to Pro (25 USD) before the public launch for daily backups |
| 60 direct connections | 3 per isolate, pooler URL recommended | `SUPABASE_DB_URL` with Supavisor transaction mode |

When the database nears 400 MB or invocations near 400 000 per month: Pro plan, no code change.

## For the next phases

- **AI with Reddit and X** (phase 2): implement `AiProvider` (structured JSON output for the offer, short French or
  English summary for the reputation) and `SocialSearch`; keep the cost per call in the result. Cache the reputation
  per company (30 days) to cut the cost.
- **App wiring** (phase 2): send `type: "text"` after ML Kit / Vision OCR (the image path stays as a fallback),
  `Idempotency-Key` on creation, send `appleAuthorizationCode` on deletion for Apple
  accounts, slow the polling to every 2 s after 10 s, set `jauge.api.offlineMode=false`.
- Later: share card images (`imageUrl` is null for now, the app draws the card), monthly calibration of the error
  with `applicant_feedback`, update of the privacy policy (Apify, the AI provider as subprocessors).
