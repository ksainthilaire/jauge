# Jauge architecture

Read this before building a screen. It explains the modules, the conventions and the exact steps to add a screen.
The mockups are in `~/maquettes-apps-2410/annonce-emploi/` (`ecrans.html` is the source of truth for colors, sizes and
texts, `BRANDING.md` for the brand).

## Stack and versions

| Tool | Version |
|---|---|
| Kotlin (and the Compose compiler plugin) | 2.4.21 |
| Compose Multiplatform | 1.12.2 (Material 3 1.9.0) |
| Android Gradle Plugin | 9.4.1 (Kotlin Multiplatform modules use `com.android.kotlin.multiplatform.library`) |
| Gradle (wrapper) | 9.8.2, JDK 17 |
| Android SDK | compile 37, target 36, min 26 |
| Navigation (JetBrains, multiplatform) | 2.10.0, type safe routes with kotlinx.serialization |
| Lifecycle / ViewModel (JetBrains) | 2.11.0 |
| Koin | 4.2.2 (BOM) |
| Coroutines / Serialization / Datetime | 1.11.0 / 1.12.0 / 0.8.0 |
| Retrofit / OkHttp (Android) | 3.0.0 / 5.5.1 |
| Ktor (iOS) | 3.6.0 |
| RevenueCat purchases-kmp / Supabase / Lottie / Compottie | 3.15.0 / 3.8.0 / 6.7.1 / 2.3.3 |
| ML Kit Text Recognition (Android, bundled Latin model) | 16.0.1 |

Every version lives in `gradle/libs.versions.toml`. Never write a version in a module build file.

## Modules

```
androidApp            Android application (MainActivity, JaugeApplication, launcher icon)
iosApp                Xcode project, calls MainViewController() of the ComposeApp framework
composeApp            Shared app: root composable, navigation graph, tab bar, Koin start, iOS entry point
feature/onboarding    01 welcome, 12 alerts permission
feature/analysis      02 paste an offer (home), 03 reading, 10 unreadable offer, manual entry, history
feature/verdict       04 verdict, 05 details (salaries, skills, trend), 09 methodology
feature/twins         06 twin offers and calm alert (tab "Veille")
feature/share         07 share card and link, list of shares (tab "Partages")
feature/paywall       08 Jauge Plus (single monthly offer, 3 day trial)
feature/account       11 account and consents, 13 profile and subscription (tab "Profil"), settings, legal
                      notice, privacy policy, terms of use, account deletion, about
core/designsystem     Tokens (colors, typography, spacing, radii), fonts, icons, reusable components
core/ui               JaugeError to friendly message mapping, ContentState for screens
core/domain           Models, repository interfaces, use cases, JaugeError, Outcome (pure Kotlin)
core/data             Repository implementations, payload mapping, error mapping, offline mode
core/network          JaugeApi interface and payloads, Retrofit/OkHttp (Android), Ktor (iOS)
core/billing          Store purchases (BillingClient): RevenueCat purchases-kmp, see docs/revenuecat.md
core/notifications    NotificationRepository: push tokens (FCM, APNs), notifications table, Realtime calm alerts
core/textrecognition  TextRecognizer: text of screenshots and photos read on the phone (Android ML Kit, bundled model;
                      iOS Apple Vision). Images never leave the phone
core/location         LocationRepository: approximate position (Android LocationManager, iOS CoreLocation) turned into a
                      city on the phone (FrenchCities), never sent
core/authentication   AuthenticationRepository: Supabase Auth (Apple, Google, email link), simulated without Supabase
core/common           Build configuration, AppEnvironment, ApiModeController, dispatchers, platform
build-logic           Convention plugins shared by the modules
```

Dependency rules (enforced by the convention plugins, keep them):

- `feature:*` depends on `core:designsystem`, `core:ui`, `core:domain`, `core:common`. Never on another feature,
  never on `core:data` or `core:network`.
- `core:domain` depends only on `core:common` and kotlinx-datetime. No Compose, no network.
- `core:data` implements the domain repositories with `core:network` and `core:billing`.
- `composeApp` is the only module that knows every module: it wires navigation and Koin.

