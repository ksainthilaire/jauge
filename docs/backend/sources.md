# Sources des offres (Apify et lecture des pages)

Ce document dit d'où viennent les offres, ce qui est payant, combien coûte une analyse pour de vrai et comment
allumer ou éteindre chaque source. Le code est dans `supabase/functions/api/src/providers/apify/` (sources du marché)
et `src/providers/offers/` (lecture d'une offre).

## En bref

- **Lire l'offre de l'utilisateur** coûte presque toujours **0 $** : Jauge ouvre la page une fois et lit les données
  que le site prépare pour Google (bloc `JobPosting`). Apify ne sert que pour Indeed (qui refuse cette lecture), et
  l'IA seulement pour les pages sans ces données.
- **Étudier le marché** (offres du même métier dans le même département) passe par Apify : LinkedIn et HelloWork pour
  chaque nouveau marché, Indeed en plus quand le marché est maigre. Un marché étudié sert **7 jours** à toutes les
  analyses de ce métier et de ce département.
- Chaque lancement Apify est **plafonné à son estimation** (nombre de résultats et montant maximum fixés à Apify) et
  **réservé** dans le budget du jour pendant qu'il tourne : la dépense du jour ne peut pas dépasser
  `JAUGE_DAILY_SPEND_LIMIT_USD`.
- Rien n'est payé tant que `JAUGE_PROVIDER_MODE` n'est pas `live` : une clé seule n'allume rien. En mode `live` sans
  `APIFY_TOKEN`, les pages restent lues (gratuit), il n'y a juste pas de collecte du marché.

## Les sources

| Source | Actor Apify | Rôle | Prix (offre Starter) | Par défaut |
|---|---|---|---|---|
| LinkedIn | `curious_coder/linkedin-jobs-scraper` | Offres du marché **avec le nombre de candidats** (la base de l'estimation) | 0,001 $ par offre + 0,00005 $ par lancement | allumée |
| HelloWork | `sbzh/hellowork-jobs-scraper` | Offres françaises, département et salaire souvent donnés | 0,0008 $ par offre + 0,00005 $ par lancement | allumée |
| Indeed | `misceres/indeed-scraper` | Beaucoup d'offres et de salaires, pas de candidats | 0,005 $ par offre (0,003 $ en offre Business) | complément : seulement si LinkedIn et HelloWork trouvent moins de 25 offres |
| Glassdoor | `getdataforme/glassdoor-jobs-scraper` | Fourchettes de salaires (données par l'employeur ou estimées par Glassdoor) | 0,05 $ par lancement + 0,009 $ par offre | éteinte (cher) |
| Welcome to the Jungle | `saswave/welcome-to-the-jungle-scraper` | Start-up et tech, salaires parfois | 0,0009 $ par offre + 0,00005 $ par lancement | éteinte (voir plus bas) |

Prix relevés sur les pages Apify le 25/10/2026 ; ils peuvent changer. Les cinq actors sont « pay per event » : on
paie les résultats rendus et le lancement, rien d'autre (le calcul et les proxys sont inclus). Le palier de prix suit
ton offre Apify : Free = `FREE` (LinkedIn y coûte 0,002 $ par offre), Starter = `BRONZE`, Scale = `SILVER`,
Business = `GOLD`. Indique-le dans `APIFY_PRICING_TIER`.

**Welcome to the Jungle** : quand tu as choisi les sources, cet actor se louait 70 $ par mois. Depuis août 2026 il
est facturé à l'offre (0,0009 $), donc il n'est plus cher. Il reste éteint comme tu l'avais demandé ; pour l'allumer,
ajoute `welcome_to_the_jungle` à `JAUGE_APIFY_SOURCES`. Sa recherche utilise l'adresse
`welcometothejungle.com/fr/jobs?query=...&aroundQuery=...` : vérifie le premier lancement dans la console Apify.

**Glassdoor** : l'actor n'a pas de réglage du nombre d'offres, c'est le plafond de Jauge (20 offres, 0,23 $ au plus)
qui l'arrête. Il ne rend pas de date de publication. À n'allumer que si les salaires manquent vraiment.

## Ce que Jauge demande à chaque actor

| Source | Entrée envoyée | Limites |
|---|---|---|
| LinkedIn | `keywords` (intitulé sans H/F ni contrat), `location` « Ville, France », `distance` en miles (5, 10, 25, 50 ou 100, le plus proche du rayon), `datePosted: pastMonth`, `scrapeCompany: false`, `limitPerSource` | 100 offres au plus, 240 s |
| HelloWork | `keywords`, `location`, `distance` (5, 10, 20 ou 50 km), `period: 30d`, `sort_by: date`, `limit` | 100 au plus (l'actor rend parfois un peu plus : `maxItems` arrête la facturation) |
| Indeed | `position`, `country: FR`, `location`, `maxItemsPerSearch`, `parseCompanyDetails: false` | 30 au plus |
| Glassdoor | `job_name`, `location` « Ville, France », proxy résidentiel Apify | 20 au plus |
| Welcome to the Jungle | `search_job` (une adresse de recherche), `max_page: 1` (environ 30 offres) | 60 au plus |

Chaque lancement porte aussi `maxItems` (nombre d'offres facturées au plus), `maxTotalChargeUsd` (montant maximum,
égal à l'estimation), `timeout` et `memory` (1 Go). Jauge ne lit dans les résultats que les champs utiles
(`fields=`) ; la description sert seulement à repérer les compétences (liste de mots connus) et n'est jamais
enregistrée. Les offres à l'étranger, sans titre ou sans identifiant sont écartées.

## Lire l'offre de l'utilisateur, du moins cher au plus cher

1. **La page elle-même** (gratuit) : une requête HTTP, 8 s au plus, 2 Mo au plus, redirections vérifiées (jamais vers
   une adresse privée). Jauge lit le bloc `JobPosting` : intitulé, entreprise, ville et département (code postal),
   contrat, télétravail, date, salaire (converti en milliers d'euros bruts par an), compétences. Sur LinkedIn, le
   nombre de candidats affiché est lu dans la page. Marche pour LinkedIn, HelloWork, France Travail, Welcome to the
   Jungle, Apec et la plupart des sites carrières.
2. **L'actor Apify du site**, seulement si la page refuse Jauge (Indeed) et si le budget du jour le permet :
   0,005 $ pour Indeed, 0,001 $ pour Welcome to the Jungle. Le lancement attend la fin (40 s au plus, dans la file de
   travail, jamais dans une requête de l'app), puis l'arrête.
3. **L'IA** sur le texte visible de la page, si la page n'a pas de bloc `JobPosting` (environ 0,001 $ avec Gemini
   3.1 Flash-Lite, voir `ia.md`). Les faits écrits en clair (« 47 candidats », « il y a 3 jours », « 35 000 - 40 000 € par an », « CDI »,
   « Télétravail partiel ») complètent ce que l'IA a laissé vide, sans jamais la contredire.

Si rien ne marche : offre retirée (404) = « offre illisible » ; site qui refuse sans solution = l'app propose la
capture ou la saisie. Dans les deux cas l'analyse gratuite de la semaine est rendue. Le **texte lu sur le
téléphone** passe par l'étape 3 directement.

LinkedIn renvoie parfois une page de connexion aux robots : Jauge s'annonce honnêtement
(`JaugeLinkPreview`, réglable par `JAUGE_PAGE_USER_AGENT`) et propose alors la capture. Jauge lit une seule page,
celle que l'utilisateur a collée, comme un aperçu de lien : jamais d'exploration du site.

## Déroulé d'une collecte du marché

```
étape market       marché connu depuis moins de 7 jours ? -> réutilisé, rien n'est payé
                   sinon : LinkedIn et HelloWork lancés (asynchrones) si le budget du jour le permet,
                   leur estimation est réservée (source_collections.reserved_micro_usd)
étape market_wait  la file de travail interroge les lancements toutes les 5 s (ou le webhook Apify la réveille)
                   fin d'un lancement : offres enregistrées, coût réel journalisé (provider_costs), réservation rendue
                   moins de 25 offres en tout ? -> Indeed lancé à son tour (une fois)
                   15 s avant l'échéance (75 s) : calcul avec ce qui est arrivé, marché gardé 1 jour seulement
après              un lancement encore en cours n'est pas perdu : le webhook Apify (ou la maintenance du jour)
                   enregistre ses offres et son coût réel ; au bout d'une heure sans fin, il est abandonné
```

**Délais et reprises** : les lectures de l'API Apify sont refaites deux fois (0,5 s puis 1 s) en cas de coupure, de
429 ou de 5xx ; un lancement n'est refait que sur 429 ou 5xx (après une coupure réseau, il a pu être créé et facturé).
Une erreur pendant l'attente n'arrête pas la collecte : on réessaie au tour suivant, jusqu'à l'échéance. Un jeton
faux (401), un compte sans crédit (402) arrêtent la source tout de suite : le marché est calculé sans elle.

**Dédoublonnage** : chaque source garde ses lignes (`collected_offers`, une par source et identifiant). À la lecture
du marché, une même offre publiée sur plusieurs sites (même métier, même entreprise, même ville, à moins de 21 jours
d'écart) ne compte qu'une fois : on garde la ligne LinkedIn (nombre de candidats), on complète salaire, contrat et
compétences avec les autres, et la date la plus ancienne. Sans nom d'entreprise, rien n'est fusionné.

## Ce que coûte une analyse, pour de vrai

Offre Starter (`BRONZE`), 60 offres demandées par source :

| Cas | Ce qui est payé | Coût |
|---|---|---|
| Offre déjà analysée il y a moins de 7 jours | rien | 0 $ |
| Nouvelle offre, marché connu (le cas le plus courant) | lecture de la page | 0 $ (lien LinkedIn, HelloWork, France Travail...), 0,005 $ (lien Indeed), 0,001 $ (texte ou page sans données, IA) |
| Nouvelle offre, nouveau marché | + LinkedIn (60 offres, 0,060 $) + HelloWork (60 offres, 0,048 $) | 0,11 $ au plus |
| Nouveau marché maigre | + Indeed (30 offres au plus, 0,15 $) ; mais LinkedIn et HelloWork rendent alors peu d'offres | 0,05 à 0,20 $ |

Le coût réel est celui des offres rendues, pas des offres demandées : un marché de 20 offres coûte environ 0,04 $.
Les avis Reddit et X, le conseil et le résumé par l'IA s'ajoutent à part (`ia.md`).

Pour 1 000 utilisateurs (hypothèses de `ARCHITECTURE.md` : environ 2 600 analyses et 1 500 nouveaux marchés par
mois, un tiers maigres) : environ 1 500 x 0,10 $ + 500 x 0,12 $ = **210 $ par mois** sans plafond. Avec le plafond par
défaut de 3 $ par jour, Jauge étudie environ 25 nouveaux marchés par jour ; au-delà, elle répond avec ce qu'elle a
déjà (fiabilité plus basse) jusqu'à minuit UTC. Pour tout couvrir au lancement, mets 8 $ par jour.

## Allumer, éteindre, régler

| Réglage (secret Supabase) | Effet | Par défaut |
|---|---|---|
| `APIFY_TOKEN` | Jeton de ton compte Apify (Apify > Settings > API & Integrations), le même que pour les avis Reddit et X. Sans lui : aucune collecte, aucune dépense Apify. | vide |
| `JAUGE_PROVIDER_MODE` | `live` allume les vraies sources ; `simulated` (ou vide) garde les données inventées, même avec un jeton | `simulated` |
| `JAUGE_APIFY_SOURCES` | Sources lancées pour chaque nouveau marché, séparées par des virgules : `linkedin`, `hellowork`, `indeed`, `glassdoor`, `welcome_to_the_jungle` | `linkedin,hellowork` |
| `JAUGE_APIFY_COMPLEMENTARY_SOURCES` | Sources lancées seulement pour les marchés maigres (vide pour aucune) | `indeed` |
| `JAUGE_APIFY_OFFERS_PER_SOURCE` | Offres demandées à chaque source (10 à 100) | 60 |
| `APIFY_PRICING_TIER` | Palier de prix de ton offre Apify : `FREE`, `BRONZE` (Starter), `SILVER`, `GOLD` | `BRONZE` |
| `APIFY_WEBHOOK_URL` | Adresse que Apify appelle à la fin d'un lancement : `https://ynvaaswsenerrummanrs.supabase.co/functions/v1/api/webhooks/apify` (directe, pas par Cloudflare) | vide (attente par la file seule) |
| `APIFY_WEBHOOK_SECRET` | Valeur au hasard (`openssl rand -hex 32`), envoyée par Apify dans l'en-tête `x-jauge-apify-secret` | vide |
| `JAUGE_DAILY_SPEND_LIMIT_USD` | Plafond du jour pour toutes les sources payantes | 3 |

Exemples :

- Couper Indeed : `supabase secrets set JAUGE_APIFY_COMPLEMENTARY_SOURCES=` (vide).
- Ajouter Welcome to the Jungle : `supabase secrets set JAUGE_APIFY_SOURCES=linkedin,hellowork,welcome_to_the_jungle`.
- Tout couper sans redéployer : `supabase secrets set JAUGE_DAILY_SPEND_LIMIT_USD=0`.

## Où voir ce qui a été dépensé

- `provider_costs` : une ligne par lancement terminé (source, offres, coût en micro dollars).
- `source_collections` : état de chaque source pour chaque marché (`running`, `succeeded`, `failed`, `skipped` avec
  `daily_limit`), coût et réservation en cours.
- `GET /health` avec le secret interne : dépense du jour (réservations comprises) et plafond.
- Console Apify > Runs : le détail de chaque lancement (Jauge n'y met aucune donnée d'utilisateur : seulement un
  métier et une ville).

## Tests

Aucun test n'appelle Internet. `src/providers/apify/fixtures/` contient des réponses Apify enregistrées (construites
à partir des exemples et des schémas publiés sur la page de chaque actor) et `src/providers/offers/fixtures/` des
pages d'offres. Les tests vérifient les entrées des actors, la lecture de chaque source, les prix, les reprises, les
lancements trop longs, le plafond du jour avec les réservations, le webhook, le dédoublonnage et la lecture des pages.
