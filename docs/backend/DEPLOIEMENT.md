# Mettre l'API Jauge en ligne

Ce guide est pour toi, propriétaire du projet. Rien n'a été mis en ligne pour toi : tu fais chaque étape une fois, dans
l'ordre. Compte une heure la première fois.

## En bref

- L'API tourne **sur Supabase** (ton projet `ynvaaswsenerrummanrs`), dans une seule fonction appelée `api`.
- Les données sont dans la base Supabase (tables créées par les fichiers de `supabase/migrations/`).
- L'adresse publique `https://api.jauge.io` passe par **Cloudflare** (gratuit), qui renvoie vers Supabase.
- Tant que les connecteurs ne sont pas branchés, l'API répond avec des **données simulées** : ne l'ouvre pas au
  public avant (voir « Mode simulé »). Les sources d'offres Apify sont prêtes (étape 8) ; l'IA arrive dans un autre lot.

## 1. Installer les outils (une fois)

Dans le Terminal :

```
brew install supabase/tap/supabase
brew install deno
```

## 2. Relier le dossier à ton projet Supabase (une fois)

```
cd ~/maquettes-apps-2410/jauge-dev
supabase login
supabase link --project-ref ynvaaswsenerrummanrs
```

`supabase login` ouvre le navigateur pour te connecter. `link` te demande le mot de passe de la base (Supabase >
Project Settings > Database).

## 3. Les deux secrets de la base (une fois)

Ils servent à la base pour réveiller l'API (et à envoyer les notifications). Si tu les as déjà créés pour les
notifications, passe à l'étape 4.

