# Site jauge.io

Site statique de Jauge : la page de téléchargement de l'app, les pages exigées par Google Play et l'App Store, et les
fichiers de vérification des liens d'application. Pas de framework, pas de build : HTML, CSS (`assets/site.css`) et un
peu de JavaScript sans dépendance (`assets/site.js`). Déployable tel quel sur Cloudflare Pages.

## Pages

| Adresse | Fichier | Rôle |
|---|---|---|
| `/` | `index.html` | Accueil : la jauge animée, les bénéfices, comment ça marche, les écrans, les prix, la FAQ, les badges des stores et le QR code. |
| `/telecharger/` | `telecharger/index.html` | Détecte Android, iPhone ou ordinateur et propose le bon bouton (sans JavaScript : les deux). |
| `/a/{id}` | `a/_index.html` | Page de partage d'une analyse. Servie par la réécriture de `_redirects` ; lit `GET https://api.jauge.io/public/analyses/{id}` (sans jeton, CORS ouvert à `https://jauge.io`, voir `docs/api.md`). `noindex`. |
| `/confidentialite/` | généré | Politique de confidentialité (lien « Privacy policy » des deux stores). |
| `/conditions/` | généré | Conditions d'utilisation (lien CGU de l'App Store et du paywall). |
| `/supprimer-mon-compte/` | généré | Suppression du compte sans l'app (lien « Delete account » obligatoire sur Google Play). |
| `/mentions-legales/` | généré | Mentions légales. |
| `/cookies/` | généré | Le site n'a aucun cookie ni traceur : pas de bandeau. |
| `/support/` | généré | Contact et questions d'aide. |
| `/accessibilite/` | généré | Déclaration d'accessibilité, niveau visé WCAG 2.1 AA. |
| `/404.html` | `404.html` | Page introuvable (Cloudflare Pages la sert automatiquement). |

Les pages « généré » sont écrites par `python3 tools/legal-pages/generate.py` : les textes légaux viennent de l'app
(`feature/account/.../values/legal.xml` et `legal_identity.xml`, source unique), les autres de
`tools/legal-pages/pages/*.html`, le tout dans le gabarit `tools/legal-pages/layout.html`. **Ne modifie pas ces
`index.html` à la main** : change la source puis relance le générateur.

Autres fichiers : `robots.txt`, `sitemap.xml`, `llms.txt` et `llms-full.txt` (description de Jauge pour les moteurs et
assistants IA), `site.webmanifest`, `favicon.ico`, `assets/icons/` (SVG, 180, 192, 512), `assets/img/og.png` (image
Open Graph 1200 × 630), `assets/img/*.webp` (captures de l'app redimensionnées), `email/logo-jauge-96.png` (logo des
e-mails de connexion, voir `docs/supabase.md`), `.well-known/` (liens d'application), `_headers`, `_redirects`.

## Déployer sur Cloudflare Pages

1. Cloudflare > Workers & Pages > Create > Pages > **Connect to Git** : ce dépôt, branche `main` (ou `develop` pour
   un aperçu). **Build command : vide. Build output directory : `web`.** Aucune variable d'environnement.
2. Après le premier déploiement, Custom domains : ajoute `jauge.io` (le domaine est déjà sur Cloudflare, l'enregistrement
   DNS est créé tout seul) et `www.jauge.io` (puis, dans Rules > Redirect Rules, redirige `www.jauge.io/*` vers
   `https://jauge.io/$1`, 301). Les liens d'application exigent `https://jauge.io` **sans redirection** : le domaine
   nu doit être le site lui-même.
3. Vérifie : `https://jauge.io/`, `https://jauge.io/.well-known/apple-app-site-association` (doit répondre en
   `application/json`), `https://jauge.io/a/test` (la page de partage, « lien erroné » tant que l'API n'est pas en ligne).

`_headers` pose la sécurité (CSP stricte sans script ni style en ligne, HSTS, nosniff, Referrer-Policy,
Permissions-Policy, pas d'iframe) et le cache (images et icônes un an, CSS et JS une heure, `.well-known` une heure).
Si tu ajoutes une ressource externe (police, script), ajoute son domaine dans la CSP. `_redirects` réécrit `/a/*` vers la
page de partage et redirige quelques anciennes adresses.

## À remplacer avant publication

Chercher `REPLACE_WITH` et `[à compléter` dans `web/` (`bash web/verifier.sh` les liste) :

| Quoi | Où | Valeur |
|---|---|---|
| Empreinte SHA-256 de la clé de signature Play (et de la clé d'upload) | `.well-known/assetlinks.json` | Play Console › Intégrité de l'app › Signature de l'app |
| Team ID Apple | `.well-known/apple-app-site-association` | Apple Developer › Membership (`REPLACE_WITH_TEAM_ID` devient `ABCDE12345.io.jauge.app`) |
| Lien et identifiant App Store | `index.html` (2 badges), `telecharger/index.html` (badge et lien) ; balise `apple-itunes-app` de `index.html`, `telecharger/index.html` et `a/_index.html` | `https://apps.apple.com/fr/app/jauge/id1234567890` et `app-id=1234567890` (App Store Connect › Informations sur l'app › Apple ID) |
| Lien Google Play | déjà en place : `https://play.google.com/store/apps/details?id=io.jauge.app` | à vérifier une fois la fiche publiée |
| Médiateur de la consommation | `conditions/` (texte `[à compléter avant publication]`, source `legal_identity.xml` de l'app) | nom et adresse du médiateur choisi |
| Adresse de support | `support@jauge.io` n'existe pas : le site utilise `contact@jauge.io` (support) et `privacy@jauge.io` (données), comme l'app | créer ces deux boîtes (Cloudflare Email Routing, voir `docs/supabase.md`) |

Les mentions légales (éditeur, SIREN, adresse, hébergeurs) viennent de `legal_identity.xml` de l'app et sont déjà
remplies ; l'hébergeur du site (Cloudflare, Inc.) est ajouté par le générateur. Pas de DPO désigné (entrepreneur
individuel) : la politique renvoie à `privacy@jauge.io`.

## Vérifier

```
bash web/verifier.sh                      # liens internes, fichiers, h1, SEO, JSON-LD, sitemap, poids de l'accueil
python3 tools/site/serveur_local.py &     # http://127.0.0.1:8812, imite les réécritures de Cloudflare Pages
node tools/site/captures.js               # captures dans web/.captures/ (390 et 1400 px, clair et sombre), console, liens, cibles tactiles
```

`captures.js` et `rendre_images.js` utilisent le Chromium de Playwright (`PLAYWRIGHT_CORE` et `PLAYWRIGHT_CHROMIUM`
pour changer les chemins). `web/.captures/` est ignoré par git.

## Régénérer

- Pages légales et d'aide : `python3 tools/legal-pages/generate.py`.
- Icônes, `favicon.ico` et image Open Graph : `node tools/site/rendre_images.js` (sources `assets/icons/favicon.svg` et
  `tools/site/og.html`).
- QR code de l'accueil : `python3 tools/site/qr_svg.py "https://jauge.io/telecharger"` puis coller le SVG dans
  `index.html` (bloc `.qr`).
- Captures de l'app : `cwebp -q 82 -resize 480 0 docs/screenshots/<capture>.png -o web/assets/img/<nom>.webp`
  (toutes au format 480 × 1041 pour le cadre de téléphone).
