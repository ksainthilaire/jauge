-- Jauge: every new notification is pushed by the send-notification Edge Function.
--
-- The trigger calls the function with pg_net (asynchronous, it never slows the insert down). It reads two secrets
-- from Supabase Vault, created once by the owner of the project (docs/supabase.md):
--   jauge_functions_url   https://<project-ref>.supabase.co/functions/v1
--   jauge_webhook_secret  a long random value, also set as the JAUGE_WEBHOOK_SECRET secret of the Edge Function
-- Without these secrets the trigger does nothing: notifications are still stored and shown in the app by Realtime.

create extension if not exists pg_net with schema extensions;

create or replace function public.push_new_notification()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    functions_url text;
    webhook_secret text;
begin
    select decrypted_secret into functions_url from vault.decrypted_secrets where name = 'jauge_functions_url' limit 1;
    select decrypted_secret into webhook_secret from vault.decrypted_secrets where name = 'jauge_webhook_secret' limit 1;
    if functions_url is null or webhook_secret is null then
        return new;
    end if;
    perform net.http_post(
        url := functions_url || '/send-notification',
        body := jsonb_build_object('notification_id', new.id),
        headers := jsonb_build_object('Content-Type', 'application/json', 'x-jauge-webhook-secret', webhook_secret),
        timeout_milliseconds := 10000
    );
    return new;
end;
$$;

revoke all on function public.push_new_notification() from public, anon, authenticated;

create trigger notifications_push_after_insert
    after insert on public.notifications
    for each row execute function public.push_new_notification();
