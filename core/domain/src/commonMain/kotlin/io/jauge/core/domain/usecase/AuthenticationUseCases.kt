package io.jauge.core.domain.usecase

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AuthenticationProvider
import io.jauge.core.domain.model.AuthenticationState
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.repository.AuthenticationRepository
import io.jauge.core.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Authentication state of the app (mockups 11 and 13).
 *
 * @param repository authentication repository.
 */
class ObserveAuthenticationStateUseCase(private val repository: AuthenticationRepository) {
    /** The observed state. */
    operator fun invoke(): StateFlow<AuthenticationState> = repository.state
}

/**
 * Starts signing in with Apple or Google ("Continuer avec Apple", "Continuer avec Google").
 *
 * @param repository authentication repository.
 */
class SignInWithProviderUseCase(private val repository: AuthenticationRepository) {
    /**
     * Opens the provider page.
     *
     * @param provider chosen provider.
     */
    suspend operator fun invoke(provider: AuthenticationProvider): Outcome<Unit> = repository.signInWith(provider)
}

/**
 * Sends a sign in link by email after checking the address ("Recevoir un lien par e-mail").
 *
 * @param repository authentication repository.
 */
class SendMagicLinkUseCase(private val repository: AuthenticationRepository) {
    /**
     * Sends the link, or fails with [JaugeError.InvalidEmail] without calling the server.
     *
     * @param email address typed by the user.
     */
    suspend operator fun invoke(email: String): Outcome<Unit> {
        val trimmed = email.trim()
        return if (isValidEmail(trimmed)) repository.sendMagicLink(trimmed) else Outcome.Failure(JaugeError.InvalidEmail)
    }

    /** Email check shared with the screens (enables the button) and the tests. */
    companion object {
        private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

        /**
         * True when [email] looks like an email address (one "@", a domain with a dot, no spaces).
         *
         * @param email address to check.
         */
        fun isValidEmail(email: String): Boolean = email.length <= MAXIMUM_EMAIL_LENGTH && emailPattern.matches(email)

        private const val MAXIMUM_EMAIL_LENGTH = 254
    }
}

/**
 * Finishes a sign in from a deep link (provider callback or email link).
 *
 * @param repository authentication repository.
 */
class CompleteSignInUseCase(private val repository: AuthenticationRepository) {
    /**
     * Handles the link.
     *
     * @param url the deep link opened by the system.
     * @return true when it was a sign in link.
     */
    suspend operator fun invoke(url: String): Outcome<Boolean> = repository.completeSignIn(url)
}

/**
 * Signs out ("Se déconnecter", mockup 13). The push token of the device is removed first, so that the next user of
 * the phone does not receive the alerts of the previous one.
 *
 * @param repository authentication repository.
 * @param notificationRepository notification repository.
 */
class SignOutUseCase(
    private val repository: AuthenticationRepository,
    private val notificationRepository: NotificationRepository,
) {
    /** Signs out on this device. */
    suspend operator fun invoke(): Outcome<Unit> {
        notificationRepository.unregisterDevice()
        return repository.signOut()
    }
}
