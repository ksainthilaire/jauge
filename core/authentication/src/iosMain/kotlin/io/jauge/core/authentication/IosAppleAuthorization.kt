package io.jauge.core.authentication

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.service.AppleAuthorization
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationErrorCanceled
import platform.AuthenticationServices.ASAuthorizationErrorDomain
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * [AppleAuthorization] of iOS: shows the native Sign in with Apple sheet (no scope asked) and returns the
 * authorization code of the credential. Needs the "Sign in with Apple" capability (`iosApp.entitlements`).
 */
internal class IosAppleAuthorization : AppleAuthorization {

    override suspend fun requestAuthorizationCode(): Outcome<String?> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val request = ASAuthorizationAppleIDProvider().createRequest()
            val controller = ASAuthorizationController(authorizationRequests = listOf(request))
            val delegate = AuthorizationDelegate { outcome ->
                if (continuation.isActive) continuation.resume(outcome)
            }
            controller.delegate = delegate
            controller.presentationContextProvider = delegate
            // The controller keeps its delegate weakly: keep it alive until the answer.
            continuation.invokeOnCancellation { delegate.release() }
            delegate.retain(controller)
            controller.performRequests()
        }
    }
}

/** Receives the answer of the Apple sheet and gives the window to present it on. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class AuthorizationDelegate(
    private val onResult: (Outcome<String?>) -> Unit,
) : NSObject(), ASAuthorizationControllerDelegateProtocol, ASAuthorizationControllerPresentationContextProvidingProtocol {

    private var controller: ASAuthorizationController? = null

    /** Keeps the controller (and through it this delegate) alive while the sheet is shown. */
    fun retain(controller: ASAuthorizationController) {
        this.controller = controller
    }

    /** Releases the controller. */
    fun release() {
        controller = null
    }

    @ObjCSignatureOverride
    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization: ASAuthorization) {
        val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        val code = credential?.authorizationCode?.let { data ->
            NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
        }
        release()
        onResult(Outcome.Success(code))
    }

    @ObjCSignatureOverride
    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithError: NSError) {
        release()
        val cancelled = didCompleteWithError.domain == ASAuthorizationErrorDomain && didCompleteWithError.code == ASAuthorizationErrorCanceled
        onResult(Outcome.Failure(if (cancelled) JaugeError.SignInCancelled else JaugeError.SignInFailed))
    }

    override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): ASPresentationAnchor {
        val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
        return scenes.flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }.firstOrNull { it.isKeyWindow() }
            ?: scenes.firstOrNull()?.windows?.firstOrNull() as? UIWindow
            ?: UIWindow()
    }
}

internal actual fun platformAppleAuthorization(): AppleAuthorization = IosAppleAuthorization()
