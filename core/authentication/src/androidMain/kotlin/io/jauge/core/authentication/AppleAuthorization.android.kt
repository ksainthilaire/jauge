package io.jauge.core.authentication

import io.jauge.core.domain.service.AppleAuthorization
import io.jauge.core.domain.service.NoAppleAuthorization

/**
 * Android has no native Sign in with Apple: a code for the app client (`io.jauge.app`, the one the server uses to
 * revoke) cannot be obtained, so the deletion is sent without it.
 */
internal actual fun platformAppleAuthorization(): AppleAuthorization = NoAppleAuthorization
