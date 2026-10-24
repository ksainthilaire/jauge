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
2. **Remplir l'identité de ta société** dans un seul fichier :
   `feature/account/src/commonMain/composeResources/values/legal_identity.xml` (nom, forme juridique, capital,
   numéro RCS, adresse, TVA, directeur de la publication, médiateur de la consommation). Chaque
   « [à compléter avant publication] » doit disparaître. Ensuite lance `python3 tools/legal-pages/generate.py` pour
   mettre à jour les pages web.
3. **Me dire qui lit les captures** : si les images partent chez un fournisseur d'IA (OpenAI, Anthropic, Google...),
   Apple exige de le nommer et de demander l'accord de l'utilisateur avant le premier envoi. Donne-moi le nom :
   j'ajoute l'écran d'accord et je complète la politique de confidentialité.
4. **Le rappel la veille de la fin de l'essai** : le paywall le promet. Soit on le fait (le serveur reçoit l'essai
   depuis RevenueCat et envoie une notification la veille), soit on retire la phrase. Dis-moi ton choix.
5. **Côté serveur, la suppression du compte** doit aussi effacer le client RevenueCat et révoquer la connexion Apple
   (c'est écrit dans `docs/api.md`, ligne `DELETE /v1/account`).

### Le site jauge.io

6. Publier le dossier `docs/web` sur `https://jauge.io` (HTTPS, sans redirection) :
   - `/confidentialite/`, `/conditions/`, `/mentions-legales/` : les liens à donner aux stores ;
   - `/supprimer-mon-compte/` : le lien de suppression demandé par Google ;
   - `/.well-known/assetlinks.json` : remplace `REPLACE_WITH_THE_SHA256_FINGERPRINT...` par l'empreinte SHA-256 de la
     clé de signature (Play Console › Intégrité de l'app › Signature de l'app) ;
   - `/.well-known/apple-app-site-association` : remplace `REPLACE_WITH_TEAM_ID` par ton Team ID Apple.
7. Créer les boîtes `contact@jauge.io` et `privacy@jauge.io` (elles sont écrites dans l'app) et répondre aux demandes
   de suppression sous 30 jours.

### Comptes et abonnement

8. **Compte Google Play Console** (25 $ une fois) et **Apple Developer Program** (99 $ par an), au nom de la société.
9. **Abonnement** `jauge_plus_monthly` à 6,99 € par mois avec 3 jours d'essai, dans la Play Console (offre de base
   `monthly`) et dans App Store Connect (groupe « Jauge Plus »), puis dans RevenueCat (produit, droit `plus`, offre
   `default`). Sur l'émulateur, le paywall affiche « Achats indisponibles » parce que le Test Store RevenueCat n'a
   encore **aucun produit** : ajoute le produit de test (voir `docs/revenuecat.md`) et je testerai l'achat et l'achat
   annulé. Signe aussi l'accord « Paid Apps » chez Apple et le profil de paiement chez Google.
10. **Supabase › Authentication › SMTP** : branche ton propre service d'e-mail (Brevo, Resend...). Le service
    d'e-mail fourni par Supabase n'envoie que quelques e-mails par heure et refuse les adresses de test : pendant
    l'essai, « Recevoir un lien » a répondu « On souffle un instant ».
11. **Apple** : sur l'identifiant `io.jauge.app`, active « Push Notifications », « Associated Domains » et « Sign in
    with Apple » ; crée la clé APNs `.p8`. Dans Xcode, garde la cible en « iPhone » seulement.

### Les fiches des stores

12. **Google Play** : remplis le formulaire « Sécurité des données » avec le tableau de `google-play.md` (section 4),
    le questionnaire de classification (section 7), public cible « 16-17 ans » et « 18 ans et plus », « pas de
    publicité », « accès à l'app : aucun compte nécessaire », l'URL de suppression et l'URL de la politique.
13. **App Store** : remplis « Confidentialité de l'app » avec le tableau de `app-store.md` (section 6), réponds
    « Non » au suivi, mets l'URL de la politique et un lien vers les conditions dans la description, ajoute les notes
    pour l'examinateur (section 9).
14. **Captures de store finales** : prends-les sur la version release (sans le bandeau orange « Mode sans API »),
    avec de vraies analyses : Google 1080 × 1920 au moins, Apple 1320 × 2868 (iPhone 6,9 pouces). Les captures de
    `docs/screenshots/device/` servent de contrôle, pas de fiche.
15. **Textes des fiches** : nom « Jauge », sous-titre (30 caractères, par exemple « Vois la foule avant de
    postuler »), description, mots-clés, catégorie (Productivité ou Entreprise), e-mail et site de contact.

### À décider (facultatif)

16. Deux couleurs de la marque sont un peu pâles pour les personnes qui voient mal (gris secondaire et texte blanc sur
    le bouton corail, détails dans `app-store.md` section 8). Je peux foncer légèrement le gris si tu es d'accord.
