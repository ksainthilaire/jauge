# Audit de publication Google Play

Date : 24/10/2026. Branches : `feature/remove-provisional-alerts-screen`, `feature/device-test-fixes`,
`feature/store-compliance`. Périmètre : APK debug et release (R8) construits depuis `develop`, manifeste fusionné,
textes de l'app, passage complet sur un émulateur Android 16 (API 36, Pixel 7, arm64, en français).

Légende : **OK** (conforme), **Corrigé** (corrigé dans le code pendant l'audit), **À toi** (dépend de ton compte ou de
ta société, voir [a-faire-par-toi.md](a-faire-par-toi.md)).

## 1. Passage sur un vrai Android

Émulateur `jauge_store` (image `system-images;android-36;google_apis;arm64-v8a`), lancé sans fenêtre. Tous les écrans
ont été parcourus avec adb + uiautomator ; les captures sont dans [`docs/screenshots/device/`](../screenshots/device/).

| Parcours | Résultat |
| --- | --- |
| 01 bienvenue (3 pages), 11 compte et accords, connexion par e-mail, 12 alertes + fenêtre système (refus et accord) | OK |
| 02 coller un lien, 03 lecture, 04 verdict, 05 détails, 09 comment on estime + « Dis-le-nous » | OK |
| 02 « Une capture » (sélecteur de photos Android) et « Photographier » (appli appareil photo) | **Corrigé** : aucune image ne pouvait être lue (voir 2.1) |
| 10 offre illisible, saisie à la main | OK (après correction 2.1) |
| 06 veille, alerte « calme », 07 carte de partage (story, carré, lien), copier, enregistrer dans les photos, partage système, onglet Partages | OK |
| 08 paywall, « Restaurer les achats », paywall du quota atteint | OK (achats indisponibles : aucun produit dans RevenueCat, voir 5) |
| 13 profil, réglages, confidentialité, conditions, mentions légales, à propos, export, suppression du compte | OK, la suppression ramène à l'écran 01 |
| Lien public `https://jauge.io/a/{id}` ouvert depuis l'extérieur | OK (ouvre le verdict) |
| Mode avion, API injoignable, délai dépassé, service indisponible, réponse illisible, session expirée, listes vides | OK : message aimable et bouton « Réessayer », jamais d'erreur technique |
| Texte agrandi à 150 % (réglage d'accessibilité) | **Corrigé** : boutons et tuiles coupaient leur texte |
| Mode sombre du système | OK |
| Version release (R8, signée avec une clé de test) : bienvenue, compte, alertes, accueil, profil, onglets | OK, aucun plantage |

Aucun plantage (`adb logcat -b crash` vide) sur tout le passage.

### 1.1 Défauts trouvés et corrigés

1. **Captures et photos jamais lues** (`ImagePicker.android.kt`) : la première lecture des dimensions de l'image
   (`inJustDecodeBounds`) renvoie toujours `null` par conception, et le code prenait ce `null` pour un fichier illisible.
   Toute capture ou photo affichait « Cette image n'a pas pu être ouverte ». Corrigé et vérifié sur l'émulateur
   (galerie et appareil photo).
2. **Bouton principal caché par le clavier** (connexion par e-mail, saisie à la main, réglages) : `imePadding()` dans
   `JaugeScreenScaffold` et l'écran e-mail. Le bouton « Recevoir un lien » remonte au-dessus du clavier.
3. **Historique faux en cas d'erreur** : l'accueil affichait « Aucune offre jaugée pour l'instant » quand l'historique
   n'avait pas pu être chargé. Il affiche maintenant le message d'erreur aimable avec « Réessayer ».
4. **Grandes tailles de texte** : `JaugeButton` avait une hauteur fixe (texte coupé, par exemple « Créer un compte pour
   garder mes analyses ») ; les tuiles « Une capture » / « Photographier » coupaient le mot. Les boutons grandissent
   avec le texte et les tuiles passent sur deux lignes.
5. **TalkBack** lisait « Poste, , non lu » sur l'écran 10 : la valeur vide n'est plus lue.
6. **Ancien écran 12 provisoire** resté dans `feature:twins` (et le composant `PlaceholderScreen` qui ne servait plus) :
   supprimés. Le vrai écran 12 est dans `feature:onboarding`.
7. **Outil de test** : la version debug accepte
   `adb shell am start -n io.jauge.app/io.jauge.android.MainActivity -e offline_scenario UNREADABLE_OFFER`
   (et `NO_CONNECTION`, `TIMEOUT`, `SERVER_UNAVAILABLE`, `SESSION_EXPIRED`, `QUOTA_REACHED`, `INVALID_RESPONSE`, `EMPTY`,
   `NORMAL`) pour voir chaque cas d'erreur sur un téléphone. La version release l'ignore.

## 2. Exigences techniques

| Exigence | État | Détail |
| --- | --- | --- |
| targetSdk exigé (API 35 depuis le 31/08/2025, API 36 attendu pour le 31/08/2026) | OK | `targetSdk 36`, `compileSdk 37`, `minSdk 26`. |
| Pages mémoire de 16 Ko (obligatoire depuis le 01/11/2025 pour les apps avec du code natif) | OK | Seules bibliothèques natives : `libandroidx.graphics.path.so` et `libdatastore_shared_counter.so`, segments `LOAD` alignés sur 16 384 ; `zipalign -c -P 16` : réussi. |
| Format de publication | À toi | Envoyer un Android App Bundle (`./gradlew :androidApp:bundleRelease`) signé avec ta clé d'envoi (Play App Signing). |
| Code optimisé (R8) | OK | Activé par l'audit de sécurité ; la version release a été lancée sans plantage. |
| Sauvegarde | OK | `allowBackup="false"` : la session n'est pas copiée vers un autre appareil. |
| Icône | OK | Icône adaptative + monochrome (thème des icônes Android 13+) ; icône de la fiche 512 × 512 sans transparence (`androidApp/src/main/playstore/ic_launcher-playstore.png`). |
| Nom | OK | « Jauge », 5 caractères. |
| Orientation | OK | Portrait seulement : `screenOrientation="portrait"` sur `MainActivity`, `"behind"` sur l'activité transparente de demande de notifications (une orientation fixe sur une activité transparente plante sous Android 8.0). Android 16 ignore l'orientation forcée sur les grands écrans (tablettes, pliables ouverts, largeur ≥ 600 dp) : l'app s'affiche alors aussi en paysage, c'est accepté. |

## 3. Autorisations (manifeste fusionné)

| Autorisation | Origine | Justification |
| --- | --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | app, Ktor/OkHttp, Firebase | Analyses, compte, achats. |
| `POST_NOTIFICATIONS` | `core:notifications`, `feature:onboarding` | Alertes « calme ». Demandée seulement à l'écran 12, après une explication, avec « Plus tard ». Refus géré (réglage expliqué dans Réglages). |
| `WAKE_LOCK`, `com.google.android.c2dm.permission.RECEIVE` | Firebase Cloud Messaging | Réception des notifications push. |
| `ACCESS_COARSE_LOCATION` | `core:location` | Ville de la zone de recherche (analyses, offres jumelles). Position **approximative** seulement, jamais `ACCESS_FINE_LOCATION` ni `ACCESS_BACKGROUND_LOCATION`. Demandée une fois, après la pop-up d'explication de l'app (« Où cherches-tu ? », avec « Plus tard »). La position est convertie en ville **sur le téléphone** (table des préfectures et grandes villes, sans réseau) et n'est jamais envoyée ; refus ou indisponibilité : Paris, modifiable dans Réglages. `uses-feature` location non requis. |
| `com.android.vending.BILLING` | RevenueCat | Abonnement Jauge Plus par Google Play Billing. |
| `io.jauge.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX | Interne à l'app, ajoutée automatiquement. |

Absentes, et c'est voulu : `CAMERA` (l'appareil photo passe par l'appli photo du téléphone, `ACTION_IMAGE_CAPTURE`),
`READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` (sélecteur de photos Android, conforme à la règle « Photos et vidéos »
de Google Play), `WRITE_EXTERNAL_STORAGE` (enregistrement par MediaStore), localisation précise et en arrière-plan, contacts, `AD_ID`.
Pas de localisation en arrière-plan : pas de déclaration « Location permissions » dans la Play Console (elle ne
concerne que `ACCESS_BACKGROUND_LOCATION`).

## 4. Sécurité des données (formulaire « Data safety », prérempli)

Réponses générales :

- L'app collecte-t-elle ou partage-t-elle des données ? **Oui (collecte)**.
- Les données sont-elles chiffrées en transit ? **Oui** (HTTPS seulement).
- Les utilisateurs peuvent-ils demander la suppression ? **Oui** : dans l'app (Profil, Supprimer mon compte) et
  sur la page web `https://jauge.io/supprimer-mon-compte/`.
- Partage avec des tiers : **Non**. Supabase, RevenueCat, Google et Apple agissent comme prestataires
  (« service providers ») pour le compte de Jauge, ce que Google ne compte pas comme un partage.
- Engagement « Families » : non (public 16 ans et plus, voir 7).

| Catégorie Google | Type | Collecté | Partagé | Facultatif | Finalités |
| --- | --- | --- | --- | --- | --- |
| Informations personnelles | Adresse e-mail | Oui | Non | Oui (compte facultatif) | Fonctionnement de l'app, gestion du compte, communications (seulement avec l'accord « conseils par e-mail ») |
| Informations personnelles | Nom | Oui | Non | Oui | Gestion du compte |
| Informations personnelles | Identifiants utilisateur | Oui | Non | Non | Fonctionnement de l'app, gestion du compte |
| Activité dans l'app | Autre contenu généré par l'utilisateur (liens d'offres, texte lu sur les captures, alertes, partages, chiffres signalés) | Oui | Non | Non | Fonctionnement de l'app |
| Informations financières | Historique des achats | Oui | Non | Oui | Fonctionnement de l'app, gestion du compte |
| Position | Position approximative | Oui (seulement la ville et le département de la zone de recherche, trouvés sur le téléphone ; la position n'est jamais envoyée) | Non | Oui (refus : Paris, ville modifiable) | Fonctionnement de l'app |
| Identifiants de l'appareil | Jeton de notification (FCM) | Oui | Non | Oui (seulement si les alertes sont activées) | Fonctionnement de l'app |
| Infos et performances de l'app | Diagnostics (version de l'app, système, langue, journaux techniques côté serveur ; statistiques techniques anonymes de ML Kit) | Oui | Non | Non | Fonctionnement de l'app, analyses techniques |

Non collectés : photos (depuis le 25/10/2026, les captures sont lues sur le téléphone par ML Kit, modèle intégré à
l'app, et seul leur texte est envoyé), position précise, localisation en arrière-plan, contacts, messages,
audio, fichiers, santé, identifiant publicitaire, historique de navigation. Aucun SDK de publicité, de mesure
d'audience ni de rapport de plantage (vérifié dans toutes les dépendances).

ML Kit (lecture du texte des captures) tourne sur le téléphone : ni l'image ni le texte ne partent chez Google, mais la
bibliothèque envoie des statistiques techniques anonymes. Avant de remplir le formulaire, relis la page « ML Kit data
disclosure » de Google (developers.google.com/ml-kit/android-data-disclosure) et reporte ce qu'elle indique.

Le texte des captures part chez le fournisseur d'IA du serveur (voir 9) : c'est un prestataire, la réponse
« Partage : Non » reste juste, et il est nommé dans la politique de confidentialité.

## 5. Abonnement (Play Billing par RevenueCat)

| Règle Google Play | État | Détail |
| --- | --- | --- |
| Achat par Google Play Billing | OK | `purchases-kmp` 3.15 (Billing Library récente incluse). Aucun autre moyen de paiement. |
| Prix, période et essai affichés avant l'achat | OK | Prix lu dans le store (`product.price.formatted`), « 3 jours gratuits, puis 6,99 €/mois », frise « Aujourd'hui, 0 € / date de fin d'essai, puis 6,99 €/mois ». |
| Renouvellement automatique et annulation expliqués | OK | Texte légal sous le bouton : renouvellement mensuel, annulation 24 h avant, chemin « Google Play › Paiements et abonnements ». |
| Gérer / annuler depuis l'app | OK | « Gérer ou annuler l'abonnement » (profil) et « Gérer mon abonnement » (paywall, suppression du compte) ouvrent la page d'abonnement Google Play. |
| Restaurer les achats | OK | Paywall et profil ; message clair si rien à restaurer (vérifié). |
| Achat annulé | OK | Silencieux (aucun message d'erreur), géré par `BillingFailure.Cancelled` et testé dans `core:billing`. Pas testable sur l'émulateur faute de produit dans RevenueCat. |
| Achats indisponibles (pas de clé, pas de produit) | OK | Encadré « Achats indisponibles », bouton désactivé, aucune erreur technique. |
| Rappel avant la fin de l'essai | **Retiré** | Pas exigé par Google : décision du 24/10/2026, l'app ne promet plus de rappel (paywall, profil, conditions). Restent le prix, la durée de l'essai, le renouvellement automatique et l'annulation 24 h avant. |
| Produit et offre configurés | À toi | Créer l'abonnement `jauge_plus_monthly` (6,99 €, mensuel, essai 3 jours pour les nouveaux abonnés) dans la Play Console et dans RevenueCat (aujourd'hui le Test Store RevenueCat n'a aucun produit : l'émulateur affiche « Achats indisponibles »). |

## 6. Compte et suppression

| Règle | État | Détail |
| --- | --- | --- |
| Suppression du compte dans l'app | OK | Profil, Supprimer mon compte : liste de ce qui est effacé, case de confirmation, `DELETE /v1/account`, retour à l'accueil. Vérifié sur l'émulateur. |
| Lien web de suppression (obligatoire dans la Play Console) | **Corrigé** | Nouvelle page `docs/web/supprimer-mon-compte/index.html` (à publier sur `https://jauge.io/supprimer-mon-compte/`) : étapes dans l'app, demande par e-mail sans l'app, données effacées et conservées, délai de 30 jours, rappel sur l'abonnement. Lien ajouté dans la politique de confidentialité et les conditions. |
| Politique de confidentialité en ligne (URL obligatoire) | **Corrigé** | Pages web générées depuis les textes de l'app : `docs/web/confidentialite/`, `docs/web/conditions/`, `docs/web/mentions-legales/` (`python3 tools/legal-pages/generate.py`). |
| Identité de l'éditeur | **Corrigé** (sauf médiateur) | `feature/account/src/commonMain/composeResources/values/legal_identity.xml` rempli le 24/10/2026 : Keny SAINT HILAIRE, EI micro-entreprise, SIREN 893 324 848, 149, avenue du Maine, 75014 Paris, directeur de la publication Keny SAINT HILAIRE, hébergeur Supabase Pte. Ltd. Reste à toi : le médiateur de la consommation (obligatoire avant de vendre à des particuliers) et la réponse sur la franchise en base de TVA. Relancer ensuite le générateur des pages web. |
| Côté serveur | À toi (API) | `DELETE /v1/account` doit aussi supprimer l'utilisateur Supabase, les appareils, le client RevenueCat et révoquer le jeton Apple (écrit dans `docs/api.md`). |

## 7. Contenu, public et fiche

| Sujet | État | Détail |
| --- | --- | --- |
| Questionnaire de classification (IARC) | À toi | Réponses : pas de violence, sexe, drogue, jeux d'argent, langage grossier ; **partage de contenu entre utilisateurs : oui** (liens publics de partage), pas de discussion entre utilisateurs, pas de localisation partagée. Classement attendu : PEGI 3 / Tout public. |
| Public cible | À toi | Choisir **18 ans et plus** seulement (les conditions et la politique disent 18 ans minimum) : pas de public de moins de 13 ans, donc pas de règles « Families ». |
| Contenu généré par l'utilisateur | OK | Les partages sont publics mais choisis par l'utilisateur, supprimables à tout moment ; signalement par contact@jauge.io (mentions légales, section « Signaler un contenu »). |
| Accès à l'app pour l'examen | OK | Tout fonctionne sans compte : rien à fournir. Dire dans « App access » que la connexion est facultative. |
| Publicités | OK | « Non, l'app ne contient pas de publicités ». |
| Fiche Play Store | À toi | Description, captures 1080 × 1920 au moins (2 à 8), image de présentation 1024 × 500, catégorie « Entreprise » ou « Productivité », e-mail de contact, URL de la politique. |
| Déclaration « Actualités », « Santé », « Finances » | OK | Non concernée. |

## 8. Liens d'app, notifications, arrière-plan

| Sujet | État | Détail |
| --- | --- | --- |
| App Links `https://jauge.io/a/{id}` | OK dans l'app, À toi en ligne | Filtre `autoVerify` sur `jauge.io` et `www.jauge.io`. Sur l'émulateur la vérification échoue (état 1024) car `https://jauge.io/.well-known/assetlinks.json` n'existe pas encore. Mettre l'empreinte SHA-256 de la clé de signature Play dans `docs/web/.well-known/assetlinks.json` et publier le dossier `docs/web` sur jauge.io. |
| Notifications | OK | Canal `calm_alerts` créé avant l'envoi, demande d'autorisation au bon moment (écran 12), refus géré. Pas de notification marketing sans accord. |
| Travail en arrière-plan | OK | Aucun service au premier plan, aucune alarme exacte, aucune localisation en arrière-plan. Seul le service FCM (non exporté) reçoit les messages. |

## 9. Intelligence artificielle

Les textes des écrans disent déjà que les chiffres sont des estimations (« Estimation Jauge », « Des estimations, pas
des garanties », méthode expliquée à l'écran 09). L'audit a ajouté dans les conditions et la politique de
confidentialité que les analyses sont produites automatiquement et peuvent faire appel à l'intelligence artificielle,
sans décision prise à la place de l'utilisateur.

La règle Google Play sur le contenu généré par IA (signalement in-app de contenu choquant) vise les apps qui génèrent
du texte ou des images librement. Jauge affiche des chiffres et des phrases courtes : si le serveur fait écrire des
conseils libres par un modèle génératif, ajouter un bouton « Signaler » sur ces conseils (le « Dis-le-nous » de
l'écran 09 ne couvre que les chiffres). **À toi** : dire quel moteur lit les captures (voir a-faire).

## 10. Reste à faire avant l'envoi (résumé)

Voir [a-faire-par-toi.md](a-faire-par-toi.md), section « Google Play ».
