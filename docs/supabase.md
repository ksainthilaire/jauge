# Supabase: authentication and notifications

Jauge uses Supabase for three things:

- **Authentication** (`core/authentication`): Sign in with Apple, Google, and email link (magic link), PKCE flow, deep
  link `io.jauge.app://login-callback`. The Supabase access token is sent to the Jauge API (`Authorization: Bearer`).
- **Notifications** (`core/notifications`): table `devices` (push tokens, FCM on Android, APNs on iOS), table
  `notifications`, Realtime to show calm alerts while the app is open, and the Edge Function `send-notification`.
- **Row level security** on every table: a user only sees and changes their own rows.

The app only knows the **project URL** and the **publishable key** (`sb_publishable_...`). The secret key and the
service role key never go in the app or in the repository: the build fails if it finds one. Without Supabase values
the app starts with simulated sign in and notifications (`SimulatedAuthenticationRepository`,
`SimulatedNotificationRepository`), so every screen still works.

The Jauge API itself (Edge Function `api`, its tables, its queue) is described in `docs/backend/` (architecture,
estimation, deployment guide in French).

Nothing in this repository changes the hosted project by itself. The steps below are done by the owner of the
project, once.

## Files

```
supabase/config.toml                                     local settings (Supabase CLI), auth redirect URLs, providers
supabase/migrations/20261024120000_devices_and_notifications.sql   tables, RLS, register_device, Realtime
supabase/migrations/20261024120100_push_on_new_notification.sql    trigger that calls the Edge Function (pg_net)
supabase/functions/send-notification/index.ts            sends a notification with FCM and APNs
supabase/functions/.env.example                          names of the secrets of the function (no values)
```

## 1. Install the Supabase CLI and link the project

```
brew install supabase/tap/supabase
cd <repository>
supabase login
supabase link --project-ref <project-ref>        # the ref is in the project URL: https://<project-ref>.supabase.co
```

## 2. Apply the migrations

```
supabase db push
```

This creates `devices`, `notifications`, their row level security policies, the functions `register_device` and
`unregister_device`, adds `notifications` to the `supabase_realtime` publication and creates the trigger
`notifications_push_after_insert`. Check in the dashboard (Database > Tables) that RLS is **enabled** on both tables.

What the policies allow:

| Table | anon | authenticated (the app) | service role (servers, Edge Function) |
|---|---|---|---|
| `devices` | nothing | read and delete own rows; write only through `register_device` / `unregister_device` | everything |
| `notifications` | nothing | read own rows, update `read_at` of own rows only | everything (inserts notifications) |

## 3. Authentication settings (dashboard > Authentication)

1. **URL Configuration**: Site URL `io.jauge.app://login-callback`; Redirect URLs: add `io.jauge.app://login-callback`.
2. **Email**: enable the Email provider, keep "Confirm email" off for magic links if you like (the link itself proves
   the address). Optional: customize the "Magic Link" template in French. Set up a custom SMTP server before launch
   (the default one is limited to a few emails per hour).
3. **Apple** (Sign in with Apple, required by the App Store as soon as Google sign in exists):
   - Apple Developer > Identifiers: enable "Sign in with Apple" on the App ID; create a **Services ID** (for example
     `io.jauge.app.signin`), enable Sign in with Apple on it, domain `<project-ref>.supabase.co`, return URL
     `https://<project-ref>.supabase.co/auth/v1/callback`;
   - create a Sign in with Apple **key** (.p8);
   - in Supabase, Apple provider: Client IDs = the Services ID (and the bundle id), secret = the JWT generated from
     the key (Supabase explains how; it expires every 6 months, put a reminder).
4. **Google**:
   - Google Cloud console > APIs and Services > Credentials: create an OAuth client of type **Web application** with
     the redirect URI `https://<project-ref>.supabase.co/auth/v1/callback`;
   - in Supabase, Google provider: Client ID and Client Secret of this web client.
5. Rate limits: keep the default limits on emails; the app shows "On souffle un instant" when they are reached.

## 4. Push notifications

### Android (Firebase Cloud Messaging)

1. Firebase console: create a project (or use the Google Cloud project above), add an Android app with the package
   `io.jauge.app`, download **`google-services.json`** and put it in `androidApp/google-services.json`.
   **Never commit it** (it is in `.gitignore`); the build applies the Google services plugin only when the file is
   there, and without it the app simply has no push.
2. Firebase console > Project settings > Service accounts > Generate new private key: this JSON is the secret
   `FCM_SERVICE_ACCOUNT_JSON` of the Edge Function (step 5). Keep the file out of the repository.
3. The app asks the Android 13 notification permission from screen 12, creates the channel "Alertes « calme »" and
   saves its token through `register_device`.