Convention plugins (`build-logic/convention`):

| Plugin id | Use |
|---|---|
| `jauge.kotlin.multiplatform.library` | any shared module: Android + iosArm64 + iosSimulatorArm64, Koin, coroutines, tests |
| `jauge.compose.multiplatform` | modules with Compose UI and resources (`Res` generated in `<namespace>.resources`) |
| `jauge.feature` | feature modules: the above plus navigation, view models and core dependencies |

Namespaces and packages follow the Gradle path: `:feature:verdict` is `io.jauge.feature.verdict`, and its strings are
in `io.jauge.feature.verdict.resources.Res`.

## Conventions

- Code, comments, commit messages and documentation in English. Commits: `feat: ...`, `fix: ...`, `docs: ...`, with no
  co-author line.
- No abbreviations in names: `analysisRepository`, `viewModel` written in full in names (`verdictViewModel`), `button`,
  `image`, `message`, `error`, `configuration`. Not `repo`, `vm`, `btn`, `img`, `msg`, `err`, `res`, `config`, `ctx`.
  (`Res` is the class generated by Compose resources, not ours.)
- KDoc on every public class and function.
- Never the characters "em dash" or "en dash" anywhere (texts, code, comments, commits): use "-", ":" or a comma.
- **No text in code.** Every user visible text is in `src/commonMain/composeResources/values/strings.xml` (French,
  default) and `values-en/strings.xml` (English) of the module that shows it. Data coming from the API (job titles,
  companies) is not interface text. In strings, write apostrophes as is (`l'annonce`), never `\'`.
- **Never a technical error on screen.** Repositories return `Outcome`; failures are `JaugeError`; screens show
  `FriendlyErrorView(error)` or a message from `error.toFriendlyMessage()`. A value that cannot be read is shown as
  an empty field, never as a wrong value.
- **Reactive and refreshable.** Every screen that loads data exposes a `StateFlow` from its view model and wraps its
  scrollable content in `JaugeRefreshContainer` (pull to refresh).

## Adding a screen, step by step

Example: building the verdict screen (mockup 04) in `feature/verdict`.

1. **Route.** It already exists in `feature/verdict/.../navigation/VerdictNavigation.kt`:
   `@Serializable data class VerdictRoute(val analysisId: String)`. For a new screen, add a route there (a
   `data object` without arguments, a `data class` with arguments) and a `composable<NewRoute> { ... }` entry in the
   `verdictGraph` function.

2. **Strings.** Add the texts of the screen to `feature/verdict/src/commonMain/composeResources/values/strings.xml`
   and `values-en/strings.xml`, prefixed with the screen (`verdict_share`, `verdict_details`, ...). Use
   placeholders for values: `<string name="verdict_applicants_range">fourchette %1$d à %2$d</string>`.

3. **View model**, next to the screen (`feature/verdict/.../verdict/VerdictViewModel.kt`), with a `StateFlow` and the
   shared `ContentState` helpers:

   ```kotlin
   /** State holder of the verdict screen. */
   class VerdictViewModel(
       private val analysisId: String,
       private val getAnalysis: GetAnalysisUseCase,
   ) : ViewModel() {
       private val state = MutableStateFlow(ContentState<Analysis>())

       /** Observed state of the screen. */
       val uiState: StateFlow<ContentState<Analysis>> = state.asStateFlow()

       init { refresh() }

       /** Loads the analysis, also called by pull to refresh and by "Réessayer". */
       fun refresh() {
           viewModelScope.launch {
               state.update { it.refreshing() }
               state.update { it.reduce(getAnalysis(analysisId)) }
           }
       }
   }
   ```

4. **Koin.** Declare it in the module of the feature (`feature/verdict/.../di/VerdictModule.kt`):

   ```kotlin
   val verdictModule = module {
       viewModel { parameters -> VerdictViewModel(analysisId = parameters.get(), getAnalysis = get()) }
   }
   ```

   Feature modules are already listed in `composeApp/.../di/JaugeKoin.kt` (`featureModules`). Use cases come from
   `domainModule`, repositories from `dataModule`.

