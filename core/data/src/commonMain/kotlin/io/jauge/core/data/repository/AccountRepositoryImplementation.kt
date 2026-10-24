package io.jauge.core.data.repository

import io.jauge.core.data.error.outcomeOf
import io.jauge.core.data.mapper.toDomain
import io.jauge.core.domain.model.Account
import io.jauge.core.domain.model.Consents
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.repository.AccountRepository
import io.jauge.core.network.JaugeApi
import io.jauge.core.network.payload.ConsentsPayload

/**
 * [AccountRepository] backed by the Jauge API.
 *
 * @param api the API client.
 */
class AccountRepositoryImplementation(private val api: JaugeApi) : AccountRepository {

    override suspend fun getAccount(): Outcome<Account> = outcomeOf { api.getAccount().toDomain() }

    override suspend fun updateConsents(consents: Consents): Outcome<Account> =
        outcomeOf { api.updateConsents(ConsentsPayload(consents.marketingEmails)).toDomain() }

    override suspend fun deleteAccount(): Outcome<Unit> = outcomeOf { api.deleteAccount() }
}
