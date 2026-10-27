# À faire par toi

Ce que je ne peux pas faire à ta place : ça touche tes comptes, tes clés ou un vrai téléphone.

## Sécurité (audit du 24/10/2026, détails dans [securite.md](securite.md))

1. **Créer la clé de signature Android (une seule fois, à garder précieusement).**
   - Dans la Play Console, active « Play App Signing » (Google garde la vraie clé).
   - Crée ta clé d'envoi sur ton Mac :
     `keytool -genkeypair -v -keystore ~/cles/jauge-upload.jks -alias jauge -keyalg RSA -keysize 4096 -validity 10000`
   - Range le fichier `.jks` et son mot de passe dans ton gestionnaire de mots de passe, **jamais** dans le dépôt
     (le dépôt ignore déjà les fichiers `.jks`).
   - Dis-moi quand c'est fait : je brancherai la signature en lisant le mot de passe depuis `local.properties`.

2. **Tester la version release sur ton téléphone.** Elle est maintenant compressée et protégée (R8). Vérifie :
   connexion, achat d'essai, partage d'un lien, notification. Sans API en ligne, l'app affiche des messages
   « service indisponible » : c'est normal, le mode sans API ne marche plus qu'en version debug.

3. **Supabase, dans le tableau de bord en ligne :**
   - Applique la nouvelle migration (`supabase db push`) : elle vérifie le format des jetons de notification et
     limite chaque compte à 10 téléphones.
   - Le secret `JAUGE_WEBHOOK_SECRET` doit faire **au moins 32 caractères** (`openssl rand -hex 32`), sinon l'envoi
     des notifications est refusé. Mets la même valeur dans le coffre (Vault) `jauge_webhook_secret`.
   - Authentication > URL Configuration : garde seulement `io.jauge.app://login-callback` (et
     `https://jauge.io/auth/callback` si tu fais la page web) dans les « Redirect URLs ». Rien d'autre.
   - Authentication > Rate Limits : laisse les limites par défaut (ou plus strictes), surtout pour les emails.
   - Si tu utilises encore les anciennes clés « anon / service_role », passe aux nouvelles clés
     (`sb_publishable_` pour l'app, `sb_secret_` pour les serveurs) puis désactive les anciennes.

4. **Clés à ne jamais mettre dans l'app ni dans le dépôt :** la clé RevenueCat `sk_...`, la clé `sb_secret_...`, la clé
   Firebase (fichier JSON du compte de service) et la clé APNs `.p8`. Elles vont seulement dans les secrets Supabase
   (`supabase secrets set`). Si l'une d'elles a été copiée un jour dans un message, un mail ou un fichier partagé,
   régénère-la dans le tableau de bord concerné.

5. **Avant la première publication :** demande-moi d'activer la vérification des bibliothèques
   (`./gradlew --write-verification-metadata sha256 help`). Je ne l'active pas maintenant pour ne pas bloquer les
   autres développeurs qui ajoutent des bibliothèques.

6. **iOS, à la première archive dans Xcode :** vérifie que l'app archivée a `aps-environment = production` (Xcode le
   met tout seul avec un profil de distribution).

## Publication Google Play et App Store (audit du 24/10/2026, détails dans [google-play.md](google-play.md) et [app-store.md](app-store.md))

L'app a été lancée pour de vrai sur un téléphone Android simulé : tous les écrans marchent, les défauts trouvés sont
corrigés. Voici ce qui reste, dans l'ordre.

### Avant tout

1. **Mettre l'API Jauge en ligne** (`https://api.jauge.io`). La version publiée n'utilise jamais les données
   d'exemple : sans API, les testeurs de Google et d'Apple ne verraient que « Pas de connexion » et refuseraient
   l'app.