5. **Screen.** Read the route arguments in the graph and get the view model with Koin:

   ```kotlin
   composable<VerdictRoute> { entry ->
       val route = entry.toRoute<VerdictRoute>()
       VerdictScreen(analysisId = route.analysisId, onBack = onBack, onShare = onShare)
   }

   @Composable
   fun VerdictScreen(analysisId: String, onBack: () -> Unit, onShare: (String) -> Unit) {
       val viewModel: VerdictViewModel = koinViewModel { parametersOf(analysisId) }
       val state by viewModel.uiState.collectAsStateWithLifecycle()
       JaugeScreenScaffold(title = ..., navigationIcon = { RoundIconButton(JaugeIcons.Back, ..., onBack) }) {
           JaugeRefreshContainer(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh) {
               when {
                   state.isLoading -> SkeletonCard()
                   state.showsFullScreenError -> FriendlyErrorView(state.error!!, onAction = { viewModel.refresh() })
                   else -> VerdictContent(state.content!!)
               }
           }
       }
   }
   ```

6. **Navigation to another feature.** Add a callback parameter to the graph function (`onShare: (String) -> Unit`)
   and wire it in `composeApp/.../navigation/JaugeNavigationHost.kt`
   (`verdictGraph(onBack = onBack, onShare = { id -> navigationController.navigate(ShareRoute(id)) })`). Features never
   import each other.

7. **Tests.** Unit test the view model or any logic you add (`src/commonTest`), with fakes of the use case
   repositories as in `core/domain/src/commonTest`.

Screens that are dark in the mockups (01, 03, 08, 12) wrap their content in `JaugeTheme(darkTheme = true) { ... }`.
Tab roots (home, twins, shares, profile) do not show a back button; the tab bar is drawn by `composeApp`.

## Design system

`JaugeTheme` provides the tokens: `JaugeTheme.colors` (light and dark), `JaugeTheme.typography`,
`JaugeTheme.spacing` (screen gutter 22 dp, cards 16 dp, ...), `JaugeTheme.radii` (cards 22 dp, buttons 18 dp).
Fonts are embedded (`core/designsystem/src/commonMain/composeResources/font`, SIL Open Font License): Bricolage
Grotesque for titles, Instrument Sans for text, JetBrains Mono for numbers.

Components (`io.jauge.core.designsystem.component`): `JaugeButton` (primary coral, neutral ink, outlined),
`JaugeCard`, `MetricTile`, `JaugeGauge` (animated 0 to 100 gauge, sizes LARGE, MEDIUM, MINI), `GaugeLevel` and
`LevelPill` (calm, disputed, crowded: always the word with its color), `JaugePill`, `JaugeChip` (filter or segment),
`JaugeTextField`, `JaugeTabBar`, `EmptyState`, `FriendlyErrorState`, skeletons (`Modifier.skeleton()`,
`SkeletonList`, `SkeletonCard`), `JaugeRefreshContainer`, `JaugeSwitch`, `JaugeProgressBar`, `JaugeScreenScaffold`,
`JaugeTopBar`, `RoundIconButton`, `JaugeLottieAnimation` (Lottie, `JaugeAnimation.LOADING` for reading an
offer on screen 03 and loading, `JaugeAnimation.SUCCESS` played once; lottie-compose on Android, Compottie on iOS;
the JSON files are generated in the brand colors by `tools/lottie/generate_animations.py`). Icons: `JaugeIcons` (line icons drawn from the mockup symbols).

Map a domain `CompetitionLevel` to a `GaugeLevel` with `GaugeLevel.valueOf(level.name)` or `GaugeLevel.fromScore`.

## Errors

`JaugeError` (domain) lists every case: no connection, timeout, server unavailable, too many requests, not found,
session expired, quota reached, unreadable offer, invalid or unsupported link, image too large, storage full, purchase
cancelled, unavailable or pending, invalid data, unknown. `core:data` maps exceptions and API codes to it
(`ErrorMapping.kt`); `core:ui` maps it to texts and a suggested action (`FriendlyMessage.kt`, `FriendlyAction`).
A cancelled purchase is silent (`error.isSilent`).

