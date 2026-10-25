-- Jauge API: tables of the analyses, their cache, the market data, the user features and the operations.
--
-- Every table has row level security enabled. The app never writes these tables directly: every write goes through
-- the Edge Function "api" (connected as the database owner). Signed in users may only read their own rows of the
-- user tables (useful for Realtime later); anonymous visitors see nothing. Global tables (offers, market data, cache,
-- queue, costs, counters) are not readable by the app at all.
--
-- Conventions: salaries in thousands of euros (annual gross), money in micro US dollars (bigint), dates in UTC.
-- See docs/backend/ARCHITECTURE.md.

-- Profiles -----------------------------------------------------------------------------------------------------------

create table public.profiles (
    user_id uuid primary key references auth.users (id) on delete cascade,
    marketing_emails boolean not null default false,
    marketing_emails_updated_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

comment on table public.profiles is 'Settings of each user known by the API (consents). Created on the first API call.';

-- Normalized offers (global, no personal data) ----------------------------------------------------------------------

create table public.offers (
    id uuid primary key default gen_random_uuid(),
    normalized_key text not null unique check (normalized_key ~ '^(link|manual|text):[0-9a-f]{64}$'),
    key_kind text not null check (key_kind in ('link', 'manual', 'text')),
    source_site text check (source_site is null or char_length(source_site) <= 40),
    canonical_url text check (canonical_url is null or char_length(canonical_url) <= 2048),
    job_title text check (job_title is null or char_length(job_title) <= 200),
    job_family text check (job_family is null or char_length(job_family) <= 120),
    company text check (company is null or char_length(company) <= 200),
    city text check (city is null or char_length(city) <= 120),
    department_code text check (department_code is null or department_code ~ '^([0-9]{2}|2[AB]|9[78][0-9])$'),
    contract_type text check (contract_type is null or contract_type in
        ('permanent', 'fixed_term', 'apprenticeship', 'internship', 'freelance', 'temporary', 'other')),
    work_mode text check (work_mode is null or char_length(work_mode) <= 80),
    published_on date,
    displayed_salary_minimum integer check (displayed_salary_minimum is null or displayed_salary_minimum between 1 and 1000),
    displayed_salary_maximum integer check (displayed_salary_maximum is null or displayed_salary_maximum between 1 and 1000),
    displayed_applicant_count integer check (displayed_applicant_count is null or displayed_applicant_count >= 0),
    skills text[] not null default '{}' check (cardinality(skills) <= 40),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

comment on table public.offers is 'Job offers met by the API, one row per normalized offer (key = hash of the canonical link or of title and city).';

-- 7 day cache: one snapshot per computation of an offer ----------------------------------------------------------

create table public.offer_snapshots (
    id uuid primary key default gen_random_uuid(),
    offer_id uuid not null references public.offers (id) on delete cascade,
    status text not null check (status in ('collecting', 'ready', 'failed')),
    degraded boolean not null default false,
    result jsonb,
    insights jsonb,
    comparable_offer_count integer not null default 0,
    salary_source_count integer not null default 0,
    computed_at timestamptz,
    expires_at timestamptz,
    created_at timestamptz not null default now(),
    check (status <> 'ready' or (result is not null and computed_at is not null and expires_at is not null))
);

comment on table public.offer_snapshots is 'Cache of 7 days: result of the collection and of the computation of an offer, shared by every user who asks for it.';

create index offer_snapshots_ready_index on public.offer_snapshots (offer_id, expires_at desc) where status = 'ready';
-- At most one collection at a time per offer: a second request waits for the running one.
create unique index offer_snapshots_one_collecting_index on public.offer_snapshots (offer_id) where status = 'collecting';

-- Market slices: collections of the market of one job family in one department, shared for 7 days -------------

create table public.market_slices (
    id uuid primary key default gen_random_uuid(),
    job_family text not null check (char_length(job_family) between 1 and 120),
    department_code text not null check (department_code ~ '^([0-9]{2}|2[AB]|9[78][0-9])$'),
    status text not null check (status in ('collecting', 'ready', 'failed')),
    collected_at timestamptz,
    expires_at timestamptz,
    created_at timestamptz not null default now(),
    check (status <> 'ready' or (collected_at is not null and expires_at is not null))
);

comment on table public.market_slices is 'Collections of the market (job family x department), reused by every offer of the slice for 7 days: the main cost saving.';

create index market_slices_ready_index on public.market_slices (job_family, department_code, expires_at desc) where status = 'ready';
create unique index market_slices_one_collecting_index on public.market_slices (job_family, department_code) where status = 'collecting';

-- Collected sources: one row per provider call of a market slice ------------------------------------------------

create table public.source_collections (
    id uuid primary key default gen_random_uuid(),
    slice_id uuid not null references public.market_slices (id) on delete cascade,
    provider text not null check (char_length(provider) between 1 and 40),
    status text not null check (status in ('pending', 'running', 'succeeded', 'failed', 'skipped')),
    external_run_id text check (external_run_id is null or char_length(external_run_id) <= 200),
    attempts integer not null default 0,
    item_count integer not null default 0,
    cost_micro_usd bigint not null default 0,
    error_code text check (error_code is null or char_length(error_code) <= 60),
    started_at timestamptz,
    finished_at timestamptz,
    created_at timestamptz not null default now(),
    unique (slice_id, provider)
);

comment on table public.source_collections is 'Sources collected for a market slice (Apify actors): status, run identifier, number of offers, cost.';

-- Market data: offers collected from the sources (global, public job offers) --------------------------------------

create table public.collected_offers (
    id uuid primary key default gen_random_uuid(),
    source text not null check (char_length(source) between 1 and 40),
    external_id text not null check (char_length(external_id) between 1 and 300),
    job_title text not null check (char_length(job_title) between 1 and 200),
    job_family text not null check (char_length(job_family) between 1 and 120),
    company text check (company is null or char_length(company) <= 200),
    city text check (city is null or char_length(city) <= 120),
    department_code text check (department_code is null or department_code ~ '^([0-9]{2}|2[AB]|9[78][0-9])$'),
    latitude double precision check (latitude is null or latitude between -90 and 90),
    longitude double precision check (longitude is null or longitude between -180 and 180),
    published_on date,
    salary_minimum integer check (salary_minimum is null or salary_minimum between 1 and 1000),
    salary_maximum integer check (salary_maximum is null or salary_maximum between 1 and 1000),
    applicant_count integer check (applicant_count is null or applicant_count >= 0),
    applicant_count_observed_at timestamptz,
    contract_type text check (contract_type is null or char_length(contract_type) <= 40),
    work_mode text check (work_mode is null or char_length(work_mode) <= 80),
    skills text[] not null default '{}' check (cardinality(skills) <= 40),
    url text check (url is null or char_length(url) <= 2048),
    first_seen_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    unique (source, external_id)
);

comment on table public.collected_offers is 'Public job offers collected by the sources: the market data behind scores, salaries, trends and twins.';

create index collected_offers_family_department_index on public.collected_offers (job_family, department_code, published_on desc);
create index collected_offers_last_seen_index on public.collected_offers (last_seen_at);

-- Twins of a snapshot (same job, nearby, with their own estimate) -------------------------------------------------

create table public.snapshot_twins (
    snapshot_id uuid not null references public.offer_snapshots (id) on delete cascade,
    collected_offer_id uuid not null references public.collected_offers (id) on delete cascade,
    estimated_applicants integer not null check (estimated_applicants >= 0),
    score integer not null check (score between 0 and 100),
    primary key (snapshot_id, collected_offer_id)
);

comment on table public.snapshot_twins is 'Twin offers of a snapshot (offres jumelles) with their estimated applicants and score.';

-- Monthly market statistics (salaries and applicants per offer, by job family and department) ---------------------

create table public.market_statistics (
    job_family text not null check (char_length(job_family) between 1 and 120),
    department_code text not null check (department_code ~ '^([0-9]{2}|2[AB]|9[78][0-9]|FR)$'),
    month date not null check (extract(day from month) = 1),
    offer_count integer not null default 0,
    applicants_per_offer integer,
    salary_offer_count integer not null default 0,
    salary_first_quartile integer,
    salary_median integer,
    salary_third_quartile integer,
    updated_at timestamptz not null default now(),
    primary key (job_family, department_code, month)
);

comment on table public.market_statistics is 'Salaries and applicants per offer by job family, department and month: history of the trend, fallback of the salary scale.';

-- Analyses of the users ------------------------------------------------------------------------------------------

create table public.analyses (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    input_kind text not null check (input_kind in ('link', 'manual', 'text', 'image')),
    input_origin text check (input_origin is null or input_origin in ('screenshot', 'photo')),
    input_url text check (input_url is null or char_length(input_url) <= 2048),
    manual_job_title text check (manual_job_title is null or char_length(manual_job_title) <= 200),
    manual_city text check (manual_city is null or char_length(manual_city) <= 120),
    area_city text check (area_city is null or char_length(area_city) <= 120),
    area_department_code text check (area_department_code is null or area_department_code ~ '^([0-9]{2}|2[AB]|9[78][0-9])$'),
    area_radius_kilometers integer check (area_radius_kilometers is null or area_radius_kilometers between 1 and 200),
    language text not null default 'fr' check (language in ('fr', 'en')),
    offer_id uuid references public.offers (id) on delete set null,
    snapshot_id uuid references public.offer_snapshots (id) on delete set null,
    status text not null default 'processing' check (status in ('processing', 'completed', 'failed')),
    progress_percent integer not null default 0 check (progress_percent between 0 and 100),
    steps jsonb not null default '[]',
    published_days_ago integer check (published_days_ago is null or published_days_ago >= 0),
    has_displayed_salary boolean,
    failure_code text check (failure_code is null or failure_code in ('unreadable_offer', 'unsupported_link', 'internal')),
    recognized_company text check (recognized_company is null or char_length(recognized_company) <= 200),
    quota_week date,
    from_cache boolean not null default false,
    search_text text not null default '' check (char_length(search_text) <= 600),
    idempotency_key text check (idempotency_key is null or idempotency_key ~ '^[A-Za-z0-9_-]{8,64}$'),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    completed_at timestamptz,
    unique (user_id, idempotency_key)
);

comment on table public.analyses is 'Analyses asked for by each user: input, search area, status, steps and progress polled by the app.';

create index analyses_user_created_index on public.analyses (user_id, created_at desc, id desc);
create index analyses_snapshot_index on public.analyses (snapshot_id) where status = 'processing';
create index analyses_offer_index on public.analyses (offer_id);

-- Result of an analysis, frozen at completion (the history stays the same when the cache changes).
create table public.analysis_results (
    analysis_id uuid primary key references public.analyses (id) on delete cascade,
    offer jsonb not null,
    result jsonb not null,
    created_at timestamptz not null default now()
);

comment on table public.analysis_results is 'Offer and result of a completed analysis, in the shapes of the API contract.';

-- Inputs in transit: image or text read on the phone, deleted after reading and at most after 24 hours.
create table public.analysis_inputs (
    analysis_id uuid primary key references public.analyses (id) on delete cascade,
    image bytea check (image is null or octet_length(image) <= 10485760),
    mime_type text check (mime_type is null or mime_type in ('image/jpeg', 'image/png', 'image/heic', 'image/heif', 'image/webp')),
    text_content text check (text_content is null or char_length(text_content) <= 20000),
    expires_at timestamptz not null default now() + interval '24 hours',
    check (image is not null or text_content is not null)
);

comment on table public.analysis_inputs is 'Image or text of an offer waiting to be read. Deleted once read, and after 24 hours at most.';

create index analysis_inputs_expires_index on public.analysis_inputs (expires_at);

-- Work queue ------------------------------------------------------------------------------------------------------

create table public.jobs (
    id uuid primary key default gen_random_uuid(),
    kind text not null check (kind in ('analyze_offer', 'delete_account', 'evaluate_alerts')),
    analysis_id uuid references public.analyses (id) on delete cascade,
    subject_user_id uuid,
    payload jsonb not null default '{}',
    stage text not null default 'start' check (char_length(stage) between 1 and 40),
    status text not null default 'queued' check (status in ('queued', 'running', 'succeeded', 'failed')),
    attempts integer not null default 0,
    max_attempts integer not null default 8 check (max_attempts between 1 and 50),
    run_after timestamptz not null default now(),
    lease_expires_at timestamptz,
    locked_by text check (locked_by is null or char_length(locked_by) <= 80),
    deadline_at timestamptz,
    last_error text check (last_error is null or char_length(last_error) <= 200),
    idempotency_key text unique check (idempotency_key is null or char_length(idempotency_key) <= 200),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    finished_at timestamptz
);

comment on table public.jobs is 'Work queue: collections and computations of the analyses, account deletions, alert evaluations. Leases, retries, deadlines.';

create index jobs_due_index on public.jobs (run_after) where status = 'queued';
create index jobs_lease_index on public.jobs (lease_expires_at) where status = 'running';

-- Favorites, feedback, alerts, shares ------------------------------------------------------------------------------

create table public.favorites (
    user_id uuid not null references auth.users (id) on delete cascade,
    analysis_id uuid not null references public.analyses (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, analysis_id)
);

comment on table public.favorites is 'Starred analyses of each user.';

create table public.applicant_feedback (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    analysis_id uuid not null references public.analyses (id) on delete cascade,
    offer_id uuid references public.offers (id) on delete set null,
    actual_applicants integer not null check (actual_applicants between 1 and 100000),
    estimated_applicants integer,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, analysis_id)
);

comment on table public.applicant_feedback is 'Real numbers of applicants given by the users: used each month to measure the error of the estimate.';

create table public.alerts (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    analysis_id uuid not null references public.analyses (id) on delete cascade,
    threshold integer not null default 30 check (threshold between 1 and 100),
    active boolean not null default true,
    last_evaluated_at timestamptz,
    created_at timestamptz not null default now(),
    unique (user_id, analysis_id)
);

comment on table public.alerts is 'Calm alerts: notify the user when a twin offer of the analysis goes under the threshold.';

create table public.alert_matches (
    alert_id uuid not null references public.alerts (id) on delete cascade,
    collected_offer_id uuid not null references public.collected_offers (id) on delete cascade,
    score integer not null,
    notified_at timestamptz not null default now(),
    primary key (alert_id, collected_offer_id)
);

comment on table public.alert_matches is 'Twin offers already notified for an alert (never twice).';

create table public.shares (
    id text primary key check (id ~ '^[A-Za-z0-9_-]{22}$'),
    user_id uuid not null references auth.users (id) on delete cascade,
    analysis_id uuid not null references public.analyses (id) on delete cascade,
    format text not null check (format in ('story', 'square', 'link')),
    hide_company boolean not null default false,
    show_calm_twin boolean not null default true,
    created_at timestamptz not null default now()
);

comment on table public.shares is 'Public shares. The identifier is 128 random bits; the public link https://jauge.io/a/{analysisId} works while a share exists.';

create index shares_user_created_index on public.shares (user_id, created_at desc);
create index shares_analysis_index on public.shares (analysis_id);

-- Quotas and subscriptions -----------------------------------------------------------------------------------------

create table public.weekly_quotas (
    user_id uuid not null references auth.users (id) on delete cascade,
    week_start date not null check (extract(isodow from week_start) = 1),
    used integer not null default 0 check (used >= 0),
    primary key (user_id, week_start)
);

comment on table public.weekly_quotas is 'Free analyses used per user and per week (Monday, Paris time).';

create table public.subscriptions (
    user_id uuid primary key references auth.users (id) on delete cascade,
    entitlement text not null default 'plus',
    status text not null check (status in ('free', 'trial', 'active', 'expired')),
    product_id text check (product_id is null or char_length(product_id) <= 200),
    store text check (store is null or char_length(store) <= 40),
    environment text check (environment is null or environment in ('PRODUCTION', 'SANDBOX')),
    expires_at timestamptz,
    trial_ends_at timestamptz,
    will_renew boolean not null default false,
    last_event_id text,
    last_event_at timestamptz,
    updated_at timestamptz not null default now()
);

comment on table public.subscriptions is 'Mirror of the RevenueCat entitlement "plus", written by the RevenueCat webhook.';

create table public.revenuecat_events (
    event_id text primary key check (char_length(event_id) between 1 and 200),
    user_id uuid references auth.users (id) on delete cascade,
    event_type text not null check (char_length(event_type) between 1 and 60),
    event_at timestamptz not null,
    received_at timestamptz not null default now()
);

comment on table public.revenuecat_events is 'RevenueCat webhook events already applied (idempotency). Purged after 90 days.';

-- Operations: costs, spend alerts, rate limits ----------------------------------------------------------------------

create table public.provider_costs (
    id bigint generated always as identity primary key,
    occurred_at timestamptz not null default now(),
    provider text not null check (char_length(provider) between 1 and 40),
    operation text not null check (char_length(operation) between 1 and 60),
    slice_id uuid references public.market_slices (id) on delete set null,
    snapshot_id uuid references public.offer_snapshots (id) on delete set null,
    units integer not null default 1,
    cost_micro_usd bigint not null check (cost_micro_usd >= 0),
    simulated boolean not null default false
);

comment on table public.provider_costs is 'Journal of the money spent on paid providers (Apify, AI, social search), in micro US dollars.';

create index provider_costs_occurred_index on public.provider_costs (occurred_at);

create table public.spend_alerts (
    day date not null,
    level text not null check (level in ('warning', 'limit_reached')),
    sent_at timestamptz not null default now(),
    primary key (day, level)
);

comment on table public.spend_alerts is 'Cost alerts already sent (one per day and level).';

create unlogged table public.rate_limit_counters (
    bucket text not null check (char_length(bucket) between 1 and 120),
    window_start timestamptz not null,
    hits integer not null default 0,
    primary key (bucket, window_start)
);

comment on table public.rate_limit_counters is 'Fixed window counters of the rate limits (user identifiers and hashed IP addresses only).';

-- Row level security -----------------------------------------------------------------------------------------------
-- RLS is enabled (not forced): the owner of the tables (the API connection and the SECURITY DEFINER functions)
-- keeps full access, every other role only gets what the policies below allow.

do $$
declare
    table_name text;
begin
    foreach table_name in array array[
        'profiles', 'offers', 'offer_snapshots', 'market_slices', 'source_collections', 'collected_offers', 'snapshot_twins',
        'market_statistics', 'analyses', 'analysis_results', 'analysis_inputs', 'jobs', 'favorites',
        'applicant_feedback', 'alerts', 'alert_matches', 'shares', 'weekly_quotas', 'subscriptions',
        'revenuecat_events', 'provider_costs', 'spend_alerts', 'rate_limit_counters'
    ] loop
        execute format('alter table public.%I enable row level security', table_name);
        execute format('revoke all on table public.%I from public, anon, authenticated', table_name);
    end loop;
end;
$$;

-- Signed in users read their own rows of the user tables, nothing else. No insert, update or delete grant.
grant select on table public.profiles, public.analyses, public.analysis_results, public.favorites, public.alerts,
    public.shares, public.weekly_quotas, public.subscriptions to authenticated;

create policy "Users read their own profile" on public.profiles for select to authenticated
    using ((select auth.uid()) = user_id);
create policy "Users read their own analyses" on public.analyses for select to authenticated
    using ((select auth.uid()) = user_id);
create policy "Users read the results of their own analyses" on public.analysis_results for select to authenticated
    using (exists (select 1 from public.analyses where analyses.id = analysis_id and analyses.user_id = (select auth.uid())));
create policy "Users read their own favorites" on public.favorites for select to authenticated
    using ((select auth.uid()) = user_id);
create policy "Users read their own alerts" on public.alerts for select to authenticated
    using ((select auth.uid()) = user_id);
create policy "Users read their own shares" on public.shares for select to authenticated
    using ((select auth.uid()) = user_id);
create policy "Users read their own quotas" on public.weekly_quotas for select to authenticated
    using ((select auth.uid()) = user_id);
create policy "Users read their own subscription" on public.subscriptions for select to authenticated
    using ((select auth.uid()) = user_id);
