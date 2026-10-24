package io.jauge.core.domain.error

/**
 * Every failure the user can meet, already translated from technical causes (HTTP codes, exceptions) by the data
 * layer. The user interface turns each case into a friendly message (`core:ui`), never into a technical one.
 */
sealed class JaugeError {
    /** The phone is offline. */
    data object NoConnection : JaugeError()

    /** The server took too long to answer. */
    data object Timeout : JaugeError()

    /** The server is down, overloaded or in maintenance. */
    data object ServerUnavailable : JaugeError()

    /** Too many requests in a short time. */
    data object TooManyRequests : JaugeError()

    /** The requested item no longer exists (deleted analysis, expired share). */
    data object NotFound : JaugeError()

    /** The session has expired: the user must sign in again. */
    data object SessionExpired : JaugeError()

    /** The free analysis of the week is used: the paywall should be shown. */
    data object QuotaReached : JaugeError()

    /**
     * The screenshot or photo could not be read (mockup 10). The attempt does not count in the free quota.
     *
     * @property recognizedCompany company name that could still be read, if any.
     */
    data class UnreadableOffer(val recognizedCompany: String? = null) : JaugeError()

    /** The pasted text is not a link. */
    data object InvalidLink : JaugeError()

    /** The link points to a site or page that is not a job offer Jauge can read. */
    data object UnsupportedLink : JaugeError()

    /** The picture is too large to be sent. */
    data object ImageTooLarge : JaugeError()

    /** The phone has no free space left to save a file (share card, screenshot). */
    data object StorageFull : JaugeError()

    /** The user cancelled the purchase: not really an error, usually shown silently. */
    data object PurchaseCancelled : JaugeError()

    /** Purchases are not available (not configured, store unreachable, restricted device). */
    data object PurchaseUnavailable : JaugeError()

    /** The payment is waiting for an approval (parental control, bank). */
    data object PurchasePending : JaugeError()

    /** Data received or entered is not valid. The screen shows an empty field rather than a wrong value. */
    data object InvalidData : JaugeError()

    /** Anything else. */
    data object Unknown : JaugeError()
}
