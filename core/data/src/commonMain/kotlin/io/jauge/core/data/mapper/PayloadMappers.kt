package io.jauge.core.data.mapper

import io.jauge.core.domain.model.AnalysisAdvice
import io.jauge.core.network.payload.AdvicePayload
import io.jauge.core.data.error.apiErrorCodeToJaugeError
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Account
import io.jauge.core.domain.model.Analysis
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.ApplicantEstimate
import io.jauge.core.domain.model.CalmAlert
import io.jauge.core.domain.model.Consents
import io.jauge.core.domain.model.ContractType
import io.jauge.core.domain.model.JobOffer
import io.jauge.core.domain.model.Methodology
import io.jauge.core.domain.model.ReadingStep
import io.jauge.core.domain.model.ReadingStepKind
import io.jauge.core.domain.model.ReadingStepStatus
import io.jauge.core.domain.model.Reliability
import io.jauge.core.domain.model.SalaryInsight
import io.jauge.core.domain.model.SalaryRange
import io.jauge.core.domain.model.Share
import io.jauge.core.domain.model.ShareFormat
import io.jauge.core.domain.model.ShareOptions
import io.jauge.core.domain.model.SignInMethod
import io.jauge.core.domain.model.SkillDemand
import io.jauge.core.domain.model.SkillsInsight
import io.jauge.core.domain.model.Subscription
import io.jauge.core.domain.model.SubscriptionStatus
import io.jauge.core.domain.model.TimingAdvice
import io.jauge.core.domain.model.TrendInsight
import io.jauge.core.domain.model.TrendPoint
import io.jauge.core.domain.model.TwinOffer
import io.jauge.core.domain.model.TwinOffers
import io.jauge.core.domain.model.TwinSort
import io.jauge.core.network.payload.AccountPayload
import io.jauge.core.network.payload.AlertPayload
import io.jauge.core.network.payload.AnalysisPayload
import io.jauge.core.network.payload.AnalysisSummaryPayload
import io.jauge.core.network.payload.ApplicantsPayload
import io.jauge.core.network.payload.MethodologyPayload
import io.jauge.core.network.payload.OfferPayload
import io.jauge.core.network.payload.SalaryInsightPayload
import io.jauge.core.network.payload.SalaryRangePayload
import io.jauge.core.network.payload.SharePayload
import io.jauge.core.network.payload.SkillsInsightPayload
import io.jauge.core.network.payload.SubscriptionPayload
import io.jauge.core.network.payload.TrendInsightPayload
import io.jauge.core.network.payload.TwinOffersPayload
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

// Mapping rules: a value that cannot be read becomes null (an empty field on screen) rather than a wrong value.
// Required values that are missing or invalid raise IllegalArgumentException, mapped to JaugeError.InvalidData.

/** Parses an ISO 8601 date, or returns null. */
internal fun String?.toLocalDateOrNull(): LocalDate? = this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** Parses an ISO 8601 instant, or returns null. */
internal fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }

/** Parses a month (`2026-10`) as the first day of the month, or returns null. */
internal fun String.toMonthOrNull(): LocalDate? = runCatching { LocalDate.parse("${take(7)}-01") }.getOrNull()

/** Maps a salary range, dropping invalid ranges. */
internal fun SalaryRangePayload.toDomainOrNull(): SalaryRange? =
    SalaryRange(minimum, maximum).takeIf { it.isValid }

/** Maps an applicants estimate, ordering the bounds. */
internal fun ApplicantsPayload.toDomain(): ApplicantEstimate {
    require(estimate >= 0) { "Negative estimate" }
    return ApplicantEstimate(estimate = estimate, low = minOf(low, estimate), high = maxOf(high, estimate))
}

/** Maps a reliability code, medium when unknown. */
internal fun String.toReliability(): Reliability = when (lowercase()) {
    "high" -> Reliability.HIGH
    "low" -> Reliability.LOW
    else -> Reliability.MEDIUM
}

/** Maps a contract code. */
internal fun String.toContractType(): ContractType = when (lowercase()) {
    "permanent" -> ContractType.PERMANENT
    "fixed_term" -> ContractType.FIXED_TERM
    "apprenticeship" -> ContractType.APPRENTICESHIP
    "internship" -> ContractType.INTERNSHIP
    "freelance" -> ContractType.FREELANCE
    "temporary" -> ContractType.TEMPORARY
    else -> ContractType.OTHER
}

