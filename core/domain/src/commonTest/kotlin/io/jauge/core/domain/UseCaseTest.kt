package io.jauge.core.domain

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Account
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.CompetitionLevel
import io.jauge.core.domain.model.Consents
import io.jauge.core.domain.model.ImageOrigin
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.SalaryRange
import io.jauge.core.domain.model.ShareLinks
import io.jauge.core.domain.model.SignInMethod
import io.jauge.core.domain.model.TwinOffer
import io.jauge.core.domain.model.TwinOffers
import io.jauge.core.domain.model.TwinSort
import io.jauge.core.domain.model.getOrNull
import io.jauge.core.domain.usecase.AnalyzeOfferUseCase
import io.jauge.core.domain.usecase.ComputeTrialTimelineUseCase
import io.jauge.core.domain.usecase.CreateCalmAlertUseCase
import io.jauge.core.domain.usecase.CreateShareUseCase
import io.jauge.core.domain.usecase.GetSharesUseCase
import io.jauge.core.domain.usecase.GetTwinOffersUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UseCaseTest {

    @Test
    fun competitionLevelsFollowTheMockupThresholds() {
        assertEquals(CompetitionLevel.CALM, CompetitionLevel.fromScore(19))
        assertEquals(CompetitionLevel.DISPUTED, CompetitionLevel.fromScore(44))
        assertEquals(CompetitionLevel.CROWDED, CompetitionLevel.fromScore(78))
        assertEquals(CompetitionLevel.CROWDED, CompetitionLevel.fromScore(250))
    }

    @Test
    fun linksAreNormalized() {
        assertEquals(
            "https://emplois-rhone.fr/offre/charge-marketing-digital-cdi-lyon-3-41872",
            AnalyzeOfferUseCase.normalizeLink("  emplois-rhone.fr/offre/charge-marketing-digital-cdi-lyon-3-41872 "),
        )
        assertEquals("http://example.com/job", AnalyzeOfferUseCase.normalizeLink("http://example.com/job"))
        assertNull(AnalyzeOfferUseCase.normalizeLink("bonjour tout le monde"))
        assertNull(AnalyzeOfferUseCase.normalizeLink(""))
    }

    @Test
    fun invalidLinkFailsWithoutCallingTheServer() = runTest {
        val repository = FakeAnalysisRepository()
        val progress = AnalyzeOfferUseCase(repository)(OfferSource.Link("pas un lien")).first()
        assertEquals(AnalysisProgress.Failed(JaugeError.InvalidLink), progress)
        assertTrue(repository.receivedSources.isEmpty())
    }

    @Test
    fun validLinkIsSentNormalized() = runTest {
        val repository = FakeAnalysisRepository()
        AnalyzeOfferUseCase(repository)(OfferSource.Link("jobs.example.com/42")).first()
        assertEquals(OfferSource.Link("https://jobs.example.com/42"), repository.receivedSources.single())
    }

    @Test
    fun emptyManualEntryAndTooLargeImagesAreRefused() = runTest {
        val repository = FakeAnalysisRepository()
        val useCase = AnalyzeOfferUseCase(repository)
        assertEquals(AnalysisProgress.Failed(JaugeError.InvalidData), useCase(OfferSource.Manual(" ", "Lyon")).first())
        val huge = OfferSource.Image(ByteArray(AnalyzeOfferUseCase.MAXIMUM_IMAGE_BYTES + 1), "image/jpeg", ImageOrigin.PHOTO)
        assertEquals(AnalysisProgress.Failed(JaugeError.ImageTooLarge), useCase(huge).first())
        assertTrue(repository.receivedSources.isEmpty())
    }

    @Test
    fun trialTimelineMatchesThePaywall() {
        val timeline = ComputeTrialTimelineUseCase.timelineFrom(LocalDate(2026, 10, 23), trialDays = 3)
        assertEquals(LocalDate(2026, 10, 25), timeline.reminderOn)
        assertEquals(LocalDate(2026, 10, 26), timeline.firstPaymentOn)
    }

    @Test
    fun calmAlertThresholdIsValidated() = runTest {
        val repository = FakeTwinRepository(TwinOffers("Lyon", 30, emptyList()))
        val useCase = CreateCalmAlertUseCase(repository)
        assertEquals(Outcome.Failure(JaugeError.InvalidData), useCase("analysis", threshold = 0))
        assertIs<Outcome.Success<*>>(useCase("analysis"))
        assertEquals(30, repository.createdThreshold)
    }

    @Test
    fun twinOffersAreSortedAsRequested() = runTest {
        fun twin(id: String, score: Int, salary: Int, day: Int) =
            TwinOffer(id, "Job", "Company", "Lyon", SalaryRange(salary, salary + 4), 10, score, 5, LocalDate(2026, 10, day), null)
        val twins = TwinOffers("Lyon", 30, listOf(twin("a", 58, 37, 20), twin("b", 22, 38, 23), twin("c", 31, 35, 21)))
        val useCase = GetTwinOffersUseCase(FakeTwinRepository(twins))
        assertEquals(listOf("b", "c", "a"), useCase("x", TwinSort.LEAST_DISPUTED).getOrNull()?.offers?.map { it.id })
        assertEquals(listOf("b", "a", "c"), useCase("x", TwinSort.BEST_PAID).getOrNull()?.offers?.map { it.id })
        assertEquals(listOf("b", "c", "a"), useCase("x", TwinSort.MOST_RECENT).getOrNull()?.offers?.map { it.id })
    }

    @Test
    fun shareLinkIsAlwaysTheJaugeLink() = runTest {
        val share = CreateShareUseCase(FakeShareRepository())("demo-marketing-digital").getOrNull()
        assertEquals("https://jauge.io/a/demo-marketing-digital", share?.url)
        assertEquals("https://jauge.io/a/abc", ShareLinks.forAnalysis(" abc "))
    }

    @Test
    fun sharesAreNewestFirst() = runTest {
        assertEquals(listOf("new", "old"), GetSharesUseCase(FakeShareRepository())().getOrNull()?.map { it.id })
    }

    @Test
    fun accountInitials() {
        val account = Account("id", "Inès Bertrand", null, SignInMethod.APPLE, 12, Consents(false))
        assertEquals("IB", account.initials)
        assertEquals("", account.copy(displayName = null).initials)
    }
}
