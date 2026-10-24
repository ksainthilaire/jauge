package io.jauge.feature.analysis

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Analysis
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.AuthenticatedUser
import io.jauge.core.domain.model.AuthenticationProvider
import io.jauge.core.domain.model.AuthenticationState
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.SignInMethod
import io.jauge.core.domain.repository.AnalysisRepository
import io.jauge.core.domain.repository.AuthenticationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Clock

/** Analyses of the mockup 02. */
val sampleAnalyses = listOf(
    AnalysisSummary("analysis-sorel", Clock.System.now(), "Chargée de marketing digital", "Atelier Sorel", "Lyon 3e", 78),
    AnalysisSummary("analysis-pellat", Clock.System.now(), "Traffic manager", "Groupe Pellat", "Villeurbanne", 44),
    AnalysisSummary("analysis-ardent", Clock.System.now(), "Responsable CRM", "Vélo Ardent", "Vaise", 19),
)

/** Analyses with programmable answers. */
class FakeAnalysisRepository : AnalysisRepository {
    /** Stored history. */
    val history = MutableStateFlow<List<AnalysisSummary>>(emptyList())

    /** Error returned by the next refresh, null for a success. */
    var refreshError: JaugeError? = null

    /** Progress returned by [analyze]. */
    var progress: List<AnalysisProgress> = emptyList()

    /** Last analyzed source. */
    var analyzed: OfferSource? = null

    override fun analyze(source: OfferSource): Flow<AnalysisProgress> {
        analyzed = source
        return flowOf(*progress.toTypedArray())
    }

    /** Favorite identifiers. */
    val favoriteIds = MutableStateFlow<Set<String>>(emptySet())

    /** Error returned by the next star change (after undoing it), null for a success. */
    var favoriteError: JaugeError? = null

    /** Star values seen while the server was answering, to check the optimistic update. */
    val favoritesDuringCall = mutableListOf<Set<String>>()

    override fun observeHistory(): Flow<List<AnalysisSummary>> =
        combine(history, favoriteIds) { list, favorites -> list.map { it.copy(isFavorite = it.id in favorites) } }

    override fun observeFavoriteIds(): Flow<Set<String>> = favoriteIds

    override suspend fun setFavorite(analysisId: String, isFavorite: Boolean): Outcome<Unit> {
        val before = favoriteIds.value
        favoriteIds.value = if (isFavorite) before + analysisId else before - analysisId
        favoritesDuringCall += favoriteIds.value
        favoriteError?.let {
            favoriteIds.value = before
            return Outcome.Failure(it)
        }
        return Outcome.Success(Unit)
    }

    override suspend fun refreshHistory(): Outcome<Unit> {
        refreshError?.let { return Outcome.Failure(it) }
        history.value = sampleAnalyses
        return Outcome.Success(Unit)
    }

    override suspend fun getAnalysis(analysisId: String): Outcome<Analysis> = Outcome.Failure(JaugeError.NotFound)

    override suspend fun deleteAnalysis(analysisId: String): Outcome<Unit> = Outcome.Success(Unit)
}

/** Authentication signed in as Inès. */
class SignedInAuthenticationRepository : AuthenticationRepository {
    override val state: StateFlow<AuthenticationState> = MutableStateFlow(
        AuthenticationState.SignedIn(AuthenticatedUser("user-1", null, "Inès Bertrand", SignInMethod.APPLE)),
    )

    override suspend fun signInWith(provider: AuthenticationProvider): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun sendMagicLink(email: String): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun completeSignIn(url: String): Outcome<Boolean> = Outcome.Success(false)

    override suspend fun signOut(): Outcome<Unit> = Outcome.Success(Unit)
}
