-- Jauge: security hardening of the push devices (audit of 24/10/2026, docs/audit/securite.md).
--
-- 1. register_device only accepts real push tokens: FCM tokens (letters, digits, "-", "_", ":") on Android and APNs
--    tokens (hexadecimal) on iOS. A token is later used in the URL of the APNs request: nothing else may get in.
-- 2. One user keeps at most 10 devices: the oldest ones are removed, so that a script cannot fill the table.
-- 3. The table refuses badly formed tokens even when written by the server.

create or replace function public.register_device(device_token text, device_platform text, device_language text default 'fr')
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    calling_user uuid := auth.uid();
    cleaned_language text := lower(coalesce(nullif(left(device_language, 16), ''), 'fr'));
begin
    if calling_user is null then
        raise exception 'Not signed in' using errcode = '42501';
    end if;
    if device_platform not in ('android', 'ios') then
        raise exception 'Unknown platform' using errcode = '22023';
    end if;
    if device_token is null
        or (device_platform = 'android' and device_token !~ '^[A-Za-z0-9_:-]{32,4096}$')
        or (device_platform = 'ios' and device_token !~ '^[0-9a-fA-F]{64,200}$') then
        raise exception 'Invalid push token' using errcode = '22023';
    end if;
    if cleaned_language !~ '^[a-z]{2,3}([_-][a-z0-9]{2,8})*$' then
        cleaned_language := 'fr';
    end if;

    insert into public.devices (user_id, platform, token, language)
    values (calling_user, device_platform, device_token, cleaned_language)
    on conflict (token) do update
        set user_id = excluded.user_id,
            platform = excluded.platform,
            language = excluded.language,
            last_seen_at = now();

    delete from public.devices
    where user_id = calling_user
      and id in (
          select id from public.devices
          where user_id = calling_user
          order by last_seen_at desc
          offset 10
      );
end;
$$;

revoke all on function public.register_device(text, text, text) from public, anon;
grant execute on function public.register_device(text, text, text) to authenticated;

alter table public.devices
    add constraint devices_token_format check (
        (platform = 'android' and token ~ '^[A-Za-z0-9_:-]{32,4096}$')
        or (platform = 'ios' and token ~ '^[0-9a-fA-F]{64,200}$')
    ) not valid;
