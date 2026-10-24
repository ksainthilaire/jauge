package io.jauge.core.network.payload

import kotlinx.serialization.Serializable

/**
 * `GET /v1/analyses/{id}/salary`.
 *
 * @property areaName city or area.
 * @property offerCount comparable offers showing a salary.
 * @property scaleMinimum left end of the scale.
 * @property scaleMaximum right end of the scale.
 * @property middleHalf range where half of the offers pay.
 * @property median local median.
 * @property offerRange estimated range of this offer.
 * @property negotiationTarget suggested amount to ask for.
 */
@Serializable
data class SalaryInsightPayload(
    val areaName: String,
    val offerCount: Int,
    val scaleMinimum: Int,
    val scaleMaximum: Int,
    val middleHalf: SalaryRangePayload,
    val median: Int,
    val offerRange: SalaryRangePayload? = null,
    val negotiationTarget: Int? = null,
)

/**
 * One skill.
 *
 * @property name name of the skill.
 * @property sharePercent share of twin offers asking for it.
 * @property owned whether the user has it, null when unknown.
 */
@Serializable
data class SkillDemandPayload(val name: String, val sharePercent: Int, val owned: Boolean? = null)

/**
 * `GET /v1/analyses/{id}/skills`.
 *
 * @property skills the skills.
 */
@Serializable
data class SkillsInsightPayload(val skills: List<SkillDemandPayload>)

/**
 * One month of the trend.
 *
 * @property month month, ISO 8601 (`2026-10`).
 * @property applicantsPerOffer average applicants per offer.
 */
@Serializable
data class TrendPointPayload(val month: String, val applicantsPerOffer: Int)

/**
 * `GET /v1/analyses/{id}/trend`.
 *
 * @property points monthly points.
 * @property changeThreeMonthsPercent change over 3 months.
 */
@Serializable
data class TrendInsightPayload(val points: List<TrendPointPayload>, val changeThreeMonthsPercent: Int)

/**
 * `GET /v1/methodology?analysisId=`.
 *
 * @property applicants the estimate explained.
 * @property reliability `high`, `medium` or `low`.
 * @property reliabilityReasons reasons, in the language of `Accept-Language`.
 * @property comparableOfferCount comparable offers used.
 * @property regionName region.
 * @property salarySourceCount comparable offers showing a salary.
 */
@Serializable
data class MethodologyPayload(
    val applicants: ApplicantsPayload,
    val reliability: String,
    val reliabilityReasons: List<String> = emptyList(),
    val comparableOfferCount: Int,
    val regionName: String,
    val salarySourceCount: Int,
)

/**
 * One twin offer.
 *
 * @property id identifier.
 * @property jobTitle title of the position.
 * @property company company name.
 * @property place city or district.
 * @property salary salary range.
 * @property estimatedApplicants estimated applicants.
 * @property score competition score.
 * @property distanceKilometers distance from the analyzed offer.
 * @property publishedOn publication date, ISO 8601.
 * @property url link of the offer.
 */
@Serializable
data class TwinOfferPayload(
    val id: String,
    val jobTitle: String,
    val company: String,
    val place: String,
    val salary: SalaryRangePayload? = null,
    val estimatedApplicants: Int,
    val score: Int,
    val distanceKilometers: Int,
    val publishedOn: String? = null,
    val url: String? = null,
)

/**
 * `GET /v1/analyses/{id}/twins?sort=`.
 *
 * @property areaName area searched.
 * @property radiusKilometers radius.
 * @property items the offers.
 */
@Serializable
data class TwinOffersPayload(val areaName: String, val radiusKilometers: Int, val items: List<TwinOfferPayload>)