2. **Identité de l'éditeur** : remplie le 24/10/2026 avec tes réponses (Keny SAINT HILAIRE, EI micro-entreprise,
   SIREN 893 324 848, 149, avenue du Maine, 75014 Paris, hébergeur Supabase Pte. Ltd., âge minimum 18 ans) dans
   `feature/account/src/commonMain/composeResources/values/legal_identity.xml`. Il reste deux réponses :
   - **Médiateur de la consommation** : encore « [à compléter avant publication] ». C'est **obligatoire avant de
     vendre à des particuliers** (abonnement Jauge Plus) : adhère à un médiateur agréé (liste sur
     www.economie.gouv.fr/mediation-conso) et donne-moi son nom et son site.
   - **Franchise en base de TVA (article 293 B du CGI) : oui ou non ?** Si oui, je peux ajouter la mention « TVA non
     applicable, art. 293 B du CGI » ; si non, donne-moi ton numéro de TVA intracommunautaire. Pour l'instant, aucune
     ligne TVA n'est affichée.
   Après chaque changement de ce fichier, lance `python3 tools/legal-pages/generate.py` pour mettre à jour les pages
   web.
3. **Me dire qui lit les captures** : si les images partent chez un fournisseur d'IA (OpenAI, Anthropic, Google...),
   Apple exige de le nommer et de demander l'accord de l'utilisateur avant le premier envoi. Donne-moi le nom :
   j'ajoute l'écran d'accord et je complète la politique de confidentialité.
4. **Côté serveur, la suppression du compte** doit aussi effacer le client RevenueCat et révoquer la connexion Apple
   (c'est écrit dans `docs/api.md`, ligne `DELETE /v1/account`).

### Le site jauge.io

5. Publier le dossier `web` sur `https://jauge.io` (HTTPS, sans redirection) :
   - `/confidentialite/`, `/conditions/`, `/mentions-legales/` : les liens à donner aux stores ;
   - `/supprimer-mon-compte/` : le lien de suppression demandé par Google ;
   - `/.well-known/assetlinks.json` : remplace `REPLACE_WITH_THE_SHA256_FINGERPRINT...` par l'empreinte SHA-256 de la
     clé de signature (Play Console › Intégrité de l'app › Signature de l'app) ;
   - `/.well-known/apple-app-site-association` : remplace `REPLACE_WITH_TEAM_ID` par ton Team ID Apple.
6. Créer les boîtes `contact@jauge.io` et `privacy@jauge.io` (elles sont écrites dans l'app) et répondre aux demandes
   de suppression sous 30 jours.

### Comptes et abonnement

7. **Compte Google Play Console** (25 $ une fois) et **Apple Developer Program** (99 $ par an), à ton nom (entrepreneur individuel).
8. **Abonnement** `jauge_plus_monthly` à 6,99 € par mois avec 3 jours d'essai, dans la Play Console (offre de base
   `monthly`) et dans App Store Connect (groupe « Jauge Plus »), puis dans RevenueCat (produit, droit `plus`, offre
   `default`). Sur l'émulateur, le paywall affiche « Achats indisponibles » parce que le Test Store RevenueCat n'a
   encore **aucun produit** : ajoute le produit de test (voir `docs/revenuecat.md`) et je testerai l'achat et l'achat
   annulé. Signe aussi l'accord « Paid Apps » chez Apple et le profil de paiement chez Google.
9. **Supabase › Authentication › SMTP** : branche ton propre service d'e-mail (Brevo, Resend...). Le service
    d'e-mail fourni par Supabase n'envoie que quelques e-mails par heure et refuse les adresses de test : pendant
    l'essai, « Recevoir un lien » a répondu « On souffle un instant ».
10. **Apple** : sur l'identifiant `io.jauge.app`, active « Push Notifications », « Associated Domains » et « Sign in
    with Apple » ; crée la clé APNs `.p8`. Dans Xcode, garde la cible en « iPhone » seulement.

### Les fiches des stores

11. **Google Play** : remplis le formulaire « Sécurité des données » avec le tableau de `google-play.md` (section 4),
    le questionnaire de classification (section 7), public cible « 18 ans et plus » seulement, « pas de
    publicité », « accès à l'app : aucun compte nécessaire », l'URL de suppression et l'URL de la politique.
12. **App Store** : remplis « Confidentialité de l'app » avec le tableau de `app-store.md` (section 6), réponds
    « Non » au suivi, mets l'URL de la politique et un lien vers les conditions dans la description, ajoute les notes
    pour l'examinateur (section 9).
13. **Captures de store finales** : prends-les sur la version release (sans le bandeau orange « Mode sans API »),
    avec de vraies analyses : Google 1080 × 1920 au moins, Apple 1320 × 2868 (iPhone 6,9 pouces). Les captures de
    `docs/screenshots/device/` servent de contrôle, pas de fiche.
14. **Textes des fiches** : nom « Jauge », sous-titre (30 caractères, par exemple « Vois la foule avant de
    postuler »), description, mots-clés, catégorie (Productivité ou Entreprise), e-mail et site de contact.

### À décider (facultatif)

15. Deux couleurs de la marque sont un peu pâles pour les personnes qui voient mal (gris secondaire et texte blanc sur
    le bouton corail, détails dans `app-store.md` section 8). Je peux foncer légèrement le gris si tu es d'accord.

## IA et avis Reddit / X (25/10/2026, détails dans [../backend/ia.md](../backend/ia.md))

1. **Clé IA** : Gemini (par défaut) **avec la facturation activée** sur son projet Google (sans facturation, Google
   peut réutiliser les textes envoyés, ce qui est interdit pour des utilisateurs européens), ou Mistral avec
   « Anonymous improvement data » coupé dans admin.mistral.ai > Privacy. Étapes : `docs/backend/DEPLOIEMENT.md`,
   partie 8 bis.
2. **Avis Reddit et X : décision à prendre.** Depuis le 25/10/2026 (ta décision), ces avis ne sont **plus montrés**
   dans l'app ni sur la page de partage : ils servent seulement, en interne, à rendre le conseil plus juste (par exemple
   « prévois une relance si la réponse tarde » quand des candidats parlent de réponses lentes), sans jamais citer
   Reddit ou X, sans juger l'entreprise et sans toucher au score. Ils passent par des robots Apify qui lisent les
   messages publics. C'est le moins cher (environ 0,05 $ par entreprise et par mois), et les données sont réduites au
   minimum (aucun pseudo, plus aucun lien gardé, rien de stocké sauf un résumé de deux phrases pendant 30 jours), mais les conditions
   d'utilisation de Reddit et de X n'autorisent pas cette lecture automatique sans leur accord : le risque est un blocage ou une demande d'arrêt, pas
   une amende. La voie sans risque : l'API officielle de X (environ 20 fois plus chère) et un accord commercial avec
   Reddit (prix non public, demande à faire sur le site de Reddit). Dis-moi si tu gardes Apify, si tu préfères X
   seulement (`JAUGE_SOCIAL_SOURCES=x`), si tu veux que je branche l'API officielle de X, ou si tu coupes tout
   (`JAUGE_SOCIAL_SOURCES=none` : le conseil reste bon, juste un peu moins précis, et cela économise 20 à 40 $ par mois).