## Configuration and keys

Values are read at build time from `local.properties` (ignored by git, see `local.properties.example`) and
generated into `io.jauge.core.common.configuration.BuildConfiguration`. Environment variables override them:

| Property | Environment variable | Use |
|---|---|---|
| `jauge.api.baseUrl` | `JAUGE_API_BASE_URL` | API base URL of **debug** builds (for example `https://<project>.supabase.co/functions/v1/api/`); release builds always use `https://api.jauge.io/` |
| `jauge.api.offlineMode` | `JAUGE_API_OFFLINE_MODE` | Debug builds: `true` forces the offline mode, `false` the API; empty: the API as soon as `jauge.api.baseUrl` is set, the offline mode otherwise |
| `supabase.url` | `JAUGE_SUPABASE_URL` | Supabase project URL |
| `supabase.publishableKey` | `JAUGE_SUPABASE_PUBLISHABLE_KEY` | Supabase publishable key only |
| `revenuecat.testStoreApiKey` | `JAUGE_REVENUECAT_TEST_STORE_API_KEY` | RevenueCat Test Store key, used by debug builds |
| `revenuecat.googleApiKey` | `JAUGE_REVENUECAT_GOOGLE_API_KEY` | RevenueCat public key for Google Play (release) |
| `revenuecat.appleApiKey` | `JAUGE_REVENUECAT_APPLE_API_KEY` | RevenueCat public key for the App Store (release) |

The build **fails** if a secret key is found (RevenueCat `sk_`, Supabase `sb_secret_` or service role). On iOS,
`iosApp/Configuration/Config.xcconfig` (ignored by git, copy `Config.xcconfig.example`) defines the same environment
variables; Xcode exports them to the Gradle build it runs. In xcconfig files write URLs as `https:/$()/host` because
`//` starts a comment.

`AppEnvironment` exposes the values at runtime (`isBillingConfigured`, `isSupabaseConfigured`). Missing values:
offline mode and purchases disabled (`UnavailableBillingClient`), without crash.

## Offline mode

`ApiModeController` (core:common) holds the mode. `SwitchingJaugeApi` sends every call to the real client or to
`OfflineJaugeApi` (core:data), which serves data from the mockups with a simulated latency. An indicator
("Données d'exemple", debug builds only) is shown at the top of the app while it is on.

- Build setting: `jauge.api.offlineMode`, see the table above (`AppEnvironment.selectOfflineMode`): a configured API
  URL turns the offline mode off by default. **Debug builds only**: release
  builds always call the real API, never sign in with `SimulatedAuthenticationRepository` (they use
  `UnavailableAuthenticationRepository` when Supabase is not configured) and an iOS Release framework never embeds the
  Test Store key (see `docs/audit/securite.md`).
- Hidden switch, debug builds only: long press on the "Profil" tab.
- Error simulation: `OfflineScenarioController.select(OfflineScenario.UNREADABLE_OFFER)` (also `NO_CONNECTION`,
  `TIMEOUT`, `SERVER_UNAVAILABLE`, `SESSION_EXPIRED`, `QUOTA_REACHED`, `INVALID_RESPONSE`, `EMPTY`). It is a Koin
  singleton: a debug menu can inject it.
- Purchases use RevenueCat as soon as a key is configured, also in offline mode: debug builds use the RevenueCat
  Test Store key, so nothing is charged. Without any key purchases are disabled (`UnavailableBillingClient`).

## Building and testing

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew :androidApp:assembleDebug          # Android app
./gradlew testAndroidHostTest                # unit tests of every module (JVM)
./gradlew :composeApp:compileKotlinIosArm64  # iOS Kotlin code (no Xcode needed)
```

Linking the iOS framework and running the app need Xcode: open `iosApp/iosApp.xcodeproj`; its build phase runs
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`.

Tests live in `src/commonTest` (run on the JVM as Android host tests) and `src/androidHostTest` (Android only, for
example the Retrofit client with MockWebServer).

## Share links and App Links