/** Maps an offer. */
internal fun OfferPayload.toDomain(): JobOffer = JobOffer(
    jobTitle = jobTitle.trim().also { require(it.isNotEmpty()) { "Missing job title" } },
    company = company?.trim()?.takeIf { it.isNotEmpty() },
    city = city.trim(),
    contractType = contractType.toContractType(),
    workMode = workMode?.takeIf { it.isNotBlank() },
    publishedOn = publishedOn.toLocalDateOrNull(),
    displayedSalary = displayedSalary?.toDomainOrNull(),
    skills = skills.filter { it.isNotBlank() },
    sourceUrl = sourceUrl,
)

/** Maps a completed analysis. Throws [IllegalArgumentException] when the analysis is not complete. */
internal fun AnalysisPayload.toDomainAnalysis(): Analysis {
    val offerPayload = requireNotNull(offer) { "Analysis without offer" }
    val resultPayload = requireNotNull(result) { "Analysis without result" }
    return Analysis(
        id = id,
        createdAt = requireNotNull(createdAt.toInstantOrNull()) { "Invalid creation date" },
        offer = offerPayload.toDomain(),
        score = resultPayload.score.coerceIn(0, 100),
        percentileInRegion = resultPayload.percentileInRegion.coerceIn(0, 100),
        regionName = resultPayload.regionName,
        applicants = resultPayload.applicants.toDomain(),
        reliability = resultPayload.reliability.toReliability(),
        estimatedSalary = resultPayload.estimatedSalary?.toDomainOrNull(),
        twinOfferCount = resultPayload.twinOfferCount.coerceAtLeast(0),
        twinRadiusKilometers = resultPayload.twinRadiusKilometers.coerceAtLeast(0),
        trendThreeMonthsPercent = resultPayload.trendThreeMonthsPercent,
        comparableOfferCount = resultPayload.comparableOfferCount.coerceAtLeast(0),
        timingAdvice = resultPayload.timingAdvice?.let { advice ->
            runCatching { DayOfWeek.valueOf(advice.applyBefore.uppercase()) }.getOrNull()?.let { day ->
                TimingAdvice(day, advice.sharePercent.coerceIn(0, 100), advice.withinDays.coerceAtLeast(1))
            }
        },
        isFavorite = isFavorite,
        advice = resultPayload.advice?.toDomainOrNull(),
    )
}

/** Maps the advice, or null when its text is blank. Blank skills are dropped, 4 at most are kept. */
internal fun AdvicePayload.toDomainOrNull(): AnalysisAdvice? {
    val adviceText = text.trim().takeIf { it.isNotEmpty() } ?: return null
    return AnalysisAdvice(
        text = adviceText,
        negotiationArgument = negotiationArgument?.trim()?.takeIf { it.isNotEmpty() },
        missingSkills = missingSkills.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAXIMUM_MISSING_SKILLS),
    )
}

private const val MAXIMUM_MISSING_SKILLS = 4



/** Maps a reading step, or null for an unknown step. */
private fun io.jauge.core.network.payload.ReadingStepPayload.toDomainOrNull(): ReadingStep? {
    val kind = when (kind) {
        "position_and_place" -> ReadingStepKind.POSITION_AND_PLACE
        "skills" -> ReadingStepKind.SKILLS
        "comparison" -> ReadingStepKind.COMPARISON
        "applicants" -> ReadingStepKind.APPLICANTS
        else -> return null
    }
    val status = when (status) {
        "done" -> ReadingStepStatus.DONE
        "running" -> ReadingStepStatus.RUNNING
        else -> ReadingStepStatus.PENDING
    }
    return ReadingStep(kind, status, detail?.takeIf { it.isNotBlank() })
}

/** Maps an analysis to its progress: reading, completed or failed. */
internal fun AnalysisPayload.toProgress(): AnalysisProgress = when (status) {
    AnalysisPayload.STATUS_COMPLETED -> AnalysisProgress.Completed(toDomainAnalysis())
    AnalysisPayload.STATUS_FAILED -> AnalysisProgress.Failed(
        apiErrorCodeToJaugeError(failure?.code, failure?.recognizedCompany) ?: JaugeError.Unknown,
    )
    else -> AnalysisProgress.Reading(
        analysisId = id,
        percent = progressPercent.coerceIn(0, 100),
        steps = steps.mapNotNull { it.toDomainOrNull() },
        publishedDaysAgo = publishedDaysAgo?.takeIf { it >= 0 },
        hasDisplayedSalary = hasDisplayedSalary,
    )
}

/** Maps a list item, or null when it cannot be read. */
internal fun AnalysisSummaryPayload.toDomainOrNull(): AnalysisSummary? {
    val created = createdAt.toInstantOrNull() ?: return null
    if (jobTitle.isBlank()) return null
    return AnalysisSummary(id, created, jobTitle.trim(), company?.takeIf { it.isNotBlank() }, place, score.coerceIn(0, 100), isFavorite)
}