3. **Politique de confidentialité : sous-traitants à citer** (fait le 25/10/2026 dans l'app et sur jauge.io, avec Brevo
   pour les e-mails ; à corriger si tu choisis un autre service d'e-mail ou un seul fournisseur d'IA) :

   | Sous-traitant | Rôle | Données envoyées | Lieu |
   |---|---|---|---|
   | Google LLC (Gemini API, offre payante) | lit les offres (texte, ou capture si le téléphone n'a pas su la lire), écrit les conseils et le résumé interne des avis | texte ou image de l'offre, chiffres calculés de l'analyse ; jamais le nom, l'e-mail ni l'identifiant de l'utilisateur | États-Unis et autres pays (clauses contractuelles types, Data Privacy Framework) |
   | ou Mistral AI SAS (si `AI_PROVIDER=mistral`) | même rôle | mêmes données | France, Union européenne |
   | Apify Technologies s.r.o. | collecte des offres du marché et recherche des avis publics Reddit et X sur l'entreprise | nom de l'entreprise et intitulé du poste seulement | République tchèque (serveurs possiblement hors UE : à vérifier dans leur DPA) |
   | Reddit, X | sources publiques lues (pas des sous-traitants : aucune donnée de l'utilisateur ne leur est envoyée) | aucune | |

   Avis : la politique dit déjà qu'ils servent à affiner les conseils, ne sont pas affichés, que les auteurs ne sont
   jamais nommés et que seul un résumé de deux phrases est gardé 30 jours (à jour, rien à faire).
