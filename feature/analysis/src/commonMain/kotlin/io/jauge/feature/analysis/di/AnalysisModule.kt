package io.jauge.feature.analysis.di

import io.jauge.feature.analysis.history.HistoryViewModel
import io.jauge.feature.analysis.home.HomeViewModel
import io.jauge.feature.analysis.manualentry.ManualEntryViewModel
import io.jauge.feature.analysis.pending.PendingAnalysisStore
import io.jauge.feature.analysis.reading.ReadingViewModel
import io.jauge.feature.analysis.unreadableoffer.UnreadableOfferViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Koin module of the analysis feature (mockups 02, 03 and 10), listed in `composeApp` (`JaugeKoin.kt`). */
val analysisModule = module {
    single { PendingAnalysisStore() }
    viewModelOf(::HomeViewModel)
    viewModelOf(::ManualEntryViewModel)
    viewModelOf(::HistoryViewModel)
    viewModel { parameters -> ReadingViewModel(requestId = parameters.get(), pendingAnalyses = get(), analyzeOffer = get()) }
    viewModel { parameters ->
        UnreadableOfferViewModel(requestId = parameters[0], recognizedCompany = parameters.values.getOrNull(1) as? String, pendingAnalyses = get())
    }
}
