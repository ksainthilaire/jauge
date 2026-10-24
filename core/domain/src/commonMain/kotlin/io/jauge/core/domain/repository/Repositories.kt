package io.jauge.core.domain.repository

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Account
import io.jauge.core.domain.model.Analysis
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.CalmAlert
import io.jauge.core.domain.model.Consents
import io.jauge.core.domain.model.Methodology
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.SalaryInsight
import io.jauge.core.domain.model.Share
import io.jauge.core.domain.model.ShareOptions
import io.jauge.core.domain.model.SkillsInsight
import io.jauge.core.domain.model.Subscription
import io.jauge.core.domain.model.SubscriptionOffer
import io.jauge.core.domain.model.TrendInsight
import io.jauge.core.domain.model.TwinOffers
import io.jauge.core.domain.model.TwinSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Analyses of offers. Implementations never throw: failures come back as [Outcome.Failure]. */
interface AnalysisRepository {
    /**
     * Starts an analysis and follows it until it completes or fails.
     *
     * @param source link, image or manual entry.
     */
    fun analyze(source: OfferSource): Flow<AnalysisProgress>

    /** Analyses of the user, newest first, updated after every refresh, analysis or deletion. */
    fun observeHistory(): Flow<List<AnalysisSummary>>

    /** Reloads the analyses of the user from the server. */
    suspend fun refreshHistory(): Outcome<Unit>

    /**
     * Loads one analysis.
     *
     * @param analysisId identifier of the analysis.
     */
    suspend fun getAnalysis(analysisId: String): Outcome<Analysis>

    /**
     * Deletes one analysis.
     *
     * @param analysisId identifier of the analysis.
     */
    suspend fun deleteAnalysis(analysisId: String): Outcome<Unit>

    /**
     * Identifiers of the favorite analyses, kept on the device and updated at once after every change (before the
     * server answers).
     */
    fun observeFavoriteIds(): Flow<Set<String>> = flowOf(emptySet())

    /**
     * Marks an analysis as a favorite or removes it from the favorites. The change is optimistic: it is visible at
     * once in [observeFavoriteIds] and [observeHistory], and undone when the server refuses it.
     *
     * @param analysisId identifier of the analysis.
     * @param isFavorite true to add the star, false to remove it.
     * @return success, or the failure after the change was undone.
     */
    suspend fun setFavorite(analysisId: String, isFavorite: Boolean): Outcome<Unit> = Outcome.Failure(JaugeError.Unknown)
}

/** Details of an analysis: salaries, skills, trend and methodology. */
interface InsightRepository {
    /**
     * Salary of the position in the area.
     *
     * @param analysisId identifier of the analysis.
     */
    suspend fun getSalary(analysisId: String): Outcome<SalaryInsight>

    /**
     * Skills asked for by twin offers.
     *
     * @param analysisId identifier of the analysis.
     */
    suspend fun getSkills(analysisId: String): Outcome<SkillsInsight>

    /**
     * Applicants per offer over 12 months.
     *
     * @param analysisId identifier of the analysis.
     */
    suspend fun getTrend(analysisId: String): Outcome<TrendInsight>

    /**
     * How the estimate of an analysis was made.
     *
     * @param analysisId identifier of the analysis.
     */
    suspend fun getMethodology(analysisId: String): Outcome<Methodology>

    /**
     * Sends the real number of applicants known by the user ("Tu connais le vrai chiffre ?").
     *
     * @param analysisId identifier of the analysis.
     * @param actualApplicants number given by the user.
     */
    suspend fun submitActualApplicants(analysisId: String, actualApplicants: Int): Outcome<Unit>
}

/** Twin offers and calm alerts. */
interface TwinRepository {
    /**
     * Twin offers of an analysis.
     *
     * @param analysisId identifier of the analysis.
     * @param sort requested order.
     */
    suspend fun getTwinOffers(analysisId: String, sort: TwinSort): Outcome<TwinOffers>

    /** Calm alerts of the user, updated after every change. */
    fun observeAlerts(): Flow<List<CalmAlert>>

    /** Reloads the alerts from the server. */
    suspend fun refreshAlerts(): Outcome<Unit>

    /**
     * Creates a calm alert.
     *
     * @param analysisId analysis whose twins are watched.
     * @param threshold score under which the user is notified.
     */
    suspend fun createAlert(analysisId: String, threshold: Int): Outcome<CalmAlert>

    /**
     * Deletes a calm alert.
     *
     * @param alertId identifier of the alert.
     */
    suspend fun deleteAlert(alertId: String): Outcome<Unit>
}

/** Shares of analyses. */
interface ShareRepository {
    /**
     * Creates a share (link and card image).
     *
     * @param analysisId shared analysis.
     * @param options card options.
     */
    suspend fun createShare(analysisId: String, options: ShareOptions): Outcome<Share>

    /** Shares of the user, newest first. */
    suspend fun getShares(): Outcome<List<Share>>

    /**
     * Deletes a share: the public link stops working.
     *
     * @param shareId identifier of the share.
     */
    suspend fun deleteShare(shareId: String): Outcome<Unit>
}

/** Account and consents. */
interface AccountRepository {
    /** The current account. */
    suspend fun getAccount(): Outcome<Account>

    /**
     * Updates the consents.
     *
     * @param consents new consents.
     */
    suspend fun updateConsents(consents: Consents): Outcome<Account>

    /** Deletes the account and its data. */
    suspend fun deleteAccount(): Outcome<Unit>
}

/** Jauge Plus subscription (store purchases and free quota). */
interface SubscriptionRepository {
    /** Current subscription. */
    suspend fun getSubscription(): Outcome<Subscription>

    /** The monthly offer with its store price. */
    suspend fun getOffer(): Outcome<SubscriptionOffer>

    /**
     * Buys the offer (starts the free trial).
     *
     * @param offer the offer shown on the paywall.
     */
    suspend fun purchase(offer: SubscriptionOffer): Outcome<Subscription>

    /** Restores previous purchases. */
    suspend fun restorePurchases(): Outcome<Subscription>

    /**
     * Store page where the user manages or cancels Jauge Plus ("Gérer l'abonnement", mockup 13): the page given by
     * the store for this subscription, or the subscriptions page of the store of the phone.
     */
    suspend fun getManagementUrl(): Outcome<String> = Outcome.Failure(JaugeError.PurchaseUnavailable)
}
