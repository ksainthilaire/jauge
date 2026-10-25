# Jauge API contract

This document and `docs/openapi.yaml` (OpenAPI 3.1, the reference) describe the API. It is implemented by the Edge
Function `supabase/functions/api` (architecture, queue, costs and deployment in `docs/backend/`); it is not deployed
yet. Release builds call `https://api.jauge.io/`; debug builds call `jauge.api.baseUrl` when it is set, and the
**offline mode** (see the end of this document) otherwise.

- Base URL: `https://api.jauge.io/` (release builds). Debug builds may use another URL with `jauge.api.baseUrl`, for
  example the Supabase function `https://<project>.supabase.co/functions/v1/api/` (same routes).
- Paths in English, versioned under `/v1`. JSON bodies in UTF-8, except image uploads (multipart).
- Dates: ISO 8601 (`2026-10-23`, `2026-10-23T08:00:00Z`). Months: `2026-10`.
- Salaries: annual gross, in thousands of euros (`{"minimum": 36, "maximum": 41}` means 36 to 41 k€).
- Scores: integer from 0 to 100. 0 to 33 is calm, 34 to 66 disputed, 67 to 100 crowded.
- Unknown fields must be ignored by clients (the app does), so the API can add fields at any time.

## Headers

| Header | Value |
|---|---|
| `Authorization` | `Bearer <Supabase access token>` (anonymous sessions included). Absent until authentication is wired. |
| `Accept-Language` | Language of the device (`fr-FR`, `en-US`). Texts written by the API (reliability reasons) follow it. |
| `Accept` | `application/json` |

## Endpoints