The public link of an analysis is `https://jauge.io/a/{analysisId}` (`ShareLinks.forAnalysis`). Opening it on a
phone with the app installed opens the verdict:

- Android: `androidApp/src/main/AndroidManifest.xml` declares an `autoVerify` intent filter for `https://jauge.io/a/`
  (and `www.`); `MainActivity` (`singleTask`) hands the link to `IncomingLinks`.
- iOS: `iosApp/iosApp/iosApp.entitlements` declares `applinks:jauge.io`; `iOSApp.swift` calls
  `MainViewControllerKt.openIncomingLink` from `onOpenURL` and `onContinueUserActivity`.
- `JaugeApp` reads `IncomingLinks.pending`, parses it with `AnalysisLink` (`feature:share`) and opens `VerdictRoute`.
- The website must serve the two verification files, templates in `docs/web/.well-known/`: `assetlinks.json` (package
  `io.jauge.app` and the SHA 256 fingerprints of the Play app signing key and of the upload key) and
  `apple-app-site-association` (team identifier and bundle `io.jauge.app`, path `/a/*`), both over HTTPS, without
  redirection, with the `application/json` content type.

The share card (`feature:share`) is rendered by the app from the card drawn on screen (`GraphicsLayer`), encoded as
PNG and sent with `NativeSharer`: Android intents with a `FileProvider` declared in the manifest of `feature:share`,
MediaStore to save it; iOS `UIActivityViewController`, `MFMessageComposeViewController` and the photo album
(`NSPhotoLibraryAddUsageDescription` in `Info.plist`).

## No technical information on screen

The user never sees technical details: no library names, no build numbers, no internal identifiers. The About screen
shows only the public version ("Version 1.0", `displayVersion`). The notices required by the licenses of the
libraries ship with the app in `composeApp/src/commonMain/composeResources/files/third_party_notices.txt`, not shown.

## Orientation

The app is portrait only: `android:screenOrientation="portrait"` on `MainActivity` (`"behind"` on the translucent
notification permission activity), `UISupportedInterfaceOrientations` = portrait on iOS (iPhone only). Android 16
ignores the forced orientation on large screens, which is accepted.

## Reading screenshots and photos on the phone

A screenshot or a photo (`OfferSource.Image`) is never uploaded. `AnalyzeOfferUseCase` asks the `TextRecognizer` of
`core:textrecognition` for its text, cleans it (`RecognizedText.clean`: trimmed lines, no repeated spaces or empty
lines, cut at 20 000 characters) and sends only the text: `POST /v1/analyses` with
`{type: "text", text, origin: "screenshot" | "photo", area}` (`OfferSource.Text`).

- Android: ML Kit Text Recognition with the Latin model **bundled** in the app (`com.google.mlkit:text-recognition`):
  offline, no Google Play service, no paid Google API. A photo taken sideways (the picker drops the orientation tag)
  is also read turned by 90 and 270 degrees when the upright reading finds little text; the longest text wins.
- iOS: Apple Vision (`VNRecognizeTextRequest`, accurate level, French then English), on a background dispatcher.
- **Fallback**: when the recognizer fails or the text is shorter than 40 characters (or has fewer than 20 letters or
  digits), the analysis fails at once with `JaugeError.UnreadableOffer` and the screen 10 "offre illisible" opens
  (new photo, or manual entry). Nothing is sent, so the free analysis of the week is kept. The multipart image upload
  of the contract stays for other clients but the app never uses it; `AnalysisRepositoryImplementation` refuses an
  `OfferSource.Image` the same way, so an image can never be uploaded by mistake.

## Real API details (contract of 25/10/2026)

- **Idempotency**: every analysis request has a key (`AnalysisRequestKeys`, 32 letters and digits) sent as
  `Idempotency-Key`. `ReadingViewModel` keeps it on "Réessayer" after a lost answer (no connection, timeout, busy
  server: `AnalysisRequestKeys.keepsKeyOnRetry`) and takes a new one after any other failure.
- **Polling**: `PollingSchedule`: every second for 10 polls, then every 2 seconds, 50 polls (90 seconds) before the
  timeout message.
