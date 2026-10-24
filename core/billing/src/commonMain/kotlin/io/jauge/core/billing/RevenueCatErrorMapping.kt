package io.jauge.core.billing

import com.revenuecat.purchases.kmp.models.PurchasesErrorCode

/**
 * Translates a RevenueCat error into a [BillingFailure]. Every code ends in one of the friendly cases of the app;
 * the technical description never reaches the screen.
 *
 * @param code RevenueCat error code.
 * @param userCancelled true when the user closed the purchase sheet.
 */
fun revenueCatFailure(code: PurchasesErrorCode, userCancelled: Boolean = false): BillingFailure = when {
    userCancelled || code == PurchasesErrorCode.PurchaseCancelledError -> BillingFailure.Cancelled()
    else -> when (code) {
        PurchasesErrorCode.PaymentPendingError -> BillingFailure.Pending()

        PurchasesErrorCode.NetworkError,
        PurchasesErrorCode.OfflineConnectionError,
        PurchasesErrorCode.ProductRequestTimedOut,
        PurchasesErrorCode.ApiEndpointBlocked,
        -> BillingFailure.Network()

        PurchasesErrorCode.ProductAlreadyPurchasedError -> BillingFailure.AlreadyOwned()

        PurchasesErrorCode.ReceiptAlreadyInUseError,
        PurchasesErrorCode.ReceiptInUseByOtherSubscriberError,
        PurchasesErrorCode.PurchaseBelongsToOtherUser,
        -> BillingFailure.OwnedByAnotherAccount()

        PurchasesErrorCode.PurchaseNotAllowedError,
        PurchasesErrorCode.InsufficientPermissionsError,
        PurchasesErrorCode.IneligibleError,
        -> BillingFailure.NotAllowed()

        PurchasesErrorCode.ConfigurationError,
        PurchasesErrorCode.InvalidCredentialsError,
        PurchasesErrorCode.ProductNotAvailableForPurchaseError,
        PurchasesErrorCode.InvalidAppleSubscriptionKeyError,
        PurchasesErrorCode.UnsupportedError,
        PurchasesErrorCode.FeatureNotAvailableInCustomEntitlementsComputationMode,
        -> BillingFailure.Unavailable()

        else -> BillingFailure.Store()
    }
}
