package io.jauge.app

import io.jauge.app.session.SessionSynchronizer
import io.jauge.core.notifications.ApnsTokenStore
import org.koin.mp.KoinPlatform

/**
 * Called by the Swift app delegate with the APNs device token, written as a hexadecimal string. The token is saved
 * for the signed in user (Supabase `devices` table).
 *
 * @param hexadecimalToken the device token.
 */
fun onApnsTokenReceived(hexadecimalToken: String) {
    ApnsTokenStore.update(hexadecimalToken)
    KoinPlatform.getKoinOrNull()?.getOrNull<SessionSynchronizer>()?.refreshDeviceRegistration()
}

/**
 * Called by SwiftUI `onOpenURL` for the links opened in the app: handles the sign in link of Supabase
 * (`io.jauge.app://login-callback`).
 *
 * @param url the full link.
 * @return true when it was a sign in link; other links go to `openIncomingLink`.
 */
fun handleDeepLink(url: String): Boolean =
    KoinPlatform.getKoinOrNull()?.getOrNull<SessionSynchronizer>()?.handleDeepLink(url) ?: false