/** Maps the salary insight. */
internal fun SalaryInsightPayload.toDomain(): SalaryInsight {
    require(scaleMinimum < scaleMaximum) { "Invalid salary scale" }
    return SalaryInsight(
        areaName = areaName,
        offerCount = offerCount.coerceAtLeast(0),
        scaleMinimumThousands = scaleMinimum,
        scaleMaximumThousands = scaleMaximum,
        middleHalf = requireNotNull(middleHalf.toDomainOrNull()) { "Invalid middle half" },
        medianThousands = median,
        offerRange = offerRange?.toDomainOrNull(),
        negotiationTargetThousands = negotiationTarget?.takeIf { it > 0 },
    )
}

/** Maps the skills insight. */
internal fun SkillsInsightPayload.toDomain(): SkillsInsight =
    SkillsInsight(skills.filter { it.name.isNotBlank() }.map { SkillDemand(it.name, it.sharePercent, it.owned) })

/** Maps the trend, dropping unreadable months. */
internal fun TrendInsightPayload.toDomain(): TrendInsight = TrendInsight(
    points = points.mapNotNull { point -> point.month.toMonthOrNull()?.let { TrendPoint(it, point.applicantsPerOffer.coerceAtLeast(0)) } },
    changeThreeMonthsPercent = changeThreeMonthsPercent,
)

/** Maps the methodology. */
internal fun MethodologyPayload.toDomain(): Methodology = Methodology(
    applicants = applicants.toDomain(),
    reliability = reliability.toReliability(),
    reliabilityReasons = reliabilityReasons.filter { it.isNotBlank() },
    comparableOfferCount = comparableOfferCount.coerceAtLeast(0),
    regionName = regionName,
    salarySourceCount = salarySourceCount.coerceAtLeast(0),
)

/** API value of a sort order. */
internal fun TwinSort.toApiValue(): String = when (this) {
    TwinSort.LEAST_DISPUTED -> "least_disputed"
    TwinSort.BEST_PAID -> "best_paid"
    TwinSort.MOST_RECENT -> "most_recent"
}

/** Maps twin offers. */
internal fun TwinOffersPayload.toDomain(): TwinOffers = TwinOffers(
    areaName = areaName,
    radiusKilometers = radiusKilometers,
    offers = items.filter { it.jobTitle.isNotBlank() }.map { item ->
        TwinOffer(
            id = item.id,
            jobTitle = item.jobTitle,
            company = item.company,
            place = item.place,
            salary = item.salary?.toDomainOrNull(),
            estimatedApplicants = item.estimatedApplicants.coerceAtLeast(0),
            score = item.score.coerceIn(0, 100),
            distanceKilometers = item.distanceKilometers.coerceAtLeast(0),
            publishedOn = item.publishedOn.toLocalDateOrNull(),
            url = item.url,
        )
    },
)

/** Maps an alert. */
internal fun AlertPayload.toDomain(): CalmAlert = CalmAlert(id, analysisId, threshold.coerceIn(1, 100), active)

/** API value of a share format. */
internal fun ShareFormat.toApiValue(): String = name.lowercase()

/** Maps a share, or null when it cannot be read. */
internal fun SharePayload.toDomainOrNull(): Share? {
    val created = createdAt.toInstantOrNull() ?: return null
    val shareFormat = ShareFormat.entries.firstOrNull { it.name.equals(format, ignoreCase = true) } ?: ShareFormat.LINK
    return Share(id, analysisId, url, imageUrl, ShareOptions(shareFormat, hideCompany, showCalmTwin), created)
}

/** Maps the account. */
internal fun AccountPayload.toDomain(): Account = Account(
    id = id,
    displayName = displayName?.takeIf { it.isNotBlank() },
    email = email?.takeIf { it.isNotBlank() },
    signInMethod = when (signInMethod.lowercase()) {
        "apple" -> SignInMethod.APPLE
        "google" -> SignInMethod.GOOGLE
        "email" -> SignInMethod.EMAIL
        else -> SignInMethod.NONE
    },
    analysisCount = analysisCount.coerceAtLeast(0),
    consents = Consents(consents.marketingEmails),
)

/** Maps the subscription known by the server. */
internal fun SubscriptionPayload.toDomain(): Subscription = Subscription(
    status = when (status.lowercase()) {
        "trial" -> SubscriptionStatus.TRIAL
        "active" -> SubscriptionStatus.ACTIVE
        "expired" -> SubscriptionStatus.EXPIRED
        else -> SubscriptionStatus.FREE
    },
    trialEndsOn = trialEndsOn.toLocalDateOrNull(),
    renewsOn = renewsOn.toLocalDateOrNull(),
    freeAnalysesLeftThisWeek = freeAnalysesLeftThisWeek.coerceAtLeast(0),
)