- **Advice and reputation** (`result.advice`, `result.reputation`, both optional): the advice text replaces the computed
  "Postule avant..." card of the verdict, its negotiation sentence replaces the computed one in the salaries of the
  details and its missing skills are listed under the skills. `ReputationCard` (verdict, under the key figures) shows the
  summary, a tone pill and links: the posts sent by the server (`https` only), or a search of the company on each source
  when there are none. Without these fields the screens stay as before.
- **Calm alerts**: `402` (free user) on `POST /v1/alerts` opens the paywall (`TwinOffersEvent.PaywallRequired`).
- **Account deletion**: for accounts signed in with Apple, `DeleteAccountUseCase` asks `AppleAuthorization` for a
  fresh code (iOS: native Apple sheet, `ASAuthorizationAppleIDProvider`, capability "Sign in with Apple" in
  `iosApp.entitlements`) and sends `{appleAuthorizationCode}`. Closing the sheet keeps the account, silently. Android
  cannot get a code for the client `io.jauge.app` (no native Sign in with Apple): the deletion is sent without it,
  which the API accepts.
- `GET /public/analyses/{id}` is not used by the app: a shared link opens the verdict of the owner's analysis through
  `GET /v1/analyses/{id}`; the public route serves the web page `jauge.io/a/{id}`.

## Location and search area

`SearchArea` (city, department, radius, origin) is used by the analyses, the twin offers and the salaries; core:data
reads it with `SearchAreaSource` and sends only the city, the department and the radius (`AreaPayload`). The default is
`SearchArea.DEFAULT` (Paris, 30 km). `LocationPromptHost` (feature:onboarding, shown by `JaugeApp` once the first launch
is over) explains why the city helps, then `LocateSearchAreaUseCase.allow` shows the system request (Android
`LocationPermissionActivity`, iOS `requestWhenInUseAuthorization`). The position is turned into the nearest city by
`FrenchCities` on the phone. A city typed in Settings (`SearchAreaOrigin.MANUAL`) always wins; "Utiliser ma position"
goes back to the position.

## Favorites

`AnalysisRepository.setFavorite` is optimistic: the identifier set (`observeFavoriteIds`, kept on the device by
`FavoriteStore`) changes at once, the API is called (`PUT`/`DELETE /v1/analyses/{id}/favorite`), and the change is
undone on failure. `observeHistory` merges the set into `AnalysisSummary.isFavorite`. Screens use
`FavoriteAnalysisUseCase` and the `FavoriteStarButton` component (home 02, "Tout voir" with the filter `AnalysisFilter`,
verdict 04); a refused star shows a friendly snackbar. Deleting the account clears the local set.

## Git flow

`main` holds releases, `develop` is the integration branch. One branch per feature, `feature/<name>`, from
`develop`, merged back with `git merge --no-ff` once `./gradlew :androidApp:assembleDebug testAndroidHostTest`
passes.

## Next phases (not done yet)

- Persistence of preferences (onboarding seen, offline switch) if needed.

## Supabase, push and purchases

- `core:authentication` builds the Supabase client and implements `AuthenticationRepository` (Apple, Google, email
  link, deep link `io.jauge.app://login-callback`) and `AccessTokenProvider`. `core:notifications` implements
  `NotificationRepository` (tables `devices` and `notifications`, Realtime, FCM and APNs). Without Supabase values
  both use simulated implementations. Setup steps: `docs/supabase.md`; SQL and Edge Function: `supabase/`.
- `SessionSynchronizer` (composeApp) links RevenueCat to the signed in user, saves the push token and receives the
  sign in deep links; `CalmAlertBannerHost` shows calm alerts received by Realtime above every screen.
- Purchases: `docs/revenuecat.md`.

## Naming of network types

Network bodies are called payloads (`AnalysisPayload`, `CreateShareRequestPayload`) in
`io.jauge.core.network.payload`, and are mapped to domain models in `core/data/.../mapper/PayloadMappers.kt`.
Domain models never leave `core:domain` as payloads, and payloads never reach a screen.
