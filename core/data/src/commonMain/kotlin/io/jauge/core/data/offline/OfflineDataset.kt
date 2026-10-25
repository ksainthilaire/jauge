package io.jauge.core.data.offline

import io.jauge.core.network.payload.AccountPayload
import io.jauge.core.network.payload.AnalysisPayload
import io.jauge.core.network.payload.AnalysisResultPayload
import io.jauge.core.network.payload.AnalysisSummaryPayload
import io.jauge.core.network.payload.ApplicantsPayload
import io.jauge.core.network.payload.ConsentsPayload
import io.jauge.core.network.payload.MethodologyPayload
import io.jauge.core.network.payload.OfferPayload
import io.jauge.core.network.payload.SalaryInsightPayload
import io.jauge.core.network.payload.SalaryRangePayload
import io.jauge.core.network.payload.SharePayload
import io.jauge.core.network.payload.SkillDemandPayload
import io.jauge.core.network.payload.SkillsInsightPayload
import io.jauge.core.network.payload.TimingAdvicePayload
import io.jauge.core.network.payload.TrendInsightPayload
import io.jauge.core.network.payload.TrendPointPayload
import io.jauge.core.network.payload.TwinOfferPayload
import io.jauge.core.network.payload.TwinOffersPayload
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Sample API payloads of the offline mode, taken from the mockups (`ecrans.html`): the offer of Inès at Atelier
 * Sorel, its twins around Lyon, salaries, skills and trend. They are server content (as the API would send it), not
 * interface texts: interface texts live in `strings.xml`. Dates are relative to the clock so that the data always
 * looks fresh.
 *
 * @param clock source of the current time.
 */
class OfflineDataset(private val clock: Clock = Clock.System) {

    private val today: LocalDate
        get() = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    /** Analysis used for the verdict of the mockups. */
    fun marketingAnalysis(id: String = MARKETING_ID, createdHoursAgo: Int = 20): AnalysisPayload = completed(
        id = id,
        createdHoursAgo = createdHoursAgo,
        offer = OfferPayload(
            jobTitle = "Chargée de marketing digital",
            company = "Atelier Sorel",
            city = "Lyon 3e",
            contractType = "permanent",
            workMode = "Hybride 2 j",
            publishedOn = today.minus(DatePeriod(days = 2)).toString(),
            displayedSalary = null,
            skills = listOf("Google Ads", "Meta Ads", "Brevo", "GA4", "Looker Studio", "SEO"),
            sourceUrl = "https://emplois-rhone.fr/offre/charge-marketing-digital-cdi-lyon-3-41872",
        ),
        result = AnalysisResultPayload(
            score = 78,
            percentileInRegion = 84,
            regionName = "Rhône",
            applicants = ApplicantsPayload(estimate = 210, low = 160, high = 260),
            reliability = "medium",
            estimatedSalary = SalaryRangePayload(36, 41),
            twinOfferCount = 46,
            twinRadiusKilometers = 30,
            trendThreeMonthsPercent = 18,
            comparableOfferCount = 1_284,
            timingAdvice = TimingAdvicePayload(applyBefore = "THURSDAY", sharePercent = 70, withinDays = 5),
        ),
    )

    /** The three analyses of the home screen (mockup 02), newest first. */
    fun initialAnalyses(): List<AnalysisPayload> = listOf(
        marketingAnalysis(),
        completed(
            id = TRAFFIC_ID,
            createdHoursAgo = 50,
            offer = OfferPayload(
                jobTitle = "Traffic manager",
                company = "Groupe Pellat",
                city = "Villeurbanne",
                contractType = "permanent",
                publishedOn = today.minus(DatePeriod(days = 4)).toString(),
                displayedSalary = SalaryRangePayload(35, 40),
                skills = listOf("Google Ads", "Meta Ads", "GA4", "Excel"),
            ),
            result = AnalysisResultPayload(
                score = 44,
                percentileInRegion = 52,
                regionName = "Rhône",
                applicants = ApplicantsPayload(estimate = 85, low = 65, high = 110),
                reliability = "high",
                estimatedSalary = SalaryRangePayload(35, 40),
                twinOfferCount = 31,
                trendThreeMonthsPercent = 6,
                comparableOfferCount = 1_284,
                timingAdvice = TimingAdvicePayload(applyBefore = "FRIDAY", sharePercent = 65, withinDays = 6),
            ),
        ),
        completed(
            id = CRM_ID,
            createdHoursAgo = 5 * 24,
            offer = OfferPayload(
                jobTitle = "Responsable CRM",
                company = "Vélo Ardent",
                city = "Lyon 9e (Vaise)",
                contractType = "permanent",
                publishedOn = today.minus(DatePeriod(days = 7)).toString(),
                skills = listOf("Brevo", "Salesforce", "SQL", "Segmentation"),
            ),
            result = AnalysisResultPayload(
                score = 19,
                percentileInRegion = 21,
                regionName = "Rhône",
                applicants = ApplicantsPayload(estimate = 28, low = 20, high = 40),
                reliability = "medium",
                estimatedSalary = SalaryRangePayload(40, 46),
                twinOfferCount = 12,
                trendThreeMonthsPercent = -4,
                comparableOfferCount = 1_284,
                timingAdvice = null,
            ),
        ),
    )

    /** Summary of an analysis for lists. */
    fun summaryOf(analysis: AnalysisPayload): AnalysisSummaryPayload? {
        val offer = analysis.offer ?: return null
        val result = analysis.result ?: return null
        return AnalysisSummaryPayload(analysis.id, analysis.createdAt, offer.jobTitle, offer.company, offer.city, result.score)
    }

