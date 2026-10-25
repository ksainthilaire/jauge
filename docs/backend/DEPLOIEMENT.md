# Mettre l'API Jauge en ligne

Ce guide est pour toi, propriétaire du projet. Rien n'a été mis en ligne pour toi : tu fais chaque étape une fois, dans
l'ordre. Compte une heure la première fois.

## En bref

- L'API tourne **sur Supabase** (ton projet `ynvaaswsenerrummanrs`), dans une seule fonction appelée `api`.
- Les données sont dans la base Supabase (tables créées par les fichiers de `supabase/migrations/`).
- L'adresse publique `https://api.jauge.io` passe par **Cloudflare** (gratuit), qui renvoie vers Supabase.
- Tant que les connecteurs (Apify, IA) ne sont pas branchés, l'API répond avec des **données simulées** : ne l'ouvre
  pas au public avant (voir « Mode simulé »).

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
| `JAUGE_PROVIDER_MODE` | `simulated` jusqu'aux connecteurs, puis `live` | oui |
| `JAUGE_DAILY_SPEND_LIMIT_USD` | dépense maximum par jour en dollars (3 par défaut, soit 90 $ par mois au plus) | non |
| `JAUGE_ALERT_WEBHOOK_URL` | une adresse ntfy (par exemple `https://ntfy.sh/un-nom-secret`) pour recevoir les alertes de coût | conseillé |
| `REVENUECAT_WEBHOOK_AUTHORIZATION` | une valeur au hasard, par exemple `Bearer ` suivi de `openssl rand -hex 24` (la même ira dans RevenueCat, étape 6) | oui |
| `REVENUECAT_SECRET_API_KEY` | ta clé secrète RevenueCat `sk_...` (RevenueCat > API keys) | oui |
| `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY_P8` | ta clé « Sign in with Apple » (Apple Developer > Keys), le contenu du fichier .p8 sur une ligne avec `\n` | oui pour la suppression des comptes Apple |
| `APPLE_CLIENT_ID` | `io.jauge.app` | oui |
| `SUPABASE_JWT_SECRET` | seulement si ton projet signe encore ses jetons avec l'ancien secret (Project Settings > API > JWT) | non |

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

## 8. Mode simulé, puis vraies sources

- `JAUGE_PROVIDER_MODE=simulated` : tout marche, mais les offres du marché, les avis et la lecture des offres sont
  **inventés** (toujours les mêmes pour une même offre). Parfait pour tester l'app, **pas pour le public**.
- Quand les connecteurs Apify et IA sont livrés (phase suivante), ajoute leurs clés (`APIFY_TOKEN`,
  `GEMINI_API_KEY`...), passe `JAUGE_PROVIDER_MODE=live`, renvoie les secrets (étape 4) et redéploie (étape 5).

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
| Apify (collecte des offres) | 0 € (5 $ offerts par mois) | 39 $ par mois (offre Starter, usage inclus), plafonné par la limite du jour |
| IA (lecture des offres, résumé des avis) | quelques centimes | 2 à 5 $ par mois |
| Domaine jauge.io | environ 40 $ par an | idem |

Une analyse coûte 0 $ quand l'offre a été analysée par quelqu'un dans les 7 derniers jours, quelques centimes
d'ordinaire, et 0,13 à 0,20 $ quand ni l'offre ni son marché (même métier, même département) ne sont connus. Le
plafond du jour (`JAUGE_DAILY_SPEND_LIMIT_USD`) garantit que la facture des sources ne dépasse jamais ce montant
multiplié par 30. Détail des calculs : `docs/backend/ARCHITECTURE.md`, partie « Costs ».
