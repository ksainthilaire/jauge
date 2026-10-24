package io.jauge.core.network.payload

import kotlinx.serialization.Serializable

/**
 * A calm alert.
 *
 * @property id identifier.
 * @property analysisId watched analysis.
 * @property threshold score under which the user is notified.
 * @property active whether the alert is on.
 */
@Serializable
data class AlertPayload(val id: String, val analysisId: String, val threshold: Int, val active: Boolean = true)

/**
 * `GET /v1/alerts`.
 *
 * @property items the alerts.
 */
@Serializable
data class AlertListPayload(val items: List<AlertPayload>)

/**
 * Body of `POST /v1/alerts`.
 *
 * @property analysisId watched analysis.
 * @property threshold score under which the user is notified.
 */
@Serializable
data class CreateAlertRequestPayload(val analysisId: String, val threshold: Int)

/**
 * Body of `POST /v1/shares`.
 *
 * @property analysisId shared analysis.
 * @property format `story`, `square` or `link`.
 * @property hideCompany hide the company name.
 * @property showCalmTwin show the calm twin offer.
 */
@Serializable
data class CreateShareRequestPayload(
    val analysisId: String,
    val format: String,
    val hideCompany: Boolean,
    val showCalmTwin: Boolean,
)

/**
 * A share.
 *
 * @property id identifier.
 * @property analysisId shared analysis.
 * @property url public link (`https://jauge.io/a/{analysisId}`).
 * @property imageUrl rendered card image.
 * @property format `story`, `square` or `link`.
 * @property hideCompany company hidden.
 * @property showCalmTwin calm twin shown.
 * @property createdAt creation time, ISO 8601.
 */
@Serializable
data class SharePayload(
    val id: String,
    val analysisId: String,
    val url: String,
    val imageUrl: String? = null,
    val format: String,
    val hideCompany: Boolean = false,
    val showCalmTwin: Boolean = true,
    val createdAt: String,
)

/**
 * `GET /v1/shares`.
 *
 * @property items the shares.
 */
@Serializable
data class ShareListPayload(val items: List<SharePayload>)

/**
 * Consents.
 *
 * @property marketingEmails monthly search tips by email.
 */
@Serializable
data class ConsentsPayload(val marketingEmails: Boolean)

/**
 * `GET /v1/account`.
 *
 * @property id identifier.
 * @property displayName name.
 * @property email email address.
 * @property signInMethod `apple`, `google`, `email` or `none`.
 * @property analysisCount analyses made.
 * @property consents consents.
 */
@Serializable
data class AccountPayload(
    val id: String,
    val displayName: String? = null,
    val email: String? = null,
    val signInMethod: String = "none",
    val analysisCount: Int = 0,
    val consents: ConsentsPayload = ConsentsPayload(marketingEmails = false),
)

/**
 * `GET /v1/subscription`: entitlement known by the server (from the store webhooks) and free quota.
 *
 * @property status `free`, `trial`, `active` or `expired`.
 * @property trialEndsOn last day of the trial, ISO 8601.
 * @property renewsOn next renewal, ISO 8601.
 * @property freeAnalysesLeftThisWeek free analyses left this week.
 */
@Serializable
data class SubscriptionPayload(
    val status: String,
    val trialEndsOn: String? = null,
    val renewsOn: String? = null,
    val freeAnalysesLeftThisWeek: Int = 0,
)

/**
 * Error body sent by the API with every 4xx and 5xx answer: `{"error": {"code": "...", "message": "..."}}`.
 *
 * @property error the error.
 */
@Serializable
data class ApiErrorBodyPayload(val error: ApiErrorPayload)

/**
 * Error details.
 *
 * @property code machine readable code (see `docs/api.md`).
 * @property message developer message, never shown to the user.
 */
@Serializable
data class ApiErrorPayload(val code: String, val message: String? = null)
