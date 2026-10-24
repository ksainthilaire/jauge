package io.jauge.feature.share.di

import io.jauge.feature.share.share.ShareViewModel
import io.jauge.feature.share.shares.SharesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Koin module of the share feature: view models of the share card and of the tab "Partages". */
val shareModule = module {
    viewModel { parameters ->
        ShareViewModel(analysisId = parameters.get(), getAnalysis = get(), getTwinOffers = get(), createShare = get())
    }
    viewModelOf(::SharesViewModel)
}