    /** Salary insight (mockup 05). */
    fun salary(): SalaryInsightPayload = SalaryInsightPayload(
        areaName = "Lyon",
        offerCount = 412,
        scaleMinimum = 30,
        scaleMaximum = 48,
        middleHalf = SalaryRangePayload(33, 44),
        median = 37,
        offerRange = SalaryRangePayload(36, 41),
        negotiationTarget = 40,
    )

    /** Skills insight (mockup 05). */
    fun skills(): SkillsInsightPayload = SkillsInsightPayload(
        listOf(
            SkillDemandPayload("Google Ads", 82, owned = true),
            SkillDemandPayload("GA4", 74, owned = true),
            SkillDemandPayload("Meta Ads", 69, owned = true),
            SkillDemandPayload("Looker Studio", 41, owned = false),
            SkillDemandPayload("Brevo / emailing", 37, owned = true),
        ),
    )

    /** Twelve months of applicants per offer ending with the current month (mockup 05, +18 %). */
    fun trend(): TrendInsightPayload {
        val values = listOf(150, 154, 143, 159, 155, 164, 161, 172, 181, 190, 204, 215)
        val currentMonth = LocalDate(today.year, today.month, 1)
        val points = values.mapIndexed { index, value ->
            val month = currentMonth.minus(DatePeriod(months = values.lastIndex - index))
            TrendPointPayload(month = month.toString().take(7), applicantsPerOffer = value)
        }
        return TrendInsightPayload(points = points, changeThreeMonthsPercent = 18)
    }

    /** Twin offers around Lyon (mockup 06). */
    fun twins(): TwinOffersPayload = TwinOffersPayload(
        areaName = "Lyon + 30 km",
        radiusKilometers = 30,
        items = listOf(
            TwinOfferPayload("twin-cooperative-brune", "Chargé·e marketing digital", "Coopérative Brune", "Écully", SalaryRangePayload(38, 42), 35, 22, 9, today.minus(DatePeriod(days = 1)).toString()),
            TwinOfferPayload("twin-ferme-numerique", "Chef·fe de projet acquisition", "Ferme Numérique", "Lyon 7e", SalaryRangePayload(35, 39), 60, 31, 4, today.minus(DatePeriod(days = 3)).toString()),
            TwinOfferPayload("twin-studio-mirabelle", "Growth marketer junior", "Studio Mirabelle", "Part-Dieu", SalaryRangePayload(33, 37), 95, 48, 2, today.toString()),
            TwinOfferPayload("twin-maison-odier", "Marketing digital & CRM", "Maison Odier", "Caluire", SalaryRangePayload(37, 40), 120, 58, 6, today.minus(DatePeriod(days = 2)).toString()),
        ),
    )

    /** Methodology of the marketing analysis (mockup 09). */
    fun methodology(): MethodologyPayload = MethodologyPayload(
        applicants = ApplicantsPayload(estimate = 210, low = 160, high = 260),
        reliability = "medium",
        reliabilityReasons = listOf("Offre en ligne depuis 2 jours seulement.", "Pas de salaire affiché."),
        comparableOfferCount = 1_284,
        regionName = "Rhône",
        salarySourceCount = 412,
    )

    /** Account of Inès (mockup 13). */
    fun account(analysisCount: Int): AccountPayload = AccountPayload(
        id = "user-ines",
        displayName = "Inès Bertrand",
        email = "ines.bertrand@example.com",
        signInMethod = "apple",
        analysisCount = analysisCount,
        consents = ConsentsPayload(marketingEmails = false),
    )

    /** Share already made from the verdict. */
    fun initialShares(): List<SharePayload> = listOf(
        SharePayload(
            id = "share-marketing",
            analysisId = MARKETING_ID,
            url = "https://jauge.io/a/$MARKETING_ID",
            imageUrl = null,
            format = "story",
            hideCompany = false,
            showCalmTwin = true,
            createdAt = (clock.now() - 18.hours).toString(),
        ),
    )

    /** ISO creation time [hoursAgo] hours before now. */
    fun createdAt(hoursAgo: Int): String = (clock.now() - hoursAgo.hours).toString()

    /** ISO creation time [daysAgo] days before now. */
    fun createdDaysAgo(daysAgo: Int): String = (clock.now() - daysAgo.days).toString()

    private fun completed(id: String, createdHoursAgo: Int, offer: OfferPayload, result: AnalysisResultPayload) = AnalysisPayload(
        id = id,
        status = AnalysisPayload.STATUS_COMPLETED,
        createdAt = createdAt(createdHoursAgo),
        progressPercent = 100,
        offer = offer,
        result = result,
    )

    /** Identifiers of the sample analyses. */
    companion object {
        /** "Chargée de marketing digital", Atelier Sorel, score 78. */
        const val MARKETING_ID = "demo-marketing-digital"

        /** Department of the sample data (Rhône): twin offers keep their own places there. */
        const val SAMPLE_DEPARTMENT_CODE = "69"

        /** "Traffic manager", Groupe Pellat, score 44. */
        const val TRAFFIC_ID = "demo-traffic-manager"

        /** "Responsable CRM", Vélo Ardent, score 19. */
        const val CRM_ID = "demo-responsable-crm"

        /** Company still readable on the unreadable screenshot (mockup 10). */
        const val UNREADABLE_COMPANY = "Maison Odier"
    }
}
