# Audit de publication App Store

Date : 24/10/2026. Branche des corrections : `feature/store-compliance`. Périmètre : projet Xcode `iosApp`, code Kotlin
`iosMain`, textes de l'app, règles de l'App Review (version de juin 2026 des « App Review Guidelines »).

Limite : il n'y a pas de Xcode sur cette machine. L'app iOS n'a donc pas été lancée ; l'audit porte sur le code, le
projet Xcode et les textes, qui sont communs avec Android (déjà parcourus sur l'émulateur, voir
[google-play.md](google-play.md)). Les fichiers modifiés (`Info.plist`, `PrivacyInfo.xcprivacy`, `InfoPlist.strings`,
`project.pbxproj`) passent `plutil -lint`.

Légende : **OK**, **Corrigé** (corrigé pendant l'audit), **À toi** (voir [a-faire-par-toi.md](a-faire-par-toi.md)).

## 1. Achats et abonnement (3.1.1, 3.1.2)

| Règle | État | Détail |
| --- | --- | --- |
| 3.1.1 Contenu numérique payé par l'achat intégré | OK | Jauge Plus passe uniquement par StoreKit (RevenueCat `purchases-kmp`). Aucun lien ni bouton vers un paiement extérieur. |
| 3.1.1 Restaurer les achats | OK | « Restaurer les achats » en haut du paywall et dans le profil ; message clair si rien à restaurer. |
| 3.1.2 Abonnement auto-renouvelable d'une vraie valeur continue | OK | Analyses illimitées, alertes, détails : service qui continue dans le temps. |
| 3.1.2(c) Avant l'achat : nom, durée, prix, contenu | OK | « Jauge Plus », « Mensuel », prix lu dans l'App Store (`product.price.formatted`), liste de ce qui est débloqué. |
| 3.1.2(c) Essai gratuit : durée et prix après l'essai, clairement | OK | « Essayer 3 jours gratuits », « 3 jours gratuits, puis 6,99 €/mois », frise avec la date du premier prélèvement, texte légal (prélèvement sur le compte Apple, renouvellement automatique, annulation 24 h avant, chemin Réglages › Apple ID › Abonnements). Le bouton n'est pas plus voyant que le prix. |
| Liens obligatoires dans l'app : conditions (EULA) et confidentialité | OK | Sous le bouton du paywall et dans le profil. |
| Gérer / annuler | OK | « Gérer ou annuler l'abonnement » ouvre la page Abonnements de l'App Store (`managementURL`). |
| Liens obligatoires dans la fiche App Store | À toi | Mettre l'URL de la politique (`https://jauge.io/confidentialite/`) dans App Store Connect et un lien vers les conditions (`https://jauge.io/conditions/`) à la fin de la description (ou le champ « Licence d'utilisation »). |
| Rappel avant la fin de l'essai | À toi (serveur) | Promis dans le texte légal du paywall : à faire vraiment (webhook RevenueCat + notification la veille) ou retirer la phrase. |
| Produit dans App Store Connect | À toi | Abonnement `jauge_plus_monthly`, groupe « Jauge Plus », 1 mois, 6,99 €, offre de lancement « essai gratuit 3 jours » ; capture du paywall pour l'examen de l'achat ; accord « Paid Apps » signé. |

## 2. Données et vie privée (5.1.1, 5.1.2)

| Règle | État | Détail |
| --- | --- | --- |
| 5.1.1(i) Politique de confidentialité dans l'app et dans la fiche | OK + **Corrigé** | Dans l'app (Profil, Confidentialité et données). Version web créée : `docs/web/confidentialite/`. |
| 5.1.1(ii) Accord avant la collecte | OK | Connexion facultative, case « conseils par e-mail » décochée par défaut, autorisation des notifications demandée après explication. |
| 5.1.1(iii) Minimisation | OK | Pas de localisation, contacts, ni accès complet aux photos (PHPicker ne demande aucune autorisation). |
| 5.1.1(v) Pas de compte obligatoire | OK | « Continuer sans compte » ; l'app est utilisable sans connexion. |
| 5.1.1(v) Suppression du compte dans l'app | OK | Profil, Supprimer mon compte, avec rappel que l'abonnement se gère dans l'App Store. |
| 5.1.1(v) Révocation du jeton « Se connecter avec Apple » à la suppression | À toi (serveur) | Le serveur doit appeler `https://appleid.apple.com/auth/revoke` dans `DELETE /v1/account` (écrit dans `docs/api.md`). |
| 5.1.2(i) Données envoyées à une IA tierce : le dire et demander l'accord | À toi | Les conditions et la politique disent maintenant que l'analyse est automatique et peut utiliser l'IA. **Si les captures partent chez un fournisseur d'IA extérieur** (OpenAI, Anthropic, Google...), Apple exige de le nommer et de demander un accord explicite avant le premier envoi : dis-moi lequel et j'ajoute l'écran d'accord et le nom dans la politique. Si l'IA tourne sur vos propres serveurs, rien de plus. |
| 5.1.2 Pas de revente, pas de pub, pas de profilage | OK | Écrit dans la politique (section 5) et vrai dans le code : aucun SDK de pub ni de mesure. |

