package io.jauge.core.ui.error

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.jauge.core.designsystem.component.FriendlyErrorState
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.ui.resources.Res
import io.jauge.core.ui.resources.error_action_retry
import io.jauge.core.ui.resources.error_action_see_plus
import io.jauge.core.ui.resources.error_action_sign_in
import io.jauge.core.ui.resources.error_action_try_screenshot
import io.jauge.core.ui.resources.error_action_take_photo_again
import io.jauge.core.ui.resources.error_image_too_large_message
import io.jauge.core.ui.resources.error_image_too_large_title
import io.jauge.core.ui.resources.error_invalid_data_message
import io.jauge.core.ui.resources.error_invalid_data_title
import io.jauge.core.ui.resources.error_invalid_link_message
import io.jauge.core.ui.resources.error_invalid_link_title
import io.jauge.core.ui.resources.error_no_connection_message
import io.jauge.core.ui.resources.error_no_connection_title
import io.jauge.core.ui.resources.error_not_found_message
import io.jauge.core.ui.resources.error_not_found_title
import io.jauge.core.ui.resources.error_purchase_cancelled_message
import io.jauge.core.ui.resources.error_purchase_cancelled_title
import io.jauge.core.ui.resources.error_purchase_pending_message
import io.jauge.core.ui.resources.error_purchase_pending_title
import io.jauge.core.ui.resources.error_purchase_unavailable_message
import io.jauge.core.ui.resources.error_purchase_unavailable_title
import io.jauge.core.ui.resources.error_quota_reached_message
import io.jauge.core.ui.resources.error_quota_reached_title
import io.jauge.core.ui.resources.error_server_unavailable_message
import io.jauge.core.ui.resources.error_server_unavailable_title
import io.jauge.core.ui.resources.error_session_expired_message
import io.jauge.core.ui.resources.error_session_expired_title
import io.jauge.core.ui.resources.error_storage_full_message
import io.jauge.core.ui.resources.error_storage_full_title
import io.jauge.core.ui.resources.error_timeout_message
import io.jauge.core.ui.resources.error_timeout_title
import io.jauge.core.ui.resources.error_too_many_requests_message
import io.jauge.core.ui.resources.error_too_many_requests_title
import io.jauge.core.ui.resources.error_unknown_message
import io.jauge.core.ui.resources.error_unknown_title
import io.jauge.core.ui.resources.error_unreadable_offer_message
import io.jauge.core.ui.resources.error_unreadable_offer_title
import io.jauge.core.ui.resources.error_unsupported_link_message
import io.jauge.core.ui.resources.error_unsupported_link_title
import io.jauge.core.ui.resources.error_purchase_already_owned_title
import io.jauge.core.ui.resources.error_purchase_already_owned_message
import io.jauge.core.ui.resources.error_purchase_owned_by_another_account_title
import io.jauge.core.ui.resources.error_purchase_owned_by_another_account_message
import io.jauge.core.ui.resources.error_purchase_not_allowed_title
import io.jauge.core.ui.resources.error_purchase_not_allowed_message
import io.jauge.core.ui.resources.error_purchase_failed_title
import io.jauge.core.ui.resources.error_purchase_failed_message
import io.jauge.core.ui.resources.error_nothing_to_restore_title
import io.jauge.core.ui.resources.error_nothing_to_restore_message
import io.jauge.core.ui.resources.error_invalid_email_title
import io.jauge.core.ui.resources.error_invalid_email_message
import io.jauge.core.ui.resources.error_sign_in_cancelled_title
import io.jauge.core.ui.resources.error_sign_in_cancelled_message
import io.jauge.core.ui.resources.error_sign_in_link_expired_title
import io.jauge.core.ui.resources.error_sign_in_link_expired_message
import io.jauge.core.ui.resources.error_sign_in_failed_title
import io.jauge.core.ui.resources.error_sign_in_failed_message
import io.jauge.core.ui.resources.error_action_restore
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What the screen should do when the user taps the suggested action of an error. */
enum class FriendlyAction {
    /** Try the same operation again. */
    RETRY,

    /** Open the paywall. */
    OPEN_PAYWALL,

    /** Open the sign in screen. */
    SIGN_IN,

    /** Take the photo again or pick another screenshot. */
    RETAKE_PHOTO,

    /** Try with a screenshot instead of a link. */
    USE_SCREENSHOT,

    /** Restore the store purchases ("Restaurer les achats"). */
    RESTORE_PURCHASES,

    /** Nothing to do: the message is informative. */
    NONE,
}

/**
 * Friendly version of a [JaugeError]: a human title, an explanation and the suggested action, all from
 * `strings.xml`.
 *
 * @property title short human title.
 * @property message what happened and what to do.
 * @property actionLabel label of the suggested action, null when there is none.
 * @property action the suggested action.
 */
data class FriendlyMessage(
    val title: StringResource,
    val message: StringResource,
    val actionLabel: StringResource?,
    val action: FriendlyAction,
)

