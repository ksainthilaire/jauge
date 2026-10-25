package io.jauge.core.data.repository

import io.jauge.core.data.error.outcomeOf
import io.jauge.core.data.mapper.toApiValue
import io.jauge.core.data.mapper.toDomain
import io.jauge.core.domain.model.CalmAlert
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.TwinOffers
import io.jauge.core.domain.model.TwinSort
import io.jauge.core.domain.model.map
import io.jauge.core.domain.repository.TwinRepository
import io.jauge.core.data.preferences.SearchAreaSource
import io.jauge.core.data.preferences.toPayload
import io.jauge.core.network.JaugeApi
import io.jauge.core.network.payload.CreateAlertRequestPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * [TwinRepository] backed by the Jauge API, with the alerts cached in memory. Realtime updates of the calm alerts
 * (Supabase) will push into the same cache.
 *
 * @param api the API client.
 * @param searchArea search area of the user, sent with the twin offers.
 */
class TwinRepositoryImplementation(
    private val api: JaugeApi,
    private val searchArea: SearchAreaSource = SearchAreaSource.NONE,
) : TwinRepository {

    private val alerts = MutableStateFlow<List<CalmAlert>>(emptyList())

    override suspend fun getTwinOffers(analysisId: String, sort: TwinSort): Outcome<TwinOffers> =
        outcomeOf { api.getTwins(analysisId, sort.toApiValue(), searchArea.current()?.toPayload()).toDomain() }

    override fun observeAlerts(): Flow<List<CalmAlert>> = alerts.asStateFlow()

    override suspend fun refreshAlerts(): Outcome<Unit> = outcomeOf {
        alerts.value = api.getAlerts().items.map { it.toDomain() }
    }

    override suspend fun createAlert(analysisId: String, threshold: Int): Outcome<CalmAlert> =
        outcomeOf { api.createAlert(CreateAlertRequestPayload(analysisId, threshold)).toDomain() }
            .map { alert -> alert.also { created -> alerts.update { list -> listOf(created) + list.filterNot { it.id == created.id } } } }

    override suspend fun deleteAlert(alertId: String): Outcome<Unit> =
        outcomeOf { api.deleteAlert(alertId) }.map { alerts.update { list -> list.filterNot { it.id == alertId } } }
}