## 3. Connexion (4.8, « Se connecter avec Apple »)

| Règle | État | Détail |
| --- | --- | --- |
| 4.8 Si connexion Google, proposer une option équivalente (Se connecter avec Apple) | OK | « Continuer avec Apple » est affiché en premier, avec la même taille que Google, sur iOS comme sur Android. |
| Masquer mon adresse e-mail | OK | Accepté (adresse relais), écrit dans la politique. |
| Méthode | OK, amélioration possible | Apple passe par Supabase OAuth dans une feuille web sécurisée (`ASWebAuthenticationSession`), accepté par Apple. Le bouton natif (`AuthenticationServices`, entitlement `com.apple.developer.applesignin`) est plus fluide ; à envisager plus tard. |

## 4. Suivi publicitaire (App Tracking Transparency)

Jauge ne fait **aucun suivi** au sens d'Apple : pas d'IDFA, pas de SDK publicitaire ou de mesure d'audience, aucune
donnée croisée avec celles d'autres entreprises, aucun courtier de données. Donc :

- pas de fenêtre ATT et pas de clé `NSUserTrackingUsageDescription` (ne pas en ajouter : Apple refuse une demande ATT
  inutile) ;
- `NSPrivacyTracking = false` et aucun domaine de suivi dans le manifeste ;
- dans App Store Connect, répondre « Non » à « Utilisez-vous des données pour faire du suivi ? ».

## 5. Manifeste de confidentialité `PrivacyInfo.xcprivacy`

**Corrigé** : créé dans `iosApp/iosApp/PrivacyInfo.xcprivacy` et ajouté aux ressources de la cible dans le projet Xcode.

| Section | Contenu |
| --- | --- |
| Suivi | `NSPrivacyTracking` faux, aucun domaine. |
| Données collectées (toutes liées à l'utilisateur, aucune pour le suivi, finalité « fonctionnement de l'app ») | Adresse e-mail, nom, identifiant utilisateur, identifiant de l'appareil (jeton push), photos (captures, effacées sous 24 h), autre contenu de l'utilisateur (liens, alertes, partages), historique d'achats. |
| API à raison obligatoire | `NSUserDefaults` : raison `CA92.1` (réglages et session de l'app, `KeyValueStore.ios.kt`, session Supabase). Horloge depuis le démarrage : raison `35F9.1` (horloge monotone du runtime Kotlin et des coroutines). Aucun accès aux dates de fichiers, à l'espace disque ou aux claviers actifs. |
| SDK tiers | RevenueCat (`PurchasesHybridCommon` / `RevenueCat`) fournit son propre manifeste. À la première archive, vérifier le rapport : Xcode › Product › Archive, puis clic droit sur l'archive › « Generate Privacy Report ». |

## 6. Étiquettes de confidentialité (« nutrition labels ») préremplies

App Store Connect › App Privacy. « Données utilisées pour vous suivre » : **aucune**. « Données liées à vous » :

| Catégorie Apple | Type | Finalité | Liée | Suivi |
| --- | --- | --- | --- | --- |
| Coordonnées | Adresse e-mail | Fonctionnement de l'app ; Marketing de l'éditeur (seulement si la case « conseils » est cochée) | Oui | Non |
| Coordonnées | Nom | Fonctionnement de l'app | Oui | Non |
| Contenu de l'utilisateur | Photos ou vidéos | Fonctionnement de l'app | Oui | Non |
| Contenu de l'utilisateur | Autre contenu de l'utilisateur | Fonctionnement de l'app | Oui | Non |
| Identifiants | Identifiant de l'utilisateur | Fonctionnement de l'app | Oui | Non |
| Identifiants | Identifiant de l'appareil (jeton push) | Fonctionnement de l'app | Oui | Non |
| Achats | Historique des achats | Fonctionnement de l'app | Oui | Non |

Non collectés : santé, finances (hors achats), localisation, informations sensibles, contacts, historique de
navigation ou de recherche, données d'usage pour analyse, diagnostics côté app (aucun SDK de plantage).

## 7. Info.plist, droits et liens

| Élément | État | Détail |
| --- | --- | --- |
| `NSCameraUsageDescription` | OK + **Corrigé** | Texte précis (photographier une offre). Ajout de la version anglaise : `fr.lproj` et `en.lproj/InfoPlist.strings`. |
| `NSPhotoLibraryAddUsageDescription` | OK + **Corrigé** | Enregistrer la carte de partage ; traduit aussi. |
| `NSPhotoLibraryUsageDescription` | OK (absent) | Inutile : le choix d'une capture passe par PHPicker, sans autorisation. |
| `NSUserTrackingUsageDescription`, localisation, micro, contacts | OK (absents) | Non utilisés. |
| `ITSAppUsesNonExemptEncryption = false` | OK | Seulement HTTPS : pas de document d'export à fournir. |
| `NSAppTransportSecurity` | OK | Aucune exception (audit de sécurité). |
| Notifications push (`aps-environment`) | OK, À toi | `development` dans le fichier ; Xcode met `production` à l'archive pour l'App Store. Activer « Push Notifications » sur l'identifiant `io.jauge.app` et créer la clé APNs `.p8` (pour l'Edge Function). Pas de `UIBackgroundModes` : pas besoin (notifications visibles seulement). |
| Universal Links | OK dans l'app, À toi en ligne | `applinks:jauge.io` et `applinks:www.jauge.io`, ouverture gérée (`onContinueUserActivity`). Remplacer `REPLACE_WITH_TEAM_ID` dans `docs/web/.well-known/apple-app-site-association` par ton Team ID et publier `docs/web` sur jauge.io (HTTPS, sans redirection). Activer « Associated Domains » sur l'identifiant. |
| Retour de connexion `io.jauge.app://login-callback` | OK | Déclaré dans `CFBundleURLTypes`. |
| Icône 1024 × 1024 sans transparence | OK | `AppIcon.png`, pas de canal alpha. |
| Orientation | OK | Portrait seulement (iPhone). L'iPad n'est pas visé : vérifier dans Xcode que la cible est « iPhone » seulement, sinon Apple demande des captures iPad. |