| Method and path | Purpose | Screen |
|---|---|---|
| `POST /v1/analyses` | Start an analysis: JSON `{type: "link", url, area}`, `{type: "manual", jobTitle, city, area}` or `{type: "text", text, origin, area}` (text of a capture read on the phone, preferred), or multipart `image` + `origin` (`screenshot` or `photo`) + `areaCity`, `areaDepartment`, `areaRadius`. Optional header `Idempotency-Key`. Answers `202` with status `processing` (or already `completed` when the offer is in the 7 day cache). | 02, 10 |
| `GET /v1/analyses?cursor=&limit=&query=` | Analyses of the user, newest first, paginated with `nextCursor`. Optional `query`: see [Search](#search). | 02 |
| `GET /v1/analyses/{id}` | One analysis. Polled about every second while `status` is `processing`. | 03, 04 |
| `DELETE /v1/analyses/{id}` | Delete an analysis. | 02 |
| `PUT /v1/analyses/{id}/favorite` | Star an analysis (favorite). Idempotent, answers `204`. | 02, 04 |
| `DELETE /v1/analyses/{id}/favorite` | Remove the star. Idempotent, answers `204`. | 02, 04 |
| `GET /v1/analyses/{id}/salary?city=&department=` | Salary scale of the area, median, middle half, estimated range of the offer, negotiation target. | 05 |
| `GET /v1/analyses/{id}/skills` | Skills asked for by twin offers, with the share of offers and whether the user has them. | 05 |
| `GET /v1/analyses/{id}/trend` | Applicants per offer over 12 months and the change over 3 months. | 05 |
| `GET /v1/analyses/{id}/twins?sort=&city=&department=&radius=&query=` | Twin offers within `radius` km of the search area, `sort` = `least_disputed` (default), `best_paid` or `most_recent`. Optional `query`: see [Search](#search). | 06 |
| `POST /v1/analyses/{id}/feedback` | `{actualApplicants}`: the real number known by the user. | 09 |
| `GET /v1/methodology?analysisId=` | How the estimate was made: range, reliability and its reasons, sources. | 09 |
| `GET /v1/alerts` | Calm alerts of the user. | 06, 12 |
| `POST /v1/alerts` | `{analysisId, threshold}` (30 by default): notify when a twin goes under the threshold. | 06 |
| `DELETE /v1/alerts/{id}` | Delete a calm alert. | 06 |
| `GET /v1/shares` | Shares of the user. | tab "Partages" |
| `POST /v1/shares` | `{analysisId, format, hideCompany, showCalmTwin}`: creates the public link and the card image. | 07 |
| `DELETE /v1/shares/{id}` | Delete a share: the public link stops working. | tab "Partages" |
| `GET /v1/account` | The account (name, sign in method, number of analyses, consents). | 11, 13 |
| `PATCH /v1/account/consents` | `{marketingEmails}`. | 11 |
| `DELETE /v1/account` | Delete the account and its data (required by the stores). Answers `202`. The server also deletes the Supabase auth user, the devices and notifications rows, revokes the Sign in with Apple token (`POST https://appleid.apple.com/auth/revoke`, App Store guideline 5.1.1(v)) and deletes the RevenueCat customer (`DELETE /v1/subscribers/{app_user_id}`). Same flow for the requests received at privacy@jauge.io from the page jauge.io/supprimer-mon-compte. | 13 |
| `GET /v1/subscription` | Jauge Plus status known by the server (from RevenueCat webhooks) and free analyses left this week. | 08, 13 |
| `GET /public/analyses/{id}` | No token. What the page `jauge.io/a/{id}` shows (no user data), while a share exists. | web |
| `GET /health` | No token. Health check. | |
| `POST /webhooks/revenuecat` | RevenueCat only (Authorization header set in RevenueCat). | |

`DELETE /v1/account` accepts an optional body `{appleAuthorizationCode}`: a fresh Sign in with Apple code that lets the
server revoke the Apple tokens. Calm alerts (`POST /v1/alerts`) are a Jauge Plus feature: free users get `402
quota_reached`. `result.reputation` (optional) sums up what public Reddit and X posts say about the company, with
`links` to the posts it relies on (no usernames). `result.advice` (optional, null when unavailable) is the advice of
the verdict written by the AI in the language of the analysis: `text` ("Postule avant jeudi..."), `negotiationArgument`
and `missingSkills`; every number in it comes from the result, never from the AI (`docs/backend/ia.md`). Both fields
are additions: older apps ignore them.

### Analysis lifecycle

1. `POST /v1/analyses` answers `202` with `status: "processing"`, `progressPercent` and `steps`
   (`position_and_place`, `skills`, `comparison`, `applicants`, each `pending`, `running` or `done`, with an optional
   `detail`). `publishedDaysAgo` and `hasDisplayedSalary` appear as soon as they are known (box "Déjà trouvé").
2. The app polls `GET /v1/analyses/{id}` every second (90 polls at most, then a timeout message).
3. `status: "completed"` brings `offer` and `result`. `status: "failed"` brings `failure.code`:
   `unreadable_offer` (with `recognizedCompany` when the company could still be read) or `unsupported_link`.
4. An unreadable image does not use the free analysis of the week (nor an unsupported link, nor a server failure).
5. Images are read once and deleted at once (24 hours at most); the text read on the phone (`type: "text"`) is better:
   the image never leaves the phone. The app only sends `type: "text"` (ML Kit on Android, Vision on iOS); when the
   phone reads less than 40 characters it shows the screen 10 without calling the API.

### Search area (location)

The analyses, the twin offers ("à moins de 30 km") and the salaries depend on the **search area** of the user: a city,
its French department and a radius (`{"city": "Lyon", "departmentCode": "69", "radiusKilometers": 30}`, query
parameters `city`, `department`, `radius` on `GET`, parts `areaCity`, `areaDepartment`, `areaRadius` on multipart).

- The app asks the phone for its **approximate** position only after its own explanation (Android
  `ACCESS_COARSE_LOCATION`, iOS "When In Use" with reduced accuracy) and turns it into the nearest city **on the
  phone** (`core:location`, table of the prefectures and large cities). The position is never sent: the API only ever
  receives a city and a department (GDPR minimization).
- Refused or unavailable position: `Paris` (`75`), radius 30 km. The user can type another city in Settings; a typed
  city always wins over the position.
- When no area is sent, the server uses the place of the offer.

### Favorites

Analyses (`GET /v1/analyses` items and `GET /v1/analyses/{id}`) carry `isFavorite` (false when absent). The app keeps
the stars on the device too, so they show at once and survive a restart. A star change is optimistic: the star changes
on screen at once, then the app calls `PUT` or `DELETE /v1/analyses/{id}/favorite`; on failure the star comes back
and a friendly message says it could not be saved. The server is the reference for the analyses it returns: their
`isFavorite` replaces the local value at every refresh. The filter "Favoris" of the list is applied by the app.

### Share links

The public link of an analysis is always `https://jauge.io/a/{analysisId}`. `POST /v1/shares` returns it in `url`,
and the app rebuilds it from the analysis identifier anyway. The card image (`imageUrl`) is rendered by the server
for the `story` and `square` formats, with the options chosen by the user (company hidden, calm twin shown).

## Errors

Every `4xx` and `5xx` answer has this body. `message` is for developers and is never shown to the user.

```json
{ "error": { "code": "quota_reached", "message": "Weekly free analysis already used" } }
```

| Code | HTTP | Meaning | What the user sees |
|---|---|---|---|
| `quota_reached` | 402 | Free analysis of the week used | Paywall invitation |
| `unreadable_offer` | 422 | Image unreadable | Mockup 10 (retake, paste link, manual entry) |
| `invalid_link` | 422 | Not a link | "Ce n'est pas un lien" |
| `unsupported_link` | 422 | Site or page not readable | Suggests a screenshot |
| `image_too_large` | 413 | Image over 10 MB | Suggests a screenshot |
| `validation_failed` | 400, 422 | Invalid body | Generic friendly message |
| `unauthorized` | 401 | Session expired | Sign in again |
| `not_found` | 404 | Item deleted or unknown | "Introuvable" |
| `rate_limited` | 429 | Too many requests | Wait a few seconds |
| `maintenance` | 503 | Planned maintenance | "Jauge fait une pause" |
| `internal` | 500 | Server bug | Generic friendly message |

Without a known code, the app maps the HTTP status (401/403 session, 402 quota, 404/410 not found, 408 timeout,
413 image too large, 400/422 invalid data, 429 rate limit, 5xx unavailable). Network failures (offline, timeout) and
unreadable bodies are mapped too. The mapping lives in `core/data/.../error/ErrorMapping.kt` and the texts in
`core/ui/src/commonMain/composeResources/values*/strings.xml`.

## Search

`GET /v1/analyses` and `GET /v1/analyses/{id}/twins` accept an optional `query` (100 characters at most). The server
keeps the elements whose job title, company or city (place for twins) contain every word of the query, in any order,
ignoring case, accents, ligatures and the middle dots of inclusive writing (`chargee` finds `Chargé·e`, `ecully` finds
`Écully`). An absent or blank `query` returns everything. It combines with `sort` and the area parameters.

The app shows a search field above these lists once they hold more than 6 elements and filters the loaded list on the
device at once (short delay while typing), with the same rules (`core/common/.../text/SearchText.kt`), combined with
the sort chips of mockup 06 and the "Toutes" / "Favoris" filter of the full list. The offline mode applies `query` and
`sort` like the server.

## Clients in the app

- Common interface: `core/network/src/commonMain/.../JaugeApi.kt`. Implementations throw only `NetworkFailure`.
- Android: Retrofit + OkHttp (`RetrofitJaugeService`, `RetrofitJaugeApi`, timeouts 15 s connect, 30 s read/write).
- iOS: Ktor with the Darwin engine (`KtorJaugeApi`), same interface and same failures.
- Offline: `core/data/.../offline/OfflineJaugeApi.kt`.

## Offline mode ("mode sans API")

`OfflineJaugeApi` implements the same interface with realistic data from the mockups (Atelier Sorel, score 78,
about 210 applicants, 36 to 41 k€, twins around Lyon), a simulated latency (350 to 900 ms) and an analysis that
progresses 20, 40, 62, 85 % then completes. New analyses, alerts and shares are kept in memory; favorites are kept on the device (key
`offline.analyses.favorites`), like a server would. It follows the search area: salaries take the
city of the area, twin offers keep the radius and move to the city of the area outside Rhône, new analyses use the
radius of the area.

Debug builds only. It is on when `jauge.api.offlineMode` is `true`, or when it is empty and no `jauge.api.baseUrl`
is set; release builds never use it. In debug builds a long press on the "Profil" tab switches between offline and real API at runtime. `OfflineScenarioController`
simulates failures: `NO_CONNECTION`, `TIMEOUT`, `SERVER_UNAVAILABLE`, `SESSION_EXPIRED`, `QUOTA_REACHED`,
`UNREADABLE_OFFER`, `INVALID_RESPONSE` and `EMPTY` (empty lists).
