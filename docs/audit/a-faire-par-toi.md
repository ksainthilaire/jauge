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
