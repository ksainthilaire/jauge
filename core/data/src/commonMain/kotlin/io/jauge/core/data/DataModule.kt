package io.jauge.core.data

import io.jauge.core.data.api.SwitchingJaugeApi
import io.jauge.core.data.offline.OfflineDataset
import io.jauge.core.data.offline.OfflineJaugeApi
import io.jauge.core.data.offline.OfflineScenarioController
import io.jauge.core.data.repository.AccountRepositoryImplementation
import io.jauge.core.data.repository.AnalysisRepositoryImplementation
import io.jauge.core.data.repository.InsightRepositoryImplementation
import io.jauge.core.data.repository.ShareRepositoryImplementation
import io.jauge.core.data.repository.SubscriptionRepositoryImplementation
import io.jauge.core.data.repository.TwinRepositoryImplementation
import io.jauge.core.domain.repository.AccountRepository
import io.jauge.core.domain.repository.AnalysisRepository
import io.jauge.core.domain.repository.InsightRepository
import io.jauge.core.domain.repository.ShareRepository
import io.jauge.core.domain.repository.SubscriptionRepository
import io.jauge.core.domain.repository.TwinRepository
import io.jauge.core.network.JaugeApi
import io.jauge.core.network.REMOTE_API
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Koin qualifier of the offline [JaugeApi]. */
const val OFFLINE_API = "offlineJaugeApi"

/**
 * Koin module of the data layer: the offline API, the switching API used by the repositories, and the repositories.
 * Requires `networkModule`, `billingModule` and `commonModule`.
 */
val dataModule = module {
    single { OfflineScenarioController() }
    single { OfflineDataset() }
    single<JaugeApi>(named(OFFLINE_API)) { OfflineJaugeApi(dataset = get(), scenarios = get()) }
    single<JaugeApi> {
        SwitchingJaugeApi(remote = get(named(REMOTE_API)), offline = get(named(OFFLINE_API)), apiModeController = get())
    }
    single<AnalysisRepository> { AnalysisRepositoryImplementation(get()) }
    single<InsightRepository> { InsightRepositoryImplementation(get()) }
    single<TwinRepository> { TwinRepositoryImplementation(get()) }
    single<ShareRepository> { ShareRepositoryImplementation(get()) }
    single<AccountRepository> { AccountRepositoryImplementation(get()) }
    single<SubscriptionRepository> { SubscriptionRepositoryImplementation(get(), get()) }
}
