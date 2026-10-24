package io.jauge.app.session

import io.jauge.core.authentication.AuthenticationLinks
import io.jauge.core.billing.BillingClient
import io.jauge.core.domain.model.AuthenticationState
import io.jauge.core.domain.repository.AuthenticationRepository
import io.jauge.core.domain.repository.NotificationRepository
import io.jauge.core.domain.usecase.CompleteSignInUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the services of the app in line with the signed in account, for the whole life of the app:
 * - RevenueCat customer = Supabase user id, so Jauge Plus follows the account on every device;
 * - push token of the device saved for the signed in user;
 * - sign in deep links (`io.jauge.app://login-callback`) handed to Supabase Auth.
 *
 * @param authentication authentication repository.
 * @param billing store client.
 * @param notifications notification repository.
 * @param completeSignIn finishes a sign in from a deep link.
 */
class SessionSynchronizer(
    private val authentication: AuthenticationRepository,
    private val billing: BillingClient,
    private val notifications: NotificationRepository,
    private val completeSignIn: CompleteSignInUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Starts following the authentication state. Called once when Koin starts. */
    fun start() {
        scope.launch {
            authentication.state
                .map { (it as? AuthenticationState.SignedIn)?.user?.id to (it is AuthenticationState.Loading) }
                .distinctUntilChanged()
                .collect { (userId, isLoading) ->
                    if (isLoading) return@collect
                    runCatching { billing.identify(userId) }
                    if (userId != null) notifications.registerDevice()
                }
        }
    }

    /**
     * Handles a sign in deep link (`io.jauge.app://login-callback...`). Other links are left to the navigation.
     *
     * @param url the link opened by the system.
     * @return true when it was a sign in link.
     */
    fun handleDeepLink(url: String): Boolean {
        if (!AuthenticationLinks.isSignInLink(url)) return false
        scope.launch { completeSignIn(url) }
        return true
    }

    /** Saves the push token again, for example after the platform gave a new token. */
    fun refreshDeviceRegistration() {
        scope.launch { notifications.registerDevice() }
    }
}
