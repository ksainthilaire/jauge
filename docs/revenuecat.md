# RevenueCat: what to create for Jauge Plus

The app sells a single subscription: **Jauge Plus, 6,99 € per month, with a 3 day free trial**. Purchases go through
the official RevenueCat SDK for Kotlin Multiplatform (`com.revenuecat.purchases:purchases-kmp-core`), implemented in
`core/billing` (`RevenueCatBillingClient`). The paywall is `feature/paywall` (mockup 08).

Only **public SDK keys** go into the app (`test_...`, `goog_...`, `appl_...`). The secret key (`sk_...`) is for servers
only: never put it in `local.properties`, in the app or in the repository (the build fails if it finds one).

## 1. Identifiers used by the code

| Item | Value | Where the code uses it |
|---|---|---|
| Entitlement | `plus` | `RevenueCatBillingClient.ENTITLEMENT_ID`: Jauge Plus is active when this entitlement is active |
| Product (both stores) | `jauge_plus_monthly` | `SubscriptionOffer.MONTHLY_PRODUCT_ID` (Google Play base plan: `monthly`) |
| Offering | `default`, marked **Current** | the app reads the current offering |
| Package | `$rc_monthly` (Monthly) | the app takes the monthly package of the current offering |

The price and the trial shown on the paywall come from the store (never written in the app): the paywall shows
what the store will really charge, in the currency of the user.

## 2. Products in the stores

### Google Play Console (app `io.jauge.app`)
1. Monetize > Products > Subscriptions > Create subscription: product id `jauge_plus_monthly`, name "Jauge Plus".
2. Add a base plan: id `monthly`, auto renewing, billing period 1 month, price 6,99 EUR (let Play convert the
   other countries), grace period as you like.
3. Add an offer on this base plan: id `free-trial`, eligibility "New customer acquisition", one phase
   **Free trial, 3 days**. Activate the base plan and the offer.
4. In Play Console > Setup > API access, link a service account and give it the financial permissions, then upload
   its JSON key in RevenueCat (Project settings > Apps > the Play Store app > Service account credentials).

### App Store Connect (bundle id of the iOS app)
1. Monetization > Subscriptions: create a subscription group "Jauge Plus".
2. Create the subscription: reference name "Jauge Plus monthly", product id `jauge_plus_monthly`, duration 1 month,
   price 6,99 EUR (tier of your choice for the other countries).
3. Introductory offer: **Free, 3 days**, all countries, new subscribers.
4. Fill the localization (name, description) in French and English, and the review screenshot (the paywall).
5. Create an In App Purchase key (Users and Access > Integrations > In-App Purchase) and upload it in RevenueCat
   (the App Store app > In-App Purchase Key), with the App Store Connect API key if RevenueCat asks for it.
6. Agreements, Tax and Banking: the Paid Apps agreement must be active or no product loads.

## 3. In the RevenueCat dashboard

1. **Project** "Jauge" with three apps:
   - **Test Store** (created automatically): used by debug builds, no store account needed, no charge;
   - **Play Store** app, package `io.jauge.app`;
   - **App Store** app, bundle id of `iosApp`.
2. **Products**: import or add `jauge_plus_monthly` in each app (Play Store: `jauge_plus_monthly:monthly`).
   In the Test Store, create a product `jauge_plus_monthly`, 1 month, 6,99 EUR, with a **3 day free trial**.
3. **Entitlement** `plus` ("Jauge Plus"): attach the product of each app (Test Store, Play Store, App Store).
4. **Offering** `default` ("Jauge Plus monthly"): one package of type **Monthly** (`$rc_monthly`) holding the product
   of each app. Mark the offering as **Current**.
5. **API keys** (Project settings > API keys), public SDK keys only:
   - Test Store key `test_...` for debug builds;
   - Play Store key `goog_...` for Android release builds;
   - App Store key `appl_...` for iOS release builds.
6. Optional: Integrations > Webhooks, to let the Jauge API know about renewals and cancellations (server side, with
   the secret key kept on the server).

## 4. Where to put the keys

`local.properties` (Android Studio, ignored by git, copy `local.properties.example`):

```
revenuecat.testStoreApiKey=test_...
revenuecat.googleApiKey=goog_...
revenuecat.appleApiKey=appl_...
```

`iosApp/Configuration/Config.xcconfig` (ignored by git, copy `Config.xcconfig.example`):

```
JAUGE_REVENUECAT_TEST_STORE_API_KEY=test_...
JAUGE_REVENUECAT_APPLE_API_KEY=appl_...
```

Debug builds use the Test Store key when it is set, release builds use the key of their store. Without any key the
app still starts: purchases are disabled cleanly and the paywall says "Achats indisponibles".

## 5. How the app behaves

- **Trial**: on Google Play the trial is shown only if the user is eligible (the default option of the product holds
  the free phase); on the App Store the app asks RevenueCat for the eligibility. Not eligible: the paywall shows
  "S'abonner pour 6,99 €/mois" and the legal text without trial.
- **Restore** ("Restaurer les achats"): restores the store purchases; if no active `plus` entitlement is found, the
  paywall says "Aucun achat à restaurer".
- **Manage the subscription** ("Gérer mon abonnement"): opens the management page given by RevenueCat, or the
  subscriptions page of the store (Google Play or App Store).
- **Account link**: `BillingClient.identify(userId)` logs the RevenueCat customer in with the Supabase user id, so
  Jauge Plus follows the account on all devices; sign out goes back to an anonymous customer.
- **Errors**: every RevenueCat error code is mapped (`revenueCatFailure`, then `JaugeError`) to a friendly message:
  cancelled (silent), payment pending, no connection, already subscribed (offers to restore), subscription linked to
  another account, purchases not allowed on this device, payment refused, purchases unavailable.

## 6. Legal texts of the paywall (App Store 3.1.2, Google Play subscriptions policy)

The paywall shows, next to the button: the name and length of the subscription (monthly), the price per month after
the trial, the trial length, when the payment is taken, the automatic renewal, how and when to cancel (24 h before
the end of the period, in the store settings), the reminder before the end of the trial, and links to the terms of use
and the privacy policy. The App Store also needs these two links in the app description (or the standard Apple EULA).

## 7. Testing

- Debug build with the Test Store key: the purchase sheet of RevenueCat Test Store opens, choose "successful
  purchase" or a failure to see each error message. Nothing is charged.
- Google Play: add testers in License testing, publish to an internal testing track.
- App Store: sandbox tester in App Store Connect, or a StoreKit configuration file in Xcode.
