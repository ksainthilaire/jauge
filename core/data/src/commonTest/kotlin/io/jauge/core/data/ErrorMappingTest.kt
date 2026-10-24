package io.jauge.core.data

import io.jauge.core.billing.BillingFailure
import io.jauge.core.common.error.StorageFullException
import io.jauge.core.data.error.outcomeOf
import io.jauge.core.data.error.toJaugeError
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Outcome
import io.jauge.core.network.NetworkFailure
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorMappingTest {

    @Test
    fun networkFailuresBecomeFriendlyErrors() {
        assertEquals(JaugeError.NoConnection, NetworkFailure.NoConnection().toJaugeError())
        assertEquals(JaugeError.Timeout, NetworkFailure.Timeout().toJaugeError())
        assertEquals(JaugeError.InvalidData, NetworkFailure.InvalidResponse().toJaugeError())
        assertEquals(JaugeError.Unknown, NetworkFailure.Unexpected().toJaugeError())
    }

    @Test
    fun apiErrorCodesWinOverStatusCodes() {
        assertEquals(JaugeError.QuotaReached, NetworkFailure.Http(403, "quota_reached").toJaugeError())
        assertEquals(JaugeError.UnreadableOffer(), NetworkFailure.Http(422, "unreadable_offer").toJaugeError())
        assertEquals(JaugeError.UnsupportedLink, NetworkFailure.Http(422, "unsupported_link").toJaugeError())
        assertEquals(JaugeError.ServerUnavailable, NetworkFailure.Http(503, "maintenance").toJaugeError())
    }

    @Test
    fun statusCodesWithoutCode() {
        val expected = mapOf(
            401 to JaugeError.SessionExpired,
            402 to JaugeError.QuotaReached,
            404 to JaugeError.NotFound,
            413 to JaugeError.ImageTooLarge,
            422 to JaugeError.InvalidData,
            429 to JaugeError.TooManyRequests,
            500 to JaugeError.ServerUnavailable,
            503 to JaugeError.ServerUnavailable,
            418 to JaugeError.Unknown,
        )
        expected.forEach { (status, error) -> assertEquals(error, NetworkFailure.Http(status, null).toJaugeError(), "HTTP $status") }
    }

    @Test
    fun storeStorageAndUnknownFailures() {
        assertEquals(JaugeError.PurchaseCancelled, BillingFailure.Cancelled().toJaugeError())
        assertEquals(JaugeError.PurchaseUnavailable, BillingFailure.Unavailable().toJaugeError())
        assertEquals(JaugeError.PurchasePending, BillingFailure.Pending().toJaugeError())
        assertEquals(JaugeError.StorageFull, StorageFullException().toJaugeError())
        assertEquals(JaugeError.InvalidData, IllegalArgumentException("bad").toJaugeError())
        assertEquals(JaugeError.Unknown, IllegalStateException("boom").toJaugeError())
    }

    @Test
    fun outcomeOfNeverThrows() = runTest {
        assertEquals(Outcome.Success(4), outcomeOf { 2 + 2 })
        assertEquals(Outcome.Failure(JaugeError.Timeout), outcomeOf<Int> { throw NetworkFailure.Timeout() })
    }
}
