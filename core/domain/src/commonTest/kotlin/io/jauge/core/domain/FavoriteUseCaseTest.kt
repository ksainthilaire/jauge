package io.jauge.core.domain

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.usecase.AnalysisFilter
import io.jauge.core.domain.usecase.FavoriteAnalysisUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant

class FavoriteUseCaseTest {
    private val analyses = listOf(
        AnalysisSummary("a", Instant.fromEpochSeconds(3), "Chargée de marketing digital", "Atelier Sorel", "Lyon 3e", 78, isFavorite = true),
        AnalysisSummary("b", Instant.fromEpochSeconds(2), "Traffic manager", null, "Villeurbanne", 44),
        AnalysisSummary("c", Instant.fromEpochSeconds(1), "Responsable CRM", "Vélo Ardent", "Vaise", 19, isFavorite = true),
    )

    @Test
    fun favoritesFilterKeepsOnlyStarsInOrder() {
        assertEquals(listOf("a", "c"), AnalysisFilter.FAVORITES.apply(analyses).map { it.id })
        assertEquals(analyses, AnalysisFilter.ALL.apply(analyses))
    }

    @Test
    fun blankIdentifierIsNotFound() = runTest {
        val useCase = FavoriteAnalysisUseCase(FakeAnalysisRepository())
        assertEquals(Outcome.Failure(JaugeError.NotFound), useCase(" ", true))
    }

    @Test
    fun repositoryWithoutFavoritesSaysNothingIsStarred() = runTest {
        val useCase = FavoriteAnalysisUseCase(FakeAnalysisRepository())
        assertFalse(useCase.observe("a").first())
    }
}
