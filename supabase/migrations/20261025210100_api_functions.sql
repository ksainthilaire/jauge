-- Jauge API: database functions used by the Edge Function "api", the scheduler and the triggers.
--
-- Every function is SECURITY DEFINER with an empty search_path (every name is schema qualified), and only the
-- service role (and the owner) may execute it: the app (anon, authenticated) cannot call any of them.

-- Rate limit: fixed window counter. Returns whether the hit is allowed and, when it is not, the seconds to wait.
create or replace function public.api_hit_rate_limit(bucket_name text, window_seconds integer, maximum_hits integer)
returns table (allowed boolean, retry_after_seconds integer)
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_window timestamptz;
    current_hits integer;
begin
    if window_seconds < 1 or maximum_hits < 1 then
        raise exception 'Invalid rate limit' using errcode = '22023';
    end if;
    current_window := to_timestamp(floor(extract(epoch from now()) / window_seconds) * window_seconds);
    insert into public.rate_limit_counters as counters (bucket, window_start, hits)
    values (bucket_name, current_window, 1)
    on conflict (bucket, window_start) do update set hits = counters.hits + 1
    returning counters.hits into current_hits;
    allowed := current_hits <= maximum_hits;
    retry_after_seconds := case when allowed then 0
        else greatest(1, ceil(extract(epoch from (current_window + make_interval(secs => window_seconds) - now())))::integer) end;
    return next;
end;
$$;

-- Weekly free quota: uses one free analysis of the week when one is left. Atomic (row lock of the upsert).
create or replace function public.api_consume_weekly_quota(target_user uuid, target_week date, weekly_limit integer)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    used_after integer;
begin
    insert into public.weekly_quotas as quotas (user_id, week_start, used)
    values (target_user, target_week, 0)
    on conflict (user_id, week_start) do nothing;

    update public.weekly_quotas
    set used = used + 1
    where user_id = target_user and week_start = target_week and used < weekly_limit
    returning used into used_after;

    return used_after is not null;
end;
$$;

-- Gives back a free analysis (unreadable offer, unsupported link, failure of the server).
create or replace function public.api_refund_weekly_quota(target_user uuid, target_week date)
returns void
language sql
security definer
set search_path = ''
as $$
    update public.weekly_quotas
    set used = greatest(used - 1, 0)
    where user_id = target_user and week_start = target_week;
$$;

-- Work queue: claims due jobs (queued and due, or running with an expired lease) for one worker.
-- SKIP LOCKED lets several workers run at once without taking the same job.
create or replace function public.api_claim_jobs(worker_name text, batch_size integer, lease_seconds integer)
returns setof public.jobs
language plpgsql
security definer
set search_path = ''
as $$
begin
    return query
    update public.jobs
    set status = 'running',
        attempts = jobs.attempts + 1,
        locked_by = worker_name,
        lease_expires_at = now() + make_interval(secs => lease_seconds),
        updated_at = now()
    where jobs.id in (
        select candidate.id
        from public.jobs as candidate
        where (candidate.status = 'queued' and candidate.run_after <= now())
           or (candidate.status = 'running' and candidate.lease_expires_at < now())
        order by candidate.run_after
        limit batch_size
        for update skip locked
    )
    returning jobs.*;
end;
$$;

-- Number of jobs waiting (used by the scheduler to wake the worker only when there is work).
create or replace function public.api_due_job_count()
returns integer
language sql
stable
security definer
set search_path = ''
as $$
    select count(*)::integer
    from public.jobs
    where (status = 'queued' and run_after <= now())
       or (status = 'running' and lease_expires_at < now());
$$;

-- Deletes every row of a user in the tables of the API (the auth user itself is deleted by the API through the
-- Auth admin endpoint, which also cascades to devices and notifications).
create or replace function public.api_delete_user_data(target_user uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    delete from public.shares where user_id = target_user;
    delete from public.alerts where user_id = target_user;
    delete from public.favorites where user_id = target_user;
    delete from public.applicant_feedback where user_id = target_user;
    delete from public.analyses where user_id = target_user;
    delete from public.weekly_quotas where user_id = target_user;
    delete from public.subscriptions where user_id = target_user;
    delete from public.revenuecat_events where user_id = target_user;
    delete from public.profiles where user_id = target_user;
    delete from public.notifications where user_id = target_user;
    delete from public.devices where user_id = target_user;
    delete from public.jobs where subject_user_id = target_user and kind <> 'delete_account';
end;
$$;

-- Real money spent on a day (UTC), in micro US dollars (simulated provider calls do not count).
create or replace function public.api_daily_spend_micro_usd(target_day date)
returns bigint
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce(sum(cost_micro_usd), 0)::bigint
    from public.provider_costs
    where not simulated
      and occurred_at >= target_day::timestamptz and occurred_at < (target_day + 1)::timestamptz;
$$;

-- Housekeeping: inputs older than 24 hours, old counters and events, finished jobs, snapshots nobody uses any more.
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

    return jsonb_build_object(
        'inputs', purged_inputs, 'counters', purged_counters, 'jobs', purged_jobs,
        'events', purged_events, 'snapshots', purged_snapshots, 'collectedOffers', purged_offers
    );
end;
$$;

do $$
declare
    function_signature text;
begin
    foreach function_signature in array array[
        'public.api_hit_rate_limit(text, integer, integer)',
        'public.api_consume_weekly_quota(uuid, date, integer)',
        'public.api_refund_weekly_quota(uuid, date)',
        'public.api_claim_jobs(text, integer, integer)',
        'public.api_due_job_count()',
        'public.api_delete_user_data(uuid)',
        'public.api_daily_spend_micro_usd(date)',
        'public.api_purge_expired()'
    ] loop
        execute format('revoke all on function %s from public, anon, authenticated', function_signature);
        execute format('grant execute on function %s to service_role', function_signature);
    end loop;
end;
$$;
