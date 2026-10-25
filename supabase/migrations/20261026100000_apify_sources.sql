-- Jauge API: Apify sources (docs/backend/sources.md).
--
-- A market run of Apify is paid when it ends, minutes after it starts. While it runs, its estimate (which is also
-- its hard cap on Apify's side, maxTotalChargeUsd) is reserved in source_collections.reserved_micro_usd and counted
-- in the spending of the day, so that analyses started at the same moment cannot overshoot the daily limit together.

alter table public.source_collections
    add column reserved_micro_usd bigint not null default 0 check (reserved_micro_usd >= 0);

comment on column public.source_collections.reserved_micro_usd is
    'Estimate of a running paid collection (its cap on Apify), counted in the daily spending until the run ends.';

create index source_collections_running_index on public.source_collections (started_at) where status = 'running';
create index source_collections_run_index on public.source_collections (external_run_id) where status = 'running';

-- Money spent on a day (UTC), in micro US dollars: journaled costs (simulated calls excluded) plus the reservations of
-- the paid collections started that day and still running.
create or replace function public.api_daily_spend_micro_usd(target_day date)
returns bigint
language sql
stable
security definer
set search_path = ''
as $$
    select (
        coalesce((
            select sum(cost_micro_usd)
            from public.provider_costs
            where not simulated
              and occurred_at >= target_day::timestamptz and occurred_at < (target_day + 1)::timestamptz
        ), 0)
        + coalesce((
            select sum(reserved_micro_usd)
            from public.source_collections
            where status = 'running'
              and started_at >= target_day::timestamptz and started_at < (target_day + 1)::timestamptz
        ), 0)
    )::bigint;
$$;

revoke all on function public.api_daily_spend_micro_usd(date) from public, anon, authenticated;
grant execute on function public.api_daily_spend_micro_usd(date) to service_role;
