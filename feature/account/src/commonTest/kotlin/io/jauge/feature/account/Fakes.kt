package io.jauge.feature.account

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Account
import io.jauge.core.domain.model.Analysis
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.AuthenticatedUser
import io.jauge.core.domain.model.AuthenticationProvider
import io.jauge.core.domain.model.AuthenticationState
import io.jauge.core.domain.model.Consents
import io.jauge.core.domain.model.JaugeNotification
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.PushPermissionStatus
import io.jauge.core.domain.model.SignInMethod
import io.jauge.core.domain.model.Subscription
import io.jauge.core.domain.model.SubscriptionOffer
import io.jauge.core.domain.model.SubscriptionStatus
import io.jauge.core.domain.model.UserPreferences
import io.jauge.core.domain.repository.AccountRepository
import io.jauge.core.domain.repository.AnalysisRepository
import io.jauge.core.domain.repository.AuthenticationRepository
import io.jauge.core.domain.repository.NotificationRepository
import io.jauge.core.domain.repository.PreferencesRepository
import io.jauge.core.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.CompletableDeferred

/** Preferences kept in memory. */
class FakePreferencesRepository : PreferencesRepository {
    /** Current value. */
    val preferences = MutableStateFlow(UserPreferences())

    override fun observePreferences(): Flow<UserPreferences> = preferences

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        preferences.value = transform(preferences.value)
    }
}

/** Authentication with a programmable answer. */
class FakeAuthenticationRepository : AuthenticationRepository {
    private val mutableState = MutableStateFlow<AuthenticationState>(AuthenticationState.SignedOut)

    override val state: StateFlow<AuthenticationState> = mutableState

    /** Error returned by the next sign in, null for a success. */
    var nextError: JaugeError? = null

    /** True to finish provider sign ins at once, false to wait for [completeFromOutside]. */
    var finishesAtOnce = true

    /** Last address a link was sent to. */
    var linkSentTo: String? = null

    override suspend fun signInWith(provider: AuthenticationProvider): Outcome<Unit> {
        nextError?.let { return Outcome.Failure(it) }
        if (finishesAtOnce) completeFromOutside(SignInMethod.valueOf(provider.name))
        return Outcome.Success(Unit)
    }

    override suspend fun sendMagicLink(email: String): Outcome<Unit> {
        linkSentTo = email
        return Outcome.Success(Unit)
    }

    override suspend fun completeSignIn(url: String): Outcome<Boolean> {
        completeFromOutside(SignInMethod.EMAIL)
        return Outcome.Success(true)
    }

    override suspend fun signOut(): Outcome<Unit> {
        mutableState.value = AuthenticationState.SignedOut
        return Outcome.Success(Unit)
    }

    /**
     * Simulates the user coming back signed in.
     *
     * @param method how the user signed in.
     */
    fun completeFromOutside(method: SignInMethod) {
        mutableState.value = AuthenticationState.SignedIn(AuthenticatedUser("user-1", "ines@exemple.fr", "Inès Bertrand", method))
    }
}

/** Account with a programmable answer. */
class FakeAccountRepository : AccountRepository {
    /** Consents saved. */
    var savedConsents: Consents? = null

    /** Error returned by every call, null for a success. */
    var error: JaugeError? = null

    /** True once the account was deleted. */
    var deleted = false

    /** The account returned. */
    val account = Account("user-1", "Inès Bertrand", "ines@example.com", SignInMethod.APPLE, 12, Consents(false))

    override suspend fun getAccount(): Outcome<Account> = error?.let { Outcome.Failure(it) } ?: Outcome.Success(account)

    override suspend fun updateConsents(consents: Consents): Outcome<Account> {
        savedConsents = consents
        return Outcome.Success(account.copy(consents = consents))
    }

    override suspend fun deleteAccount(): Outcome<Unit> {
        error?.let { return Outcome.Failure(it) }
        deleted = true
        return Outcome.Success(Unit)
    }
}

/** Subscription with a programmable answer. */
class FakeSubscriptionRepository : SubscriptionRepository {
    /** Subscription returned. */
    var subscription = Subscription(SubscriptionStatus.FREE, null, null, 1)

    /** Error returned by restore, null for a success. */
    var restoreError: JaugeError? = null

    override suspend fun getSubscription(): Outcome<Subscription> = Outcome.Success(subscription)

    override suspend fun getOffer(): Outcome<SubscriptionOffer> = Outcome.Success(SubscriptionOffer(SubscriptionOffer.MONTHLY_PRODUCT_ID, "6,99 €", 3))

    override suspend fun purchase(offer: SubscriptionOffer): Outcome<Subscription> = Outcome.Success(subscription)

    /** When set, restore waits for it, to observe the loading state. */
    var restoreGate: CompletableDeferred<Unit>? = null

    override suspend fun restorePurchases(): Outcome<Subscription> {
        restoreGate?.await()
        return restoreError?.let { Outcome.Failure(it) } ?: Outcome.Success(subscription)
    }

    override suspend fun getManagementUrl(): Outcome<String> = Outcome.Success(MANAGEMENT_URL)

    /** Constants. */
    companion object {
        /** Store page returned by [getManagementUrl]. */
        const val MANAGEMENT_URL = "https://apps.apple.com/account/subscriptions"
    }
}

/** Notifications that do nothing. */
class FakeNotificationRepository : NotificationRepository {
    /** Number of unregistrations. */
    var unregistrations = 0

    override fun observeIncoming(): Flow<JaugeNotification> = emptyFlow()

    override suspend fun getNotifications(): Outcome<List<JaugeNotification>> = Outcome.Success(emptyList())

    override suspend fun markAsRead(notificationId: String): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun getPermissionStatus(): PushPermissionStatus = PushPermissionStatus.GRANTED

    override suspend fun requestPermission(): Outcome<PushPermissionStatus> = Outcome.Success(PushPermissionStatus.GRANTED)

    override suspend fun registerDevice(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun unregisterDevice(): Outcome<Unit> {
        unregistrations++
        return Outcome.Success(Unit)
    }
}

/** Analyses with a fixed history. */
class FakeAnalysisRepository : AnalysisRepository {
    /** Stored history. */
    val history = MutableStateFlow<List<AnalysisSummary>>(emptyList())

    override fun analyze(source: OfferSource): Flow<AnalysisProgress> = emptyFlow()

    override fun observeHistory(): Flow<List<AnalysisSummary>> = history

    override suspend fun refreshHistory(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun getAnalysis(analysisId: String): Outcome<Analysis> = Outcome.Failure(JaugeError.NotFound)

    override suspend fun deleteAnalysis(analysisId: String): Outcome<Unit> = Outcome.Success(Unit)
}
