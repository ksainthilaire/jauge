# Jauge

> Before you apply, look at the crowd.

Jauge is a mobile app for job seekers. Paste the link, a screenshot or a photo of a job offer and Jauge estimates
how many people are applying, what the job really pays, which skills matter, how competition evolves, and finds
"twin" offers with fewer applicants. Results can be shared as a link (`https://jauge.io/a/{analysisId}`) or as a card.

## Platforms and stack

- Kotlin Multiplatform and Compose Multiplatform, targets Android and iOS.
- Modular clean architecture: `core:*` modules, one `feature:*` module per user journey, `composeApp` (navigation and
  dependency injection), `androidApp` and `iosApp` (Xcode project consuming the shared framework).
- Koin, Coroutines, Material 3, Compose Navigation, Retrofit and OkHttp (Android), Ktor (iOS), RevenueCat, Supabase.

## Getting started

1. Install JDK 17 and the Android SDK (platform 36).
2. Copy `local.properties.example` to `local.properties` and fill in the values you have. Missing values are fine:
   debug builds then start in offline mode (realistic sample data, no network) and purchases are disabled. Set
   `jauge.api.baseUrl` (for example the Supabase function URL) to call the real API from a debug build; release
   builds always call `https://api.jauge.io/`.
3. Build the Android app: `./gradlew :androidApp:assembleDebug`
4. Run the unit tests: `./gradlew testAndroidHostTest`
5. iOS: open `iosApp/iosApp.xcodeproj` in Xcode (copy `iosApp/Configuration/Config.xcconfig.example` to
   `iosApp/Configuration/Config.xcconfig` first).

See `docs/ARCHITECTURE.md` for the module layout and conventions, and `docs/api.md` for the API contract.

## Website

`web/` is the static site `jauge.io` (landing page, download page, share pages `/a/{id}`, the legal pages required by
the stores, app link files): HTML, CSS and vanilla JavaScript, no build, deployed on Cloudflare Pages. See `web/README.md`.

## Branches

Git flow: `main` holds releases, `develop` is the integration branch, and every feature lives on `feature/<name>`
and is merged into `develop` with `--no-ff`.