## 8. Accessibilité de base

| Sujet | État | Détail |
| --- | --- | --- |
| Tailles de texte dynamiques | OK + **Corrigé** | Toutes les tailles sont en `sp`, qui suivent le réglage du système (Compose Multiplatform applique aussi la taille de texte iOS). Testé à 150 % sur Android : boutons et tuiles coupaient leur texte, corrigé. |
| Libellés pour VoiceOver / TalkBack | OK + **Corrigé** | Boutons ronds (Retour, Fermer, Partager, Copier, Supprimer) libellés ; jauges décrites (« Concurrence : 78 sur 100, Bondé ») ; titres marqués comme titres ; cases et interrupteurs avec leur rôle. Libellé vide de l'écran 10 corrigé. |
| Contrastes | OK, deux réserves | Texte principal encre `#13211D` sur papier `#F7F2E8` : 14,9:1. Textes des pastilles (calme 4,6:1, disputé 4,8:1, bondé 4,5:1) conformes. Les états ont toujours un mot en plus de la couleur. Réserves (couleurs de la marque, non modifiées sans ton accord) : le gris secondaire `Sage #6C7A74` sur papier fait 4,0:1 (le seuil WCAG AA du petit texte est 4,5:1 ; `#5F6C66` passerait) ; le texte blanc sur le bouton corail `#FF5B3A` fait 3,0:1, admis seulement pour du texte gras de grande taille (c'est le cas des boutons). Apple ne refuse pas une app pour ça, mais c'est à améliorer. |
| Zones de toucher | OK | 44 pt au moins pour les boutons ronds et les lignes de réglages. |
| Fiche « Accessibility Nutrition Labels » (App Store Connect, facultative) | À toi | Tu peux déclarer « Texte plus grand », « Mode sombre », « Contraste suffisant » ; ne coche VoiceOver qu'après un test sur un iPhone. |

## 9. Autres règles de l'examen

| Règle | État | Détail |
| --- | --- | --- |
| 2.1 App complète, sans plantage, avec un vrai service | À toi | La version App Store parle à la vraie API (`api.jauge.io`), qui n'existe pas encore : sans elle, l'examinateur ne verrait que « Pas de connexion » et refuserait l'app. Mettre l'API en ligne avant l'envoi. |
| 2.3 Fiche exacte | À toi | Captures réelles de l'app (6,9 pouces : 1320 × 2868 ou 1290 × 2796), sans le bandeau « Mode sans API ». |
| 2.5.2 Pas de code téléchargé qui change l'app | OK | Rien de tel. |
| 4.2 Fonctionnalité minimale | OK | Vraie app native (Compose), pas un site emballé. |
| 1.2 Contenu des utilisateurs | OK | Partages publics choisis par l'utilisateur, supprimables ; signalement par e-mail (mentions légales). |
| Âge | À toi | Questionnaire d'âge : réponses « Aucun » partout, sauf « contenu généré par les utilisateurs : partages publics », classement attendu 4+ ou 9+. Les conditions demandent 15 ans : ça ne contredit pas l'âge App Store. |
| Notes pour l'examinateur | À toi | « Aucun compte nécessaire ; l'abonnement se teste avec un compte Sandbox ; les chiffres sont des estimations. » |