1. Crée une valeur au hasard : `openssl rand -hex 32` (garde-la, elle sert aussi à l'étape 4).
2. Supabase > SQL Editor, colle puis lance (remplace `TA_VALEUR`) :

```
select vault.create_secret('https://ynvaaswsenerrummanrs.supabase.co/functions/v1', 'jauge_functions_url');
select vault.create_secret('TA_VALEUR', 'jauge_webhook_secret');
```

## 4. Les secrets de l'API

Copie le modèle puis remplis-le (ce fichier n'est jamais envoyé sur GitHub) :

```
cp supabase/functions/.env.example supabase/functions/.env
open -e supabase/functions/.env
```

| Nom | Quoi mettre | Obligatoire |
|---|---|---|
| `JAUGE_WEBHOOK_SECRET` | la valeur de l'étape 3 | oui |
| `JAUGE_IP_HASH_SALT` | une autre valeur `openssl rand -hex 32` | oui |
| `JAUGE_PROXY_SECRET` | une troisième valeur `openssl rand -hex 32` (la même ira dans Cloudflare, étape 7) | oui |
| `JAUGE_PROVIDER_MODE` | `simulated` jusqu'aux connecteurs, puis `live` (une clé seule n'allume rien) | oui |
| `APIFY_PRICING_TIER` | `BRONZE` avec l'offre Starter d'Apify, `FREE` avec l'offre gratuite | non |
| `APIFY_WEBHOOK_URL` | `https://ynvaaswsenerrummanrs.supabase.co/functions/v1/api/webhooks/apify` (déjà rempli dans le modèle) | conseillé |
| `APIFY_WEBHOOK_SECRET` | une valeur `openssl rand -hex 32` | conseillé |
| `JAUGE_APIFY_SOURCES`, `JAUGE_APIFY_COMPLEMENTARY_SOURCES` | sources allumées (voir `docs/backend/sources.md`) ; les valeurs du modèle conviennent | non |
| `JAUGE_DAILY_SPEND_LIMIT_USD` | dépense maximum par jour en dollars (3 par défaut, soit 90 $ par mois au plus) | non |
| `JAUGE_ALERT_WEBHOOK_URL` | une adresse ntfy (par exemple `https://ntfy.sh/un-nom-secret`) pour recevoir les alertes de coût | conseillé |
| `REVENUECAT_WEBHOOK_AUTHORIZATION` | une valeur au hasard, par exemple `Bearer ` suivi de `openssl rand -hex 24` (la même ira dans RevenueCat, étape 6) | oui |
| `REVENUECAT_SECRET_API_KEY` | ta clé secrète RevenueCat `sk_...` (RevenueCat > API keys) | oui |
| `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY_P8` | ta clé « Sign in with Apple » (Apple Developer > Keys), le contenu du fichier .p8 sur une ligne avec `\n` | oui pour la suppression des comptes Apple |
| `APPLE_CLIENT_ID` | `io.jauge.app` | oui |
| `SUPABASE_JWT_SECRET` | seulement si ton projet signe encore ses jetons avec l'ancien secret (Project Settings > API > JWT) | non |
| `AI_PROVIDER` | `gemini` (par défaut) ou `mistral` : quelle IA lit les offres et écrit les conseils (étape 8 bis) | non |
| `GEMINI_API_KEY` | ta clé Gemini (Google AI Studio > Get API key), sur un projet **avec facturation** | oui si `gemini` |
| `MISTRAL_API_KEY` | ta clé Mistral (console.mistral.ai > API Keys) | oui si `mistral` |
| `APIFY_TOKEN` | ton jeton Apify (Apify > Settings > API & Integrations), aussi utilisé pour les avis Reddit et X | oui en mode `live` |
| `JAUGE_SOCIAL_SOURCES` | réseaux lus pour les avis : `reddit,x` (par défaut), `x`, `reddit` ou `none` | non |

Les autres lignes ont déjà une bonne valeur. Supabase fournit lui-même `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` et
`SUPABASE_DB_URL`. Envoie les secrets :

```
supabase secrets set --env-file supabase/functions/.env
```

## 5. Déployer (une commande, à refaire à chaque nouvelle version)

```
bash supabase/deploy.sh
```

Le script vérifie le code (tests), crée ou met à jour les tables, puis envoie les fonctions `api` et
`send-notification`. Vérifie ensuite :

```
curl -s https://ynvaaswsenerrummanrs.supabase.co/functions/v1/api/health
```

Tu dois lire `"status":"ok"`.

## 6. Brancher RevenueCat

RevenueCat > Project > Integrations > Webhooks > Add :

- URL : `https://api.jauge.io/webhooks/revenuecat` (ou, avant Cloudflare,
  `https://ynvaaswsenerrummanrs.supabase.co/functions/v1/api/webhooks/revenuecat`) ;
- Authorization header : exactement la valeur de `REVENUECAT_WEBHOOK_AUTHORIZATION`.

Clique « Send test event » : la réponse doit être 200.

## 7. L'adresse api.jauge.io (quand le domaine est acheté)

1. Achète `jauge.io` et ajoute-le à Cloudflare (gratuit), en suivant l'assistant de Cloudflare.
2. Dans `cloudflare/api-proxy/wrangler.toml`, remplace `<project-ref>` par `ynvaaswsenerrummanrs`.
3. Dans le Terminal :

```
cd cloudflare/api-proxy
npx wrangler login
npx wrangler secret put JAUGE_PROXY_SECRET
npx wrangler deploy
```

`secret put` te demande la valeur : la même que `JAUGE_PROXY_SECRET` de l'étape 4. Vérifie :
`curl -s https://api.jauge.io/health`.

## 7 bis. Les e-mails de connexion (Brevo)

Les liens de connexion partent par **Brevo**, de `connexion@jauge.io` (« Jauge »), avec le modèle aux couleurs de Jauge
en français ou en anglais selon la langue du téléphone. Le détail de chaque écran est dans `docs/supabase.md`, section
« E-mails de connexion avec Brevo ». En bref, dans l'ordre :

1. **Brevo** (brevo.com, offre gratuite) > roue > Expéditeurs, domaines et IP dédiées > **Domaines** > ajoute
   `jauge.io`.
2. **Cloudflare > jauge.io > DNS** : crée exactement les entrées données par Brevo : le **code Brevo** (`TXT` sur `@`,
   `brevo-code:…`), la **DKIM** (`CNAME` `brevo1._domainkey` et `brevo2._domainkey`, nuage gris, ou `TXT`
   `mail._domainkey`) et la **DMARC** (`TXT` sur `_dmarc`, par exemple `v=DMARC1; p=none; rua=mailto:contact@jauge.io`).
   Puis « Vérifier » sur Brevo : le domaine passe à « Authentifié ».
3. Brevo > **Expéditeurs** : ajoute `Jauge` / `connexion@jauge.io`.
4. Brevo > **SMTP & API** > SMTP > **Générer une nouvelle clé SMTP**. Garde l'identifiant (`…@smtp-brevo.com`) et la clé
   (montrée une fois, jamais dans le dépôt). Coupe le suivi des ouvertures et des clics des e-mails transactionnels.
5. **Supabase > Authentication > Emails > SMTP Settings** : Enable custom SMTP, expéditeur `connexion@jauge.io`, nom
   `Jauge`, hôte `smtp-relay.brevo.com`, port `587`, intervalle `60`, Username = identifiant Brevo, Password = clé SMTP.
   Puis Authentication > Rate Limits : 100 e-mails par heure.
6. **Supabase > Authentication > Emails > Templates** > **Magic Link** : Subject =
   `{{ if and .Data (eq .Data.locale "en") }}Your Jauge sign-in link{{ else }}Ton lien de connexion à Jauge{{ end }}`,
   Message body (onglet Source) = le contenu de `supabase/templates/magic_link.html`
   (`pbcopy < supabase/templates/magic_link.html`). Même chose pour **Confirm signup** avec `confirmation.html`.
   **Email OTP Expiration** = `3600` (le lien vaut 1 heure).
7. Mets le logo en ligne à `https://jauge.io/email/logo-jauge-96.png` (fichier
   `supabase/templates/assets/logo-jauge-96.png`) quand le site existe ; avant, l'e-mail affiche le nom en texte.
8. **Test** : connexion par e-mail depuis l'app sur un téléphone en français, puis en anglais ; l'e-mail arrive de
   « Jauge », le bouton « Me connecter » ouvre l'app ; dans Gmail > Afficher l'original, SPF, DKIM et DMARC sont à
   `PASS` et le lien commence par `https://ynvaaswsenerrummanrs.supabase.co/auth/v1/verify`.

Ce que ça coûte : 0 € jusqu'à 300 e-mails par jour (offre gratuite de Brevo).

## 8. Mode simulé, puis vraies sources

- `JAUGE_PROVIDER_MODE=simulated` : tout marche, mais les offres du marché, les avis et la lecture des offres sont
  **inventés** (toujours les mêmes pour une même offre). Parfait pour tester l'app, **pas pour le public**.
- **Vraies offres (Apify)** :
  1. Crée un compte sur apify.com. L'offre gratuite donne 5 $ par mois (assez pour essayer) ; prends **Starter
     (39 $ par mois, 39 $ d'usage compris)** avant le lancement public, et mets alors `APIFY_PRICING_TIER=BRONZE`.
  2. Apify > Settings > API & Integrations : copie le jeton (« Personal API token ») dans `APIFY_TOKEN`.
  3. Apify > Settings > Usage & billing : fixe une **limite de dépense mensuelle** (par exemple 60 $). C'est une
     deuxième sécurité, en plus du plafond du jour de Jauge.
  4. Remplis `APIFY_WEBHOOK_SECRET` (`openssl rand -hex 32`) ; `APIFY_WEBHOOK_URL` est déjà rempli.
  5. Passe `JAUGE_PROVIDER_MODE=live`, renvoie les secrets (étape 4) et redéploie (étape 5).
  6. Essaie un lien LinkedIn dans l'app, puis regarde Apify > Runs : un lancement LinkedIn et un HelloWork, de
     quelques centimes chacun.
- Sans clé d'IA (étape 8 bis), en mode `live` les **captures et textes** ne sont pas lus (l'app propose la saisie) ;
  les liens, eux, marchent.
- Ce que coûte chaque source, ce qui est allumé par défaut et comment changer : `docs/backend/sources.md`.

## 8 bis. L'IA et les avis Reddit / X

Détails et prix : `docs/backend/ia.md`.

1. **Gemini (par défaut)** : va sur https://aistudio.google.com, crée une clé API, puis **active la facturation**
   sur son projet Google Cloud (bouton « Set up billing »). Sans facturation, Google peut se servir des textes envoyés
   pour améliorer ses produits, et c'est interdit pour des utilisateurs européens. Mets la clé dans `GEMINI_API_KEY`.
2. **Ou Mistral (données en Europe, un peu moins cher)** : crée une clé sur https://console.mistral.ai, puis dans
   https://admin.mistral.ai > Privacy, **coupe** « Anonymous improvement data ». Mets la clé dans `MISTRAL_API_KEY` et
   `AI_PROVIDER=mistral`. Tu peux passer de l'une à l'autre quand tu veux : un secret à changer, rien à redéployer
   d'autre que les secrets.
3. **Avis Reddit et X** : ils passent par Apify avec le même `APIFY_TOKEN`. Une recherche par entreprise tous les
   30 jours, environ 0,05 $ chacune. Pour payer moins, `JAUGE_SOCIAL_SOURCES=x` (X seulement, environ 0,005 $).
   Pour couper les avis : `JAUGE_SOCIAL_SOURCES=none`.
4. Renvoie les secrets (étape 4), applique la migration (`bash supabase/deploy.sh` le fait) et regarde les journaux :
   des lignes `ai_call` (avec le nombre de jetons et le coût) doivent apparaître à chaque analyse.

Coût de l'IA : environ 0,001 $ par analyse (lecture du texte et conseil), plus environ 0,05 $ la première fois qu'une
entreprise est analysée dans le mois (avis Reddit et X). Tout compte dans la limite du jour.

## 9. Surveiller

- `https://api.jauge.io/health` : `ok` ou `degraded` (file en retard ou budget du jour atteint).
- Les alertes arrivent sur ton ntfy : 80 % du budget du jour, budget atteint, file de travail en retard, suppression
  de compte bloquée.
- Supabase > Edge Functions > api > Logs : une ligne par requête, sans donnée personnelle.

## 10. En cas de problème

- **Mettre l'API en pause** (message « Jauge fait une pause » dans l'app) :
  `supabase secrets set JAUGE_MAINTENANCE=true`, puis `false` pour reprendre.