/** Maps every domain error to its friendly message. This is the only place where errors become texts. */
fun JaugeError.toFriendlyMessage(): FriendlyMessage = when (this) {
    JaugeError.NoConnection -> retry(Res.string.error_no_connection_title, Res.string.error_no_connection_message)
    JaugeError.Timeout -> retry(Res.string.error_timeout_title, Res.string.error_timeout_message)
    JaugeError.ServerUnavailable -> retry(Res.string.error_server_unavailable_title, Res.string.error_server_unavailable_message)
    JaugeError.TooManyRequests -> retry(Res.string.error_too_many_requests_title, Res.string.error_too_many_requests_message)
    JaugeError.NotFound -> informative(Res.string.error_not_found_title, Res.string.error_not_found_message)
    JaugeError.SessionExpired -> FriendlyMessage(
        Res.string.error_session_expired_title,
        Res.string.error_session_expired_message,
        Res.string.error_action_sign_in,
        FriendlyAction.SIGN_IN,
    )
    JaugeError.QuotaReached -> FriendlyMessage(
        Res.string.error_quota_reached_title,
        Res.string.error_quota_reached_message,
        Res.string.error_action_see_plus,
        FriendlyAction.OPEN_PAYWALL,
    )
    is JaugeError.UnreadableOffer -> FriendlyMessage(
        Res.string.error_unreadable_offer_title,
        Res.string.error_unreadable_offer_message,
        Res.string.error_action_take_photo_again,
        FriendlyAction.RETAKE_PHOTO,
    )
    JaugeError.InvalidLink -> informative(Res.string.error_invalid_link_title, Res.string.error_invalid_link_message)
    JaugeError.UnsupportedLink -> FriendlyMessage(
        Res.string.error_unsupported_link_title,
        Res.string.error_unsupported_link_message,
        Res.string.error_action_try_screenshot,
        FriendlyAction.USE_SCREENSHOT,
    )
    JaugeError.ImageTooLarge -> FriendlyMessage(
        Res.string.error_image_too_large_title,
        Res.string.error_image_too_large_message,
        Res.string.error_action_take_photo_again,
        FriendlyAction.RETAKE_PHOTO,
    )
    JaugeError.StorageFull -> retry(Res.string.error_storage_full_title, Res.string.error_storage_full_message)
    JaugeError.PurchaseCancelled -> informative(Res.string.error_purchase_cancelled_title, Res.string.error_purchase_cancelled_message)
    JaugeError.PurchaseUnavailable -> informative(Res.string.error_purchase_unavailable_title, Res.string.error_purchase_unavailable_message)
    JaugeError.PurchasePending -> informative(Res.string.error_purchase_pending_title, Res.string.error_purchase_pending_message)
    JaugeError.PurchaseAlreadyOwned -> FriendlyMessage(
        Res.string.error_purchase_already_owned_title,
        Res.string.error_purchase_already_owned_message,
        Res.string.error_action_restore,
        FriendlyAction.RESTORE_PURCHASES,
    )
    JaugeError.PurchaseOwnedByAnotherAccount -> FriendlyMessage(
        Res.string.error_purchase_owned_by_another_account_title,
        Res.string.error_purchase_owned_by_another_account_message,
        Res.string.error_action_sign_in,
        FriendlyAction.SIGN_IN,
    )
    JaugeError.PurchaseNotAllowed -> informative(Res.string.error_purchase_not_allowed_title, Res.string.error_purchase_not_allowed_message)
    JaugeError.PurchaseFailed -> retry(Res.string.error_purchase_failed_title, Res.string.error_purchase_failed_message)
    JaugeError.NothingToRestore -> informative(Res.string.error_nothing_to_restore_title, Res.string.error_nothing_to_restore_message)
    JaugeError.InvalidEmail -> informative(Res.string.error_invalid_email_title, Res.string.error_invalid_email_message)
    JaugeError.SignInCancelled -> informative(Res.string.error_sign_in_cancelled_title, Res.string.error_sign_in_cancelled_message)
    JaugeError.SignInLinkExpired -> informative(Res.string.error_sign_in_link_expired_title, Res.string.error_sign_in_link_expired_message)
    JaugeError.SignInFailed -> retry(Res.string.error_sign_in_failed_title, Res.string.error_sign_in_failed_message)
    JaugeError.InvalidData -> retry(Res.string.error_invalid_data_title, Res.string.error_invalid_data_message)
    JaugeError.Unknown -> retry(Res.string.error_unknown_title, Res.string.error_unknown_message)
}

/** True when the error should be shown silently (no error screen), for example a cancelled purchase or sign in. */
val JaugeError.isSilent: Boolean
    get() = this == JaugeError.PurchaseCancelled || this == JaugeError.SignInCancelled

private fun retry(title: StringResource, message: StringResource) =
    FriendlyMessage(title, message, Res.string.error_action_retry, FriendlyAction.RETRY)

private fun informative(title: StringResource, message: StringResource) =
    FriendlyMessage(title, message, null, FriendlyAction.NONE)

/**
 * Full screen friendly error for a [JaugeError] (design system [FriendlyErrorState] with the mapped texts).
 *
 * @param error the error to show.
 * @param onAction called with the suggested action when the user taps the button.
 * @param modifier modifier of the layout.
 */
@Composable
fun FriendlyErrorView(
    error: JaugeError,
    onAction: (FriendlyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = error.toFriendlyMessage()
    FriendlyErrorState(
        title = stringResource(message.title),
        message = stringResource(message.message),
        modifier = modifier,
        primaryActionLabel = message.actionLabel?.let { stringResource(it) },
        onPrimaryAction = { onAction(message.action) },
    )
}
