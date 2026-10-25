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
supabase/.env.example                                    names of the settings read by config.toml (Brevo SMTP, Apple, Google)
supabase/templates/magic_link.html                       sign in email (French and English), see "E-mails de connexion avec Brevo"
supabase/templates/confirmation.html                     same email for a new account when "Confirm email" is on
supabase/templates/assets/logo-jauge-96.png              logo of the emails, to put online at https://jauge.io/email/logo-jauge-96.png
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
   the address). The emails go out through **Brevo** with the branded templates of `supabase/templates/`: follow the
   French guide "E-mails de connexion avec Brevo" below (the default Supabase server is limited to a few emails per
   hour and sends a plain English email).
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

## E-mails de connexion avec Brevo

Les e-mails de connexion (lien magique) partent par **Brevo**, de `connexion@jauge.io`, au nom de « Jauge », avec le
modèle aux couleurs de Jauge. Tout se fait une fois, dans l'ordre. Il faut le domaine `jauge.io` (il est sur Cloudflare,
voir `docs/backend/DEPLOIEMENT.md`, étape 7).

### Ce qui est dans le dépôt

- `supabase/templates/magic_link.html` : l'e-mail de connexion. `confirmation.html` : le même pour un nouveau compte,
  envoyé seulement si « Confirm email » est allumé.
- **Deux langues dans un seul modèle** : Supabase n'a qu'un modèle par type d'e-mail. L'app envoie la langue du
  téléphone avec la demande de lien (`data: { locale: "fr" | "en" }`, français par défaut) et le modèle choisit avec
  `{{ if and .Data (eq .Data.locale "en") }}…{{ else }}…{{ end }}` (`.Data` = les métadonnées du compte ; le `and .Data`
  évite une erreur pour un compte sans métadonnées). Supabase n'enregistre ces métadonnées qu'à la création du compte :
  l'app remet donc la langue à jour après chaque connexion (`SupabaseAuthenticationRepository`), et l'e-mail suivant
  suit la langue du téléphone. Un compte Apple ou Google qui ne s'est jamais connecté depuis l'app reçoit le français.
- **L'objet est conditionnel aussi** (Supabase traite l'objet comme un modèle) :
  « Ton lien de connexion à Jauge » ou « Your Jauge sign-in link ». Si un jour l'objet reçu affiche les `{{`, remplace
  l'objet par un objet fixe court : `Ton lien Jauge / Your Jauge link`.
- Lien valable **1 heure**, une seule fois (`otp_expiry = 3600`, Authentication > Emails > « Email OTP Expiration » sur
  le projet en ligne : mets `3600`).
- Aucun traceur, aucune police chargée à distance. Une seule image distante : le logo,
  **`https://jauge.io/email/logo-jauge-96.png`** (96 × 96, affiché en 48 × 48, fichier
  `supabase/templates/assets/logo-jauge-96.png` à mettre en ligne à cette adresse). Tant qu'il n'y est pas, ou si la
  messagerie bloque les images, le nom « Jauge » écrit en texte à côté suffit.
- Supabase n'envoie que la partie HTML (pas de partie texte) : le lien est donc aussi écrit en clair sous le bouton,
  et un texte d'aperçu caché remplit la ligne de prévisualisation des messageries.
- Mode sombre : couleurs sombres de Jauge dans Apple Mail, iOS, Outlook.com ; Gmail fait sa propre inversion.
- Aperçus : `~/maquettes-apps-2410/email-apercu/` (PNG français et anglais, mobile, sombre, sans image).

### 1. Le compte et le domaine sur Brevo

1. Crée un compte sur https://www.brevo.com (l'offre gratuite permet 300 e-mails par jour, assez pour commencer).
2. Brevo > roue en haut à droite > **Expéditeurs, domaines et IP dédiées** > onglet **Domaines** > **Ajouter un
   domaine** : `jauge.io`. Choisis « Authentifier le domaine moi-même » (ou la connexion automatique Cloudflare si
   Brevo la propose : elle crée les entrées toute seule).
3. Brevo affiche les entrées DNS à créer. Dans **Cloudflare > jauge.io > DNS > Records**, ajoute-les **exactement**
   comme Brevo les donne (copie-colle chaque valeur) :
   - le **code Brevo** : une entrée `TXT` sur `jauge.io` (`@`) dont la valeur commence par `brevo-code:` ;
   - la **DKIM** : selon ce que Brevo affiche, deux entrées `CNAME` (`brevo1._domainkey` et `brevo2._domainkey`) ou une
     entrée `TXT` (`mail._domainkey`). Pour les `CNAME`, mets le nuage en **gris (DNS only)**, jamais orange ;
   - la **DMARC** : une entrée `TXT` sur `_dmarc` ; si Brevo n'en propose pas, mets
     `v=DMARC1; p=none; rua=mailto:contact@jauge.io` (tu passeras à `p=quarantine` après quelques semaines sans
     problème). S'il existe déjà une entrée `_dmarc`, garde une seule entrée.
   - SPF : pas obligatoire pour Brevo. Si `jauge.io` a déjà une entrée `TXT` `v=spf1 …`, ajoute `include:spf.brevo.com`
     dedans (une seule entrée SPF par domaine).
4. Reviens sur Brevo et clique **Vérifier** (ou « Authentifier ce domaine »). Cloudflare est rapide, mais compte
   jusqu'à quelques heures. Le domaine doit afficher « Authentifié » avec DKIM et DMARC en vert.
