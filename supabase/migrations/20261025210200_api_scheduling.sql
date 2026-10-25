-- Jauge API: wakes the worker of the Edge Function "api" (pg_net) and schedules the recurring work (pg_cron).
--
-- The worker runs at POST {jauge_functions_url}/api/internal/worker with the header x-jauge-webhook-secret. It is
-- woken right away when a job is created (trigger below), and every 15 seconds by pg_cron when jobs are due
-- (retries, collections waiting for Apify, expired leases). Without due jobs nothing is called, so the free quota of
-- Edge Function invocations is kept for the app.
--
-- Uses the same Vault secrets as the push notifications (docs/supabase.md):
--   jauge_functions_url   https://<project-ref>.supabase.co/functions/v1
--   jauge_webhook_secret  long random value, also the JAUGE_WEBHOOK_SECRET secret of the Edge Functions
-- Without them, nothing is called (the API then runs the worker itself after each request, see ARCHITECTURE.md).

create extension if not exists pg_net with schema extensions;

do $$
begin
    if exists (select 1 from pg_available_extensions where name = 'pg_cron') then
        create extension if not exists pg_cron;
    end if;
end;
$$;

-- Calls one internal route of the Edge Function "api". Does nothing when the Vault secrets are missing.
create or replace function public.api_call_internal_route(route text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    functions_url text;
    webhook_secret text;
begin
    if route not in ('worker', 'maintenance') then
        raise exception 'Unknown internal route' using errcode = '22023';
    end if;
    select decrypted_secret into functions_url from vault.decrypted_secrets where name = 'jauge_functions_url' limit 1;
    select decrypted_secret into webhook_secret from vault.decrypted_secrets where name = 'jauge_webhook_secret' limit 1;
    if functions_url is null or webhook_secret is null then
        return;
    end if;
    perform net.http_post(
        url := functions_url || '/api/internal/' || route,
        body := '{}'::jsonb,
        headers := jsonb_build_object('Content-Type', 'application/json', 'x-jauge-webhook-secret', webhook_secret),
        timeout_milliseconds := 5000
    );
end;
$$;

-- Wakes the worker only when jobs are due.
create or replace function public.api_wake_worker_when_due()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if public.api_due_job_count() > 0 then
        perform public.api_call_internal_route('worker');
    end if;
end;
$$;

-- New jobs wake the worker at once (one call per statement, not per row).
create or replace function public.api_wake_worker_after_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    perform public.api_call_internal_route('worker');
    return null;
end;
$$;

create trigger jobs_wake_worker_after_insert
    after insert on public.jobs
    for each statement execute function public.api_wake_worker_after_insert();

do $$
declare
    function_signature text;
begin
    foreach function_signature in array array[
        'public.api_call_internal_route(text)',
        'public.api_wake_worker_when_due()',
        'public.api_wake_worker_after_insert()'
    ] loop
        execute format('revoke all on function %s from public, anon, authenticated', function_signature);
    end loop;
end;
$$;

-- Recurring work, only where pg_cron exists (the hosted project; not the test database).
do $$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        perform cron.schedule('jauge-api-worker', '15 seconds', 'select public.api_wake_worker_when_due()');
        perform cron.schedule('jauge-api-purge', '7 * * * *', 'select public.api_purge_expired()');
        perform cron.schedule('jauge-api-maintenance', '23 5 * * *', $job$select public.api_call_internal_route('maintenance')$job$);
    end if;
end;
$$;
