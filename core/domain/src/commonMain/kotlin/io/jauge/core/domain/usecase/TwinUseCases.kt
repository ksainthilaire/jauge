package io.jauge.core.domain.usecase

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.CalmAlert
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.TwinOffers
import io.jauge.core.domain.model.TwinSort
import io.jauge.core.domain.model.map
import io.jauge.core.domain.repository.TwinRepository
import kotlinx.coroutines.flow.Flow

/**
 * Twin offers of an analysis (mockup 06).
 *
 * @param repository twins repository.
 */
class GetTwinOffersUseCase(private val repository: TwinRepository) {
    /**
     * Loads the twin offers and guarantees the requested order.
     *
     * @param analysisId identifier of the analysis.
     * @param sort requested order.
     */
    suspend operator fun invoke(analysisId: String, sort: TwinSort = TwinSort.LEAST_DISPUTED): Outcome<TwinOffers> =
        repository.getTwinOffers(analysisId, sort).map { twins ->
            val sorted = when (sort) {
                TwinSort.LEAST_DISPUTED -> twins.offers.sortedBy { it.score }
                TwinSort.BEST_PAID -> twins.offers.sortedByDescending { it.salary?.middleThousands ?: 0.0 }
                TwinSort.MOST_RECENT -> twins.offers.sortedByDescending { it.publishedOn }
            }
            twins.copy(offers = sorted)
        }
}

/**
 * Calm alerts of the user.
 *
 * @param repository twins repository.
 */
class GetCalmAlertsUseCase(private val repository: TwinRepository) {
    /** Observed alerts. */
    operator fun invoke(): Flow<List<CalmAlert>> = repository.observeAlerts()

    /** Reloads the alerts (pull to refresh). */
    suspend fun refresh(): Outcome<Unit> = repository.refreshAlerts()
}

/**
 * Turns on a calm alert (mockup 06, "Préviens-moi dès qu'une offre jumelle passe sous 30").
 *
 * @param repository twins repository.
 */
class CreateCalmAlertUseCase(private val repository: TwinRepository) {
    /**
     * Creates the alert.
     *
     * @param analysisId analysis whose twins are watched.
     * @param threshold score between 1 and 100 under which the user is notified.
     */
    suspend operator fun invoke(analysisId: String, threshold: Int = CalmAlert.DEFAULT_THRESHOLD): Outcome<CalmAlert> =
        if (threshold !in 1..100 || analysisId.isBlank()) {
            Outcome.Failure(JaugeError.InvalidData)
        } else {
            repository.createAlert(analysisId, threshold)
        }
}

/**
 * Turns off a calm alert.
 *
 * @param repository twins repository.
 */
class DeleteCalmAlertUseCase(private val repository: TwinRepository) {
    /**
     * Deletes the alert.
     *
     * @param alertId identifier of the alert.
     */
    suspend operator fun invoke(alertId: String): Outcome<Unit> = repository.deleteAlert(alertId)
}
