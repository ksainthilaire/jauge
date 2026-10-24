package io.jauge.feature.verdict.di

import io.jauge.feature.verdict.details.DetailsViewModel
import io.jauge.feature.verdict.methodology.MethodologyViewModel
import io.jauge.feature.verdict.verdict.VerdictViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module of the verdict feature: view models of the verdict, the details and the methodology. */
val verdictModule = module {
    viewModel { parameters -> VerdictViewModel(analysisId = parameters.get(), getAnalysis = get(), favoriteAnalysis = get()) }
    viewModel { parameters ->
        DetailsViewModel(
            analysisId = parameters.get(),
            getSalary = get(),
            getSkills = get(),
            getTrend = get(),
            getAnalysis = get(),
        )
    }
    viewModel { parameters ->
        MethodologyViewModel(analysisId = parameters.get(), getMethodology = get(), submitActualApplicants = get())
    }
}
