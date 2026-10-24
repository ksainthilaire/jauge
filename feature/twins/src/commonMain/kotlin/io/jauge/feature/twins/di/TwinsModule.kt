package io.jauge.feature.twins.di

import io.jauge.feature.twins.twinoffers.TwinOffersViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module of the twins feature: view model of the twin offers and of the calm alert. */
val twinsModule = module {
    viewModel { parameters ->
        TwinOffersViewModel(
            initialAnalysisId = parameters.getOrNull(),
            getTwinOffers = get(),
            getAnalysisHistory = get(),
            getCalmAlerts = get(),
            createCalmAlert = get(),
            deleteCalmAlert = get(),
        )
    }
}
