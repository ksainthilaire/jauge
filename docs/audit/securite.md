# Audit de sécurité de Jauge (Android, iOS, Supabase)

Date : 24/10/2026. Branche : `feature/security-audit`. Périmètre : tout le dépôt (historique git compris), l'APK debug
et l'APK release construits depuis ce dépôt, les migrations et l'Edge Function Supabase, les dépendances.

Gravités : **Haute** (exploitable ou fuite de données probable), **Moyenne** (exploitable sous conditions),
**Basse** (défense en profondeur), **Info** (rien à corriger).

Résultat : toutes les failles trouvées dans le code sont corrigées. Ce qui reste dépend de réglages hors du dépôt
(clé de signature, réglages Supabase en ligne, test de la version release sur un téléphone) : voir
[a-faire-par-toi.md](a-faire-par-toi.md).

## 1. Secrets

| Constat | Gravité | Correction |
| --- | --- | --- |
| Historique git complet (33 commits, toutes les branches) relu : aucune clé secrète (`sk_`, `sb_secret_`, clé service role, JWT, clé privée, `google-services.json`, `.p8`). Les valeurs réelles de `local.properties` et `Config.xcconfig` n'apparaissent nulle part dans l'historique. | Info | Rien. |
| `local.properties`, `Config.xcconfig`, `*.jks`, `*.keystore`, `google-services.json`, `GoogleService-Info.plist`, `supabase/functions/.env` sont ignorés par git. | Info | Rien. |
| APK debug et release décompressés, `strings` sur tous les fichiers : seules les clés publiques sont présentes (URL du projet Supabase, clé `sb_publishable_`). La clé Test Store RevenueCat (`test_`) n'est que dans l'APK debug. Aucun chemin local de la machine. | Info | Rien. |
| La garde de build refusait `sk_`, `sb_secret_` et le texte `service_role`, mais pas une ancienne clé Supabase JWT dont le rôle `service_role` est encodé en base64. | Basse | `GenerateBuildConfigurationTask` décode le JWT et refuse le rôle `service_role`. |
| Un framework iOS Release aurait embarqué la clé Test Store et le mode sans API (pas de R8 sur iOS). | Basse | Quand Xcode construit en `CONFIGURATION=Release`, la clé Test Store est vidée et le mode sans API forcé à faux. |

## 2. Réseau

| Constat | Gravité | Correction |
| --- | --- | --- |
| Pas de `network_security_config` : HTTP en clair seulement bloqué par défaut (targetSdk 36), autorités ajoutées par l'utilisateur non exclues explicitement. | Moyenne | `res/xml/network_security_config.xml` : HTTPS seulement, autorités système seulement ; `usesCleartextTraffic="false"`. La version debug (`src/debug`) autorise le clair uniquement vers `10.0.2.2`, `localhost`, `127.0.0.1` (Supabase local) et les certificats utilisateur (proxy de débogage). |
| Une URL d'API ou Supabase en `http://` dans la configuration aurait été utilisée telle quelle. | Moyenne | `AppEnvironment.secureUrlOrEmpty` : `https` toujours ; `http` seulement en debug et vers les hôtes locaux ; sinon le service est désactivé. Testé (`AppEnvironmentSecurityTest`). |
| iOS : ATS actif par défaut, aucune exception. | Info | `NSAppTransportSecurity` / `NSAllowsArbitraryLoads = false` écrit explicitement dans `Info.plist`. |
| Délais : 15 s connexion, 30 s lecture/écriture (OkHttp et Ktor). | Info | Rien. |
| Journal OkHttp : niveau BASIC en debug seulement (méthode, URL, code), absent en release. Le jeton n'était pas journalisé, mais rien n'empêchait de l'exposer si quelqu'un montait le niveau. | Basse | `redactHeader("Authorization")` et `Cookie`. |
| supabase-kt journalise au niveau INFO par défaut, aussi en release. | Moyenne | `defaultLogLevel = NONE` en release, INFO en debug. RevenueCat : WARN en release (pas de jeton). R8 supprime `Log.v/d/i` en release. |
| iOS (Ktor) : les identifiants étaient collés tels quels dans le chemin (`v1/analyses/$id`) : un identifiant comme `../account` aurait changé l'URL appelée. | Moyenne | `pathSegment()` encode chaque identifiant (y compris `/`, `?`, `#`, `.`). Retrofit encode déjà les `@Path`. Testé (`PathSegmentsTest`). |

