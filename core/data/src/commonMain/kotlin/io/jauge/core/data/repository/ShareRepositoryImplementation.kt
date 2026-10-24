package io.jauge.core.data.repository

import io.jauge.core.data.error.outcomeOf
import io.jauge.core.data.mapper.toApiValue
import io.jauge.core.data.mapper.toDomainOrNull
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.Share
import io.jauge.core.domain.model.ShareOptions
import io.jauge.core.domain.repository.ShareRepository
import io.jauge.core.network.JaugeApi
import io.jauge.core.network.payload.CreateShareRequestPayload

/**
 * [ShareRepository] backed by the Jauge API.
 *
 * @param api the API client.
 */
class ShareRepositoryImplementation(private val api: JaugeApi) : ShareRepository {

    override suspend fun createShare(analysisId: String, options: ShareOptions): Outcome<Share> = outcomeOf {
        val payload = api.createShare(
            CreateShareRequestPayload(
                analysisId = analysisId,
                format = options.format.toApiValue(),
                hideCompany = options.hideCompany,
                showCalmTwin = options.showCalmTwin,
            ),
        )
        requireNotNull(payload.toDomainOrNull()) { "Invalid share" }
    }

    override suspend fun getShares(): Outcome<List<Share>> = outcomeOf { api.getShares().items.mapNotNull { it.toDomainOrNull() } }

    override suspend fun deleteShare(shareId: String): Outcome<Unit> = outcomeOf { api.deleteShare(shareId) }
}