5. Onglet **Expéditeurs** > **Ajouter un expéditeur** : nom `Jauge`, adresse `connexion@jauge.io`. Comme le domaine
   est authentifié, il est validé sans boîte aux lettres. Conseillé : Cloudflare > Email > **Email Routing**, fais
   suivre `connexion@jauge.io` et `contact@jauge.io` vers ta boîte, pour lire les réponses.

### 2. La clé SMTP

1. Brevo > **SMTP & API** > onglet **SMTP** > **Générer une nouvelle clé SMTP**, nom `supabase-auth`.
2. Note l'**identifiant** affiché sur cette page (du type `1a2b3c001@smtp-brevo.com`) et la **clé** (elle n'est montrée
   qu'une fois). C'est une clé **SMTP**, pas la clé API `xkeysib-…`. Ne la mets jamais dans le dépôt ni dans l'app.
3. **Coupe le suivi** : dans les réglages des e-mails transactionnels de Brevo (Transactionnel > Paramètres, rubrique
   suivi), désactive le suivi des **ouvertures** et des **clics**. Le modèle n'a aucun traceur, et Brevo ne doit pas en
   ajouter ni réécrire le lien de connexion. Tu le vérifies à l'étape 5.

### 3. Les réglages Supabase

Supabase > ton projet > **Authentication** > **Emails** > onglet **SMTP Settings** :

| Champ | Valeur |
|---|---|
| Enable custom SMTP | allumé |
| Sender email address | `connexion@jauge.io` |
| Sender name | `Jauge` |
| Host | `smtp-relay.brevo.com` |
| Port number | `587` |
| Minimum interval per user | `60` secondes |
| Username | l'identifiant SMTP de Brevo (`…@smtp-brevo.com`) |
| Password | la clé SMTP de Brevo |

Clique **Save**. Puis **Authentication** > **Rate Limits** : « Rate limit for sending emails » passe à 30 par heure
avec un SMTP à toi ; mets `100` (largement assez au début). Quand la limite est atteinte, l'app affiche « On souffle un
instant ».

### 4. Le modèle et l'objet

Supabase > **Authentication** > **Emails** > onglet **Templates** :

1. **Magic Link** :
   - **Subject** : colle exactement
     `{{ if and .Data (eq .Data.locale "en") }}Your Jauge sign-in link{{ else }}Ton lien de connexion à Jauge{{ end }}`
   - **Message body** : onglet **Source**, efface tout et colle le contenu de `supabase/templates/magic_link.html`
     (dans le Terminal : `pbcopy < supabase/templates/magic_link.html`, puis Cmd+V). **Save**.
2. **Confirm signup** (utile seulement si « Confirm email » est allumé, à faire quand même) :
   - **Subject** : `{{ if and .Data (eq .Data.locale "en") }}Confirm your email for Jauge{{ else }}Confirme ton adresse pour Jauge{{ end }}`
   - **Message body** : le contenu de `supabase/templates/confirmation.html`. **Save**.
3. Authentication > Emails (ou Providers > Email) : **Email OTP Expiration** = `3600` (1 heure, ce que dit l'e-mail).

Autre moyen, pour qui utilise la ligne de commande : remplis `supabase/.env` (copie de `supabase/.env.example`, jamais
envoyé sur GitHub) avec `BREVO_SMTP_LOGIN` et `BREVO_SMTP_KEY`, puis `supabase config push` : la CLI envoie les
réglages Auth de `supabase/config.toml` (SMTP, objets, modèles, durée du lien). Elle montre les différences et demande
confirmation : relis-les, elle envoie aussi les autres réglages Auth du fichier.

### 5. Le test d'envoi

1. Sur un téléphone en **français** : app > Continuer avec un e-mail > ton adresse. L'e-mail arrive en moins d'une
   minute, de « Jauge <connexion@jauge.io> », objet « Ton lien de connexion à Jauge ». « Me connecter » ouvre l'app et
   te connecte. Retouche le même lien : il ne marche plus (une seule fois).
2. Sur un téléphone en **anglais** (ou une autre adresse) : objet « Your Jauge sign-in link », texte en anglais.
3. Dans Gmail, ouvre l'e-mail > ⋮ > **Afficher l'original** : `SPF`, `DKIM` et `DMARC` doivent dire `PASS`, et le lien
   du bouton doit commencer par `https://<project-ref>.supabase.co/auth/v1/verify` (s'il passe par un domaine Brevo,
   le suivi des clics est encore allumé : étape 2.3).
4. Facultatif : envoie un lien à l'adresse donnée par https://www.mail-tester.com et vise 9/10 ou plus.
5. Si rien n'arrive : Supabase > Logs > **Auth** (erreur SMTP), puis Brevo > Transactionnel > **Logs** (e-mail refusé,
   domaine non authentifié). Une erreur `535` veut dire identifiant ou clé SMTP faux.

## How the app uses it

- `SupabaseClientProvider` builds one client (Auth with PKCE and the deep link, Postgrest, Realtime).
- `SupabaseAuthenticationRepository` implements the domain `AuthenticationRepository`: `signInWith(APPLE | GOOGLE)`
  opens the provider page, `sendMagicLink(email)` sends the link with the language of the device in the user metadata
  (`EmailLanguage`, `locale` = `en` or `fr`, read by the email templates; saved again after each sign in), `completeSignIn(url)` exchanges the code of the
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