## 3. Stockage

| Constat | Gravité | Correction |
| --- | --- | --- |
| La session Supabase (jeton d'accès et jeton de rafraîchissement) était gardée **en clair** par supabase-kt : SharedPreferences sur Android, NSUserDefaults sur iOS. Idem pour le code PKCE. | **Haute** | `SecureSessionManager` et `SecureCodeVerifierCache` branchés dans `install(Auth)`. Android : `KeystoreSecureStore`, AES-256-GCM avec une clé de l'Android Keystore (non exportable). iOS : `KeychainSecureStore`, Keychain `AfterFirstUnlockThisDeviceOnly` (jamais copié par iCloud ni par une sauvegarde). Une valeur indéchiffrable est effacée : l'utilisateur se reconnecte. |
| Sauvegarde Android : `allowBackup="false"` coupe la sauvegarde cloud, mais pas le transfert d'appareil à appareil d'Android 12+. | Moyenne | `data_extraction_rules.xml` (et `backup_rules.xml` pour Android 11 et moins) : seul `jauge_preferences.xml` (étapes d'accueil, interrupteur des alertes, zone de recherche) est copié. Session, caches, fichiers des SDK : jamais. |
| Photos d'offres prises avec l'appareil photo : `cache/photos/offer.jpg` restait sur le téléphone après la lecture. | Moyenne | Le fichier est effacé dès qu'il est lu, et aussi si la prise de vue est annulée. Les captures choisies dans la galerie ne sont jamais copiées (lecture en mémoire). |
| FileProvider : deux fournisseurs non exportés, chemins minimaux (`cache/photos/`, `cache/jauge-share/`), permission de lecture donnée à l'intent seulement. | Info | Rien. La carte de partage (contenu public) est réécrite à chaque partage. |
| Préférences (`jauge_preferences`) : rien de sensible. | Info | Rien. |

## 4. Composants Android et liens

| Constat | Gravité | Correction |
| --- | --- | --- |
| Composants : seule `MainActivity` est exportée (lanceur, liens). Récepteurs exportés des bibliothèques protégés par permission (`c2dm.SEND`, `DUMP`). `NotificationPermissionActivity` et `JaugeMessagingService` non exportés. | Info | Rien. |
| Lien d'analyse : `http://` accepté, ainsi que `user@` et un port. | Basse | `AnalysisLink` : `https://jauge.io` ou `www.jauge.io` uniquement, ni utilisateur ni port, identifiant `[A-Za-z0-9_-]{1,64}`, 2048 caractères max. Tests complétés. |
| Lien de connexion : `startsWith("io.jauge.app://login-callback")` acceptait `io.jauge.app://login-callback.evil...`. Pas exploitable seul (PKCE : le code ne sert qu'avec le vérificateur gardé sur ce téléphone), mais trop large. | Basse | Correspondance exacte de l'hôte, suivie seulement de `/`, `?` ou `#`. Test ajouté. |
| Liens ouverts hors de l'app à partir de données réseau (URL des offres jumelles venant de l'API, page de gestion d'abonnement) : n'importe quel schéma pouvait être ouvert (`intent:`, `file:`, `javascript:`). | Moyenne | `WebLinks.safeWebLinkOrNull` : `https` seulement, hôte valide, pas d'identifiants dans l'URL. Utilisé dans les offres jumelles, le profil, la suppression du compte et le paywall. Testé (`WebLinksTest`). |
| Pas de redirection ouverte : l'app ne redirige vers aucune URL reçue dans un lien. | Info | Rien. |
| Tapjacking : rien n'empêchait une autre app de recouvrir l'écran (achat, suppression du compte). | Moyenne | `filterTouchesWhenObscured` sur toute la fenêtre de `MainActivity`. |
| Détournement de tâche (StrandHogg) avec `singleTask`. | Basse | `android:taskAffinity=""` sur `MainActivity`. |
| WebView : aucune. PendingIntent : aucun dans le code de l'app (Firebase les crée lui-même, immuables). | Info | Rien. |

## 5. Version release

| Constat | Gravité | Correction |
| --- | --- | --- |
| R8 désactivé en release (`isMinifyEnabled = false`) : code lisible, journaux de débogage présents. | **Haute** | R8 activé (`isMinifyEnabled`, `isShrinkResources`), `proguard-rules.pro` (kotlinx.serialization, Retrofit suspend, Supabase, Ktor, suppression de `Log.v/d/i`). L'APK release passe de 24,5 Mo (debug) à 4,3 Mo. `debuggable=false` vérifié avec apkanalyzer. |
| Mode sans API : activable par réglage de build **aussi en release**, et la connexion simulée (`SimulatedAuthenticationRepository`) aurait « connecté » n'importe qui en release sans Supabase. | Moyenne | `AppEnvironment` : mode sans API forcé à faux hors debug ; `canUseSimulatedServices` ; en release sans Supabase, `UnavailableAuthenticationRepository` (connexion impossible, message aimable). L'interrupteur caché était déjà réservé au debug. Testé. |
| Signature : aucune configuration de signature dans le dépôt (voulu). | Info | À faire par toi : clé d'envoi Play App Signing, voir a-faire-par-toi.md. |

## 6. Supabase

| Constat | Gravité | Correction |
| --- | --- | --- |
| RLS active sur `devices` et `notifications`, droits retirés à `anon`, `authenticated` limité à ses lignes (`auth.uid() = user_id`), mise à jour limitée à la colonne `read_at`. Realtime applique la même règle. | Info | Rien. |
| Fonctions `SECURITY DEFINER` (`register_device`, `unregister_device`, `push_new_notification`) : `search_path = ''`, `EXECUTE` retiré à `public` et `anon`. | Info | Rien. |
| `register_device` acceptait n'importe quel texte comme jeton (32 à 4096 caractères), ensuite collé dans l'URL de la requête APNs de l'Edge Function. | Moyenne | Migration `20261024180000_security_hardening.sql` : format FCM (`[A-Za-z0-9_:-]`) ou APNs (hexadécimal) obligatoire, langue validée, contrainte de format sur la table. |
| Un utilisateur pouvait enregistrer un nombre illimité d'appareils. | Basse | 10 appareils au plus par utilisateur, les plus anciens sont retirés. |
| Edge Function : corps de requête non limité, secret partagé sans longueur minimale, jeton APNs non encodé dans l'URL, réponses de FCM et APNs écrites dans les journaux. | Moyenne | Corps limité à 4 Ko, identifiant UUID strict, secret d'au moins 32 caractères, jetons revérifiés et encodés, journaux réduits au code HTTP. Appelant vérifié par secret partagé (comparaison à temps constant) ou clé service role ; aucune clé en dur. |
| Un jeton de push connu d'un tiers peut être « repris » par son compte (`on conflict (token)`) : il recevrait alors ses propres notifications sur ce téléphone. Les jetons ne sont lisibles que par leur propriétaire. | Basse | Accepté (comportement voulu quand un téléphone change de compte). |

## 7. Dépendances

- 486 bibliothèques résolues (release Android et iOS) vérifiées sur la base OSV (osv.dev) le 24/10/2026 :
  **aucune vulnérabilité connue**. Comprend OkHttp 5.5.1, Retrofit 3.0.0, Ktor 3.6.0, supabase-kt 3.8.0,
  RevenueCat 10.28.1 / KMP 3.15.0, Firebase Messaging 26.0.1, Lottie 6.7.1, et supabase-js 2.49.4 de l'Edge Function.
- Vérification des dépendances Gradle (`verification-metadata.xml`) : **pas activée maintenant**. Trois développeurs
  ajoutent des bibliothèques en parallèle : chaque ajout casserait le build des autres. À activer avant la première
  publication (commande dans a-faire-par-toi.md).

## 8. iOS

- Session dans le Keychain (voir 3), ATS strict, schéma `io.jauge.app` réservé au retour de connexion, Universal Links
  `applinks:jauge.io` validés par `AnalysisLink`.
- `aps-environment` vaut `development` dans `iosApp.entitlements` : Xcode le remplace par `production` avec un profil
  de distribution ; à vérifier à la première archive.
- Le code iOS compile (`compileKotlinIosArm64`) ; l'app n'a pas pu être liée ni lancée ici (pas de Xcode).

## Ce qui reste

1. Tester la version release sur un vrai téléphone (R8 activé : vérifier connexion, achat, partage, notifications).
   Sans API en ligne, la release affiche des erreurs aimables : c'est attendu, le mode sans API est réservé au debug.
2. Réglages hors dépôt : voir [a-faire-par-toi.md](a-faire-par-toi.md).
3. Quand l'API `https://api.jauge.io` existera : la relire (autorisation par utilisateur sur chaque `{id}`, limites,
  taille des images, validation des URL d'offres côté serveur pour éviter la SSRF).
