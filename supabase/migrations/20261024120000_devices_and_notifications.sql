-- Jauge: push devices and notifications of the users, with strict row level security.
--
-- devices        one row per push token (FCM on Android, APNs on iOS). Users never write it directly: the app calls
--                register_device / unregister_device, which only act for auth.uid().
-- notifications  calm alerts and trial reminders. Written by the server only (service role); users can read their
--                own rows and set read_at, nothing else. New rows reach the open app through Realtime.

-- Devices -----------------------------------------------------------------------------------------------------------

create table public.devices (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    platform text not null check (platform in ('android', 'ios')),
    token text not null unique check (char_length(token) between 32 and 4096),
    language text not null default 'fr' check (char_length(language) between 2 and 16),
    created_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now()
);

comment on table public.devices is 'Push tokens of the devices of each user (FCM, APNs). Written through register_device only.';

create index devices_user_id_index on public.devices (user_id);

alter table public.devices enable row level security;

revoke all on table public.devices from anon, authenticated;
grant select, delete on table public.devices to authenticated;

create policy "Users read their own devices"
    on public.devices for select to authenticated
    using ((select auth.uid()) = user_id);

create policy "Users delete their own devices"
    on public.devices for delete to authenticated
    using ((select auth.uid()) = user_id);

-- Saves the push token of the calling user. A token belongs to one device: if another account used this device
-- before, the token moves to the calling user so that the previous account stops receiving its alerts here.
create or replace function public.register_device(device_token text, device_platform text, device_language text default 'fr')
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    calling_user uuid := auth.uid();
begin
    if calling_user is null then
        raise exception 'Not signed in' using errcode = '42501';
    end if;
    if device_platform not in ('android', 'ios') then
        raise exception 'Unknown platform' using errcode = '22023';
    end if;
    insert into public.devices (user_id, platform, token, language)
    values (calling_user, device_platform, device_token, coalesce(nullif(left(device_language, 16), ''), 'fr'))
    on conflict (token) do update
        set user_id = excluded.user_id,
            platform = excluded.platform,
            language = excluded.language,
            last_seen_at = now();
end;
$$;

-- Removes the push token of the calling user (sign out).
create or replace function public.unregister_device(device_token text)
returns void
language sql
security definer
set search_path = ''
as $$
    delete from public.devices where token = device_token and user_id = auth.uid();
$$;

revoke all on function public.register_device(text, text, text) from public, anon;
revoke all on function public.unregister_device(text) from public, anon;
grant execute on function public.register_device(text, text, text) to authenticated;
grant execute on function public.unregister_device(text) to authenticated;

-- Notifications -----------------------------------------------------------------------------------------------------

create table public.notifications (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    kind text not null check (kind in ('calm_alert', 'trial_reminder', 'other')),
    title text not null check (char_length(title) between 1 and 120),
    body text not null default '' check (char_length(body) <= 500),
    analysis_id text check (analysis_id is null or char_length(analysis_id) <= 64),
    alert_id text check (alert_id is null or char_length(alert_id) <= 64),
    created_at timestamptz not null default now(),
    read_at timestamptz,
    push_sent_at timestamptz,
    push_skipped_reason text
);

comment on table public.notifications is 'Notifications of each user. Inserted by the server (service role), read by the app, pushed by the send-notification Edge Function.';

create index notifications_user_created_index on public.notifications (user_id, created_at desc);

alter table public.notifications enable row level security;

revoke all on table public.notifications from anon, authenticated;
grant select on table public.notifications to authenticated;
grant update (read_at) on table public.notifications to authenticated;

create policy "Users read their own notifications"
    on public.notifications for select to authenticated
    using ((select auth.uid()) = user_id);

create policy "Users mark their own notifications as read"
    on public.notifications for update to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

-- Realtime: the open app receives the new rows of its user (Realtime applies the select policy above).
alter publication supabase_realtime add table public.notifications;
