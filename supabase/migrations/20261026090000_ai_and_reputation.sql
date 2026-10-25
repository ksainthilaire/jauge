-- AI and social search (phase 2): tokens in the cost journal, reputation of companies cached 30 days.

-- Tokens of the AI calls next to their cost (one row per operation, every attempt counted).
alter table public.provider_costs
    add column model text check (model is null or char_length(model) <= 80),
    add column input_tokens integer check (input_tokens is null or input_tokens >= 0),
    add column output_tokens integer check (output_tokens is null or output_tokens >= 0);

comment on column public.provider_costs.input_tokens is 'Input tokens of an AI operation (all attempts).';
comment on column public.provider_costs.output_tokens is 'Output tokens of an AI operation, thinking included (all attempts).';

-- Reputation of a company as summarized by the AI from public Reddit and X posts. Global (shared by every user),
-- no personal data: the posts are never stored, only the summary in French and English and links without usernames.
create table public.company_reputations (
    company_key text primary key check (company_key ~ '^[0-9a-f]{64}$'),
    found boolean not null,
    tone text check (tone is null or tone in ('positive', 'mixed', 'negative', 'unknown')),
    summary_french text check (summary_french is null or char_length(summary_french) <= 400),
    summary_english text check (summary_english is null or char_length(summary_english) <= 400),
    mention_count integer not null default 0 check (mention_count >= 0),
    sources text[] not null default '{}',
    links jsonb not null default '[]'::jsonb check (jsonb_typeof(links) = 'array'),
    prompt_version text not null check (char_length(prompt_version) <= 40),
    searched_at timestamptz not null default now(),
    expires_at timestamptz not null
);

comment on table public.company_reputations is 'Reputation of companies from public posts (Reddit, X), summarized by the AI, cached 30 days. Key = sha256 of the folded company name.';

create index company_reputations_expires_index on public.company_reputations (expires_at);

alter table public.company_reputations enable row level security;
revoke all on table public.company_reputations from public, anon, authenticated;

create or replace function public.api_purge_expired()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    purged_inputs integer;
    purged_counters integer;
    purged_jobs integer;
    purged_events integer;
    purged_snapshots integer;
    purged_offers integer;
begin
    delete from public.analysis_inputs where expires_at < now();
    get diagnostics purged_inputs = row_count;

    delete from public.rate_limit_counters where window_start < now() - interval '1 day';
    get diagnostics purged_counters = row_count;

    delete from public.jobs where status in ('succeeded', 'failed') and finished_at < now() - interval '14 days';
    get diagnostics purged_jobs = row_count;

    delete from public.revenuecat_events where received_at < now() - interval '90 days';
    get diagnostics purged_events = row_count;

    delete from public.offer_snapshots as snapshots
    where coalesce(snapshots.expires_at, snapshots.created_at) < now() - interval '30 days'
      and not exists (select 1 from public.analyses where analyses.snapshot_id = snapshots.id);
    get diagnostics purged_snapshots = row_count;

    delete from public.market_slices
    where coalesce(expires_at, created_at) < now() - interval '30 days';

    -- Twins of old snapshots are not needed any more (alerts compare with recent twins only).
    delete from public.snapshot_twins as twins
    using public.offer_snapshots as snapshots
    where snapshots.id = twins.snapshot_id and snapshots.computed_at < now() - interval '90 days';

    -- Offers not seen for 6 months: their months stay in market_statistics (trend and salary history).
    delete from public.collected_offers as collected
    where collected.last_seen_at < now() - interval '180 days'
      and not exists (select 1 from public.snapshot_twins where snapshot_twins.collected_offer_id = collected.id)
      and not exists (select 1 from public.alert_matches where alert_matches.collected_offer_id = collected.id);
    get diagnostics purged_offers = row_count;

    delete from public.provider_costs where occurred_at < now() - interval '400 days';

    -- Reputations of companies: 30 days of cache, then searched again when asked.
    delete from public.company_reputations where expires_at < now();

    return jsonb_build_object(
        'inputs', purged_inputs, 'counters', purged_counters, 'jobs', purged_jobs,
        'events', purged_events, 'snapshots', purged_snapshots, 'collectedOffers', purged_offers
    );
end;
$$;

revoke all on function public.api_purge_expired() from public, anon, authenticated;
grant execute on function public.api_purge_expired() to service_role;
