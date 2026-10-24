package io.jauge.core.domain.usecase

import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.Share
import io.jauge.core.domain.model.ShareLinks
import io.jauge.core.domain.model.ShareOptions
import io.jauge.core.domain.model.map
import io.jauge.core.domain.repository.ShareRepository

/**
 * Shares an analysis as a link and a card (mockup 07). The link is always `https://jauge.io/a/{analysisId}`, whatever
 * the server sends back.
 *
 * @param repository shares repository.
 */
class CreateShareUseCase(private val repository: ShareRepository) {
    /**
     * Creates the share.
     *
     * @param analysisId shared analysis.
     * @param options card options.
     */
    suspend operator fun invoke(analysisId: String, options: ShareOptions = ShareOptions()): Outcome<Share> =
        repository.createShare(analysisId, options).map { it.copy(url = ShareLinks.forAnalysis(analysisId)) }
}

/**
 * Shares of the user (tab "Partages").
 *
 * @param repository shares repository.
 */
class GetSharesUseCase(private val repository: ShareRepository) {
    /** Loads the shares, newest first. */
    suspend operator fun invoke(): Outcome<List<Share>> = repository.getShares().map { shares -> shares.sortedByDescending { it.createdAt } }
}

/**
 * Deletes a share.
 *
 * @param repository shares repository.
 */
class DeleteShareUseCase(private val repository: ShareRepository) {
    /**
     * Deletes the share.
     *
     * @param shareId identifier of the share.
     */
    suspend operator fun invoke(shareId: String): Outcome<Unit> = repository.deleteShare(shareId)
}