### iOS (APNs)

1. Apple Developer > Keys: create a key with **Apple Push Notifications service (APNs)**, download the .p8 file and
   note its Key ID and your Team ID.
2. Xcode: the target already uses `iosApp/iosApp.entitlements` (`aps-environment`). In Signing and Capabilities, check
   that **Push Notifications** is listed (Xcode adds it to the App ID with automatic signing; it needs a paid
   developer account). Set `aps-environment` to `production` for App Store builds (Xcode does it when archiving).
3. The app delegate (`iosApp/iosApp/iOSApp.swift`) forwards the device token to the shared code, which saves it with
   `register_device`. The URL scheme `io.jauge.app` is declared in `Info.plist` for the sign in callback.

## 5. Deploy the Edge Function and its secrets

```
cp supabase/functions/.env.example supabase/functions/.env      # fill it, never commit it
supabase secrets set --env-file supabase/functions/.env
supabase functions deploy send-notification --no-verify-jwt
```

Secrets of the function:

| Secret | Value |
|---|---|
| `JAUGE_WEBHOOK_SECRET` | a long random value (`openssl rand -hex 32`), the same as the Vault secret below |
| `FCM_SERVICE_ACCOUNT_JSON` | content of the Firebase service account JSON, on one line |
| `APNS_KEY_P8` | content of the APNs .p8 key |
| `APNS_KEY_ID`, `APNS_TEAM_ID` | ids of the APNs key and of the team |
| `APNS_BUNDLE_ID` | bundle id of the iOS app |
| `APNS_USE_SANDBOX` | `true` for development builds run from Xcode, `false` for TestFlight and the App Store |

`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are given to the function by Supabase: do not set them.

## 6. Connect the database to the function

The trigger reads two secrets from **Vault** (dashboard > Project Settings > Vault, or SQL editor):

```sql
select vault.create_secret('https://<project-ref>.supabase.co/functions/v1', 'jauge_functions_url');
select vault.create_secret('<the same value as JAUGE_WEBHOOK_SECRET>', 'jauge_webhook_secret');
```

Without them the trigger does nothing: notifications are still saved and shown in the open app by Realtime.

## 7. Check that it works

1. Sign in in the app (Apple, Google or email link), allow notifications on screen 12.
2. Table `devices`: a row with your user id and the platform.
3. Insert a notification as the service role (SQL editor):

   ```sql
   insert into public.notifications (user_id, kind, title, body, analysis_id)
   values ('<your user id>', 'calm_alert', 'Une offre jumelle devient calme',
           'Chargée de communication, Villeurbanne : 19/100', 'a-41872');
   ```

   With the app open, the mint banner appears at the top (Realtime). With the app in the background, the push
   arrives. The row gets `push_sent_at`, or `push_skipped_reason` (`weekly_limit` after 3 calm alerts in 7 days,
   `no_device`, `push_not_configured`, `delivery_failed`).
4. Logs: dashboard > Edge Functions > send-notification > Logs; `select * from net._http_response order by id desc;`
   for the calls made by the trigger.

## 8. Values for the app

`local.properties` (Android, ignored by git) and `iosApp/Configuration/Config.xcconfig` (iOS, ignored by git):

```
supabase.url=https://<project-ref>.supabase.co
supabase.publishableKey=sb_publishable_...
```

```
JAUGE_SUPABASE_URL=https:/$()/<project-ref>.supabase.co
JAUGE_SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```

## How the app uses it

- `SupabaseClientProvider` builds one client (Auth with PKCE and the deep link, Postgrest, Realtime).
- `SupabaseAuthenticationRepository` implements the domain `AuthenticationRepository`: `signInWith(APPLE | GOOGLE)`
  opens the provider page, `sendMagicLink(email)` sends the link, `completeSignIn(url)` exchanges the code of the
  deep link for a session (expired link: "Ce lien a expiré"), `state` follows the session.
- `SessionSynchronizer` (composeApp) links RevenueCat to the user id after sign in, saves the push token, and hands
  the deep links (Android `MainActivity`, iOS `onOpenURL`) to the authentication.
- `SupabaseNotificationRepository` implements `NotificationRepository`: list and read state of notifications,
  Realtime feed of the signed in user, permission and token of the platform (`FirebasePushService`,
  `ApnsPushService`), `register_device` / `unregister_device` (sign out removes the token first).
- `CalmAlertBannerHost` (composeApp) shows each calm alert received by Realtime for 6 seconds; a tap opens the twin
  offers of the analysis.
- Account deletion (`DELETE /v1/account` on the Jauge API, done with the service role on the server) removes the
  user from `auth.users`; `devices` and `notifications` rows are deleted with it (`on delete cascade`).