- **Revenir à la version d'avant** : `git checkout <version d'avant>` puis `bash supabase/deploy.sh`. Les migrations
  ne s'annulent pas toutes seules : demande de l'aide avant de toucher à la base.
- **Couper les dépenses tout de suite** : `supabase secrets set JAUGE_DAILY_SPEND_LIMIT_USD=0`. L'app continue avec ce
  qui est déjà en cache et en base.

## Ce que ça coûte

| Poste | Au début | Avec 1 000 utilisateurs |
|---|---|---|
| Supabase | 0 € (gratuit) | 25 $ par mois (Pro) conseillé avant le lancement public : sauvegardes chaque jour, plus de place |
| Cloudflare (proxy) | 0 € | 0 € |
| Apify (collecte des offres) | 0 € (5 $ offerts par mois) | 39 $ par mois (offre Starter, 39 $ d'usage compris) ; environ 210 $ d'usage si rien n'est plafonné, 90 $ au plus avec le plafond de 3 $ par jour |
| IA (lecture des offres, conseils, résumé des avis) | quelques centimes | 3 à 5 $ par mois |
| Avis Reddit et X (Apify) | quelques centimes | 20 à 40 $ par mois (moins de 5 $ avec X seulement), dans le plafond du jour |
| Domaine jauge.io | environ 40 $ par an | idem |
| E-mails de connexion (Brevo) | 0 € (300 par jour offerts) | 0 € tant qu'on reste sous 300 par jour, sinon une offre payante de Brevo (voir leurs tarifs) |

Une analyse coûte 0 $ quand l'offre a été analysée par quelqu'un dans les 7 derniers jours, presque rien d'ordinaire
(la page de l'offre est lue gratuitement), et 0,05 à 0,20 $ quand son marché (même métier, même département) n'a pas
été étudié depuis 7 jours. Le plafond du jour (`JAUGE_DAILY_SPEND_LIMIT_USD`) garantit que la facture des sources ne dépasse jamais ce montant
multiplié par 30. Détail des calculs : `docs/backend/sources.md` et `docs/backend/ARCHITECTURE.md` (« Costs »).
