package io.jauge.feature.paywall.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.common.link.WebLinks
import io.jauge.core.common.platform.Platform
import io.jauge.core.designsystem.component.JaugeAnimation
import io.jauge.core.designsystem.component.JaugeButton
import io.jauge.core.designsystem.component.JaugeButtonStyle
import io.jauge.core.designsystem.component.JaugeLottieAnimation
import io.jauge.core.designsystem.component.JaugeRefreshContainer
import io.jauge.core.designsystem.component.RoundIconButton
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.SubscriptionOffer
import io.jauge.core.domain.model.SubscriptionStatus
import io.jauge.core.domain.model.TrialTimeline
import io.jauge.core.ui.error.FriendlyAction
import io.jauge.core.ui.error.toFriendlyMessage
import io.jauge.feature.paywall.resources.Res
import io.jauge.feature.paywall.resources.paywall_already_plus_active
import io.jauge.feature.paywall.resources.paywall_already_plus_ends
import io.jauge.feature.paywall.resources.paywall_already_plus_renews
import io.jauge.feature.paywall.resources.paywall_already_plus_title
import io.jauge.feature.paywall.resources.paywall_already_plus_trial
import io.jauge.feature.paywall.resources.paywall_benefit_alerts_rest
import io.jauge.feature.paywall.resources.paywall_benefit_alerts_strong
import io.jauge.feature.paywall.resources.paywall_benefit_cards_rest
import io.jauge.feature.paywall.resources.paywall_benefit_cards_strong
import io.jauge.feature.paywall.resources.paywall_benefit_salary_rest
import io.jauge.feature.paywall.resources.paywall_benefit_salary_strong
import io.jauge.feature.paywall.resources.paywall_benefit_unlimited_rest
import io.jauge.feature.paywall.resources.paywall_benefit_unlimited_strong
import io.jauge.feature.paywall.resources.paywall_close
import io.jauge.feature.paywall.resources.paywall_continue
import io.jauge.feature.paywall.resources.paywall_error_dismiss
import io.jauge.feature.paywall.resources.paywall_headline_first_line
import io.jauge.feature.paywall.resources.paywall_headline_second_line
import io.jauge.feature.paywall.resources.paywall_headline_third_line
import io.jauge.feature.paywall.resources.paywall_legal_no_trial
import io.jauge.feature.paywall.resources.paywall_legal_trial
import io.jauge.feature.paywall.resources.paywall_manage_subscription
import io.jauge.feature.paywall.resources.paywall_offer_loading
import io.jauge.feature.paywall.resources.paywall_plan_monthly
import io.jauge.feature.paywall.resources.paywall_plan_no_trial_details
import io.jauge.feature.paywall.resources.paywall_plan_per_month
import io.jauge.feature.paywall.resources.paywall_plan_trial_details
import io.jauge.feature.paywall.resources.paywall_privacy
import io.jauge.feature.paywall.resources.paywall_quota_used
import io.jauge.feature.paywall.resources.paywall_restore
import io.jauge.feature.paywall.resources.paywall_restored_message
import io.jauge.feature.paywall.resources.paywall_restored_title
import io.jauge.feature.paywall.resources.paywall_short_date
import io.jauge.feature.paywall.resources.paywall_short_months
import io.jauge.feature.paywall.resources.paywall_start_trial
import io.jauge.feature.paywall.resources.paywall_store_apple
import io.jauge.feature.paywall.resources.paywall_store_google
import io.jauge.feature.paywall.resources.paywall_store_settings_apple
import io.jauge.feature.paywall.resources.paywall_store_settings_google
import io.jauge.feature.paywall.resources.paywall_subscribe
import io.jauge.feature.paywall.resources.paywall_success_message
import io.jauge.feature.paywall.resources.paywall_success_title
import io.jauge.feature.paywall.resources.paywall_success_trial_message
import io.jauge.feature.paywall.resources.paywall_terms
import io.jauge.feature.paywall.resources.paywall_timeline_payment_details
import io.jauge.feature.paywall.resources.paywall_timeline_today
import io.jauge.feature.paywall.resources.paywall_timeline_today_details
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Callbacks of the paywall.
 *
 * @property onClose closes the paywall without buying.
 * @property onUnlocked Jauge Plus was unlocked (purchase or restore) and the user tapped "Continuer".
 * @property onOpenTerms opens the terms of use.
 * @property onOpenPrivacy opens the privacy policy.
 * @property onSignIn opens the account screen (purchase linked to another account).
 */
data class PaywallActions(
    val onClose: () -> Unit,
    val onUnlocked: () -> Unit,
    val onOpenTerms: () -> Unit,
    val onOpenPrivacy: () -> Unit,
    val onSignIn: () -> Unit,
)

/**
 * Jauge Plus paywall (mockup 08, adapted: a single monthly offer at the store price with a free trial). Dark screen.
 *
 * @param showsQuotaReached true when opened because the free analysis of the week is used (shows the lock pill).
 * @param actions navigation callbacks.
 */
@Composable
fun PaywallScreen(showsQuotaReached: Boolean, actions: PaywallActions) {
    val viewModel: PaywallViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(state.managementUrl) {
        state.managementUrl?.let { url ->
            WebLinks.safeWebLinkOrNull(url)?.let { runCatching { uriHandler.openUri(it) } }
            viewModel.onManagementUrlOpened()
        }
    }
    PaywallContent(
        state = state,
        showsQuotaReached = showsQuotaReached,
        actions = actions,
        onRefresh = viewModel::refresh,
        onPurchase = viewModel::purchase,
        onRestore = viewModel::restore,
        onManageSubscription = viewModel::manageSubscription,
        onDismissError = viewModel::dismissError,
    )
}

/**
 * Stateless paywall, shown by [PaywallScreen] and by the screenshot tests.
 *
 * @param state observed state.
 * @param showsQuotaReached true to show the "analyse gratuite utilisée" pill.
 * @param actions navigation callbacks.
 * @param onRefresh reloads the offer.
 * @param onPurchase starts the purchase.
 * @param onRestore restores purchases.
 * @param onManageSubscription opens the store page of the subscription.
 * @param onDismissError closes the error dialog.
 */
@Composable
fun PaywallContent(
    state: PaywallUiState,
    showsQuotaReached: Boolean,
    actions: PaywallActions,
    onRefresh: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onManageSubscription: () -> Unit,
    onDismissError: () -> Unit,
) {
    JaugeTheme(darkTheme = true) {
        Box(Modifier.fillMaxSize().background(JaugeTheme.colors.background)) {
            val completion = state.completion
            if (completion != null) {
                val trialDays = state.offer.content?.trialDays ?: 0
                UnlockedContent(
                    title = if (completion == PaywallCompletion.RESTORED) Res.string.paywall_restored_title else Res.string.paywall_success_title,
                    message = when {
                        completion == PaywallCompletion.RESTORED -> stringResource(Res.string.paywall_restored_message)
                        state.subscription?.status == SubscriptionStatus.TRIAL && trialDays > 0 ->
                            stringResource(Res.string.paywall_success_trial_message, trialDays)
                        else -> stringResource(Res.string.paywall_success_message)
                    },
                    onContinue = actions.onUnlocked,
                )
            } else {
                OfferContent(state, showsQuotaReached, actions, onRefresh, onPurchase, onRestore, onManageSubscription)
            }
            state.dialogError?.let { error ->
                ErrorDialog(
                    error = error,
                    onDismiss = onDismissError,
                    onAction = { action ->
                        onDismissError()
                        when (action) {
                            FriendlyAction.RETRY -> onPurchase()
                            FriendlyAction.RESTORE_PURCHASES -> onRestore()
                            FriendlyAction.SIGN_IN -> actions.onSignIn()
                            else -> Unit
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun OfferContent(
    state: PaywallUiState,
    showsQuotaReached: Boolean,
    actions: PaywallActions,
    onRefresh: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onManageSubscription: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopRow(isRestoring = state.isRestoring, onClose = actions.onClose, onRestore = onRestore)
        JaugeRefreshContainer(
            isRefreshing = state.offer.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = JaugeTheme.spacing.screenGutter)
                    .padding(top = 4.dp, bottom = 18.dp),
            ) {
                if (showsQuotaReached && !state.isAlreadyPlus) {
                    QuotaPill()
                    Spacer(Modifier.height(14.dp))
                }
                Headline()
                Spacer(Modifier.height(20.dp))
                Benefits()
                Spacer(Modifier.height(18.dp))
                val offer = state.offer.content
                val error = state.offer.error
                when {
                    state.isAlreadyPlus -> AlreadyPlusCard(state, onManageSubscription)
                    offer != null -> {
                        PlanCard(offer)
                        state.timeline?.let { timeline ->
                            Spacer(Modifier.height(20.dp))
                            TrialTimelineRow(timeline, offer.formattedPrice)
                        }
                    }
                    state.offer.isLoading -> OfferLoading()
                    error != null -> OfferError(error, onRetry = onRefresh)
                }
            }
        }
        BottomArea(state, actions, onPurchase)
    }
}

@Composable
private fun TopRow(isRestoring: Boolean, onClose: () -> Unit, onRestore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = JaugeTheme.spacing.screenGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(icon = JaugeIcons.Close, contentDescription = stringResource(Res.string.paywall_close), onClick = onClose)
        Spacer(Modifier.weight(1f))
        if (isRestoring) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = JaugeTheme.colors.textPrimary, strokeWidth = 2.dp)
        } else {
            Text(
                text = stringResource(Res.string.paywall_restore),
                style = JaugeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = JaugeTheme.colors.textPrimary,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onRestore)
                    .padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun QuotaPill() {
    val colors = JaugeTheme.colors
    Row(
        modifier = Modifier
            .heightIn(min = 26.dp)
            .background(colors.surface, JaugeTheme.radii.pillShape)
            .border(1.dp, colors.outline, JaugeTheme.radii.pillShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(JaugeIcons.Lock, contentDescription = null, tint = colors.calm.accent, modifier = Modifier.size(14.dp))
        Text(stringResource(Res.string.paywall_quota_used), style = JaugeTheme.typography.pill, color = colors.calm.accent)
    }
}

@Composable
private fun Headline() {
    val style = JaugeTheme.typography.headline.copy(fontSize = 32.sp, lineHeight = 34.sp, letterSpacing = (-0.031).em)
    Text(
        text = buildAnnotatedString {
            append(stringResource(Res.string.paywall_headline_first_line))
            append("\n")
            withStyle(SpanStyle(color = JaugeTheme.colors.action)) {
                append(stringResource(Res.string.paywall_headline_second_line))
                append("\n")
                append(stringResource(Res.string.paywall_headline_third_line))
            }
        },
        style = style,
        color = JaugeTheme.colors.textPrimary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun Benefits() {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Benefit(Res.string.paywall_benefit_unlimited_strong, Res.string.paywall_benefit_unlimited_rest)
        Benefit(Res.string.paywall_benefit_salary_strong, Res.string.paywall_benefit_salary_rest)
        Benefit(Res.string.paywall_benefit_alerts_strong, Res.string.paywall_benefit_alerts_rest)
        Benefit(Res.string.paywall_benefit_cards_strong, Res.string.paywall_benefit_cards_rest)
    }
}

@Composable
private fun Benefit(strong: StringResource, rest: StringResource) {
    val colors = JaugeTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(JaugeIcons.Check, contentDescription = null, tint = colors.calm.accent, modifier = Modifier.padding(top = 1.dp).size(20.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.Bold)) { append(stringResource(strong)) }
                append(" ")
                append(stringResource(rest))
            },
            style = JaugeTheme.typography.body.copy(fontSize = 15.5.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun PlanCard(offer: SubscriptionOffer) {
    val colors = JaugeTheme.colors
    val shape = RoundedCornerShape(JaugeTheme.radii.button)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.crowded.container, shape)
            .border(2.dp, colors.action, shape)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).border(7.dp, colors.action, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.paywall_plan_monthly), style = JaugeTheme.typography.bodyStrong.copy(fontSize = 16.5.sp), color = colors.textPrimary)
            Text(
                text = if (offer.trialDays > 0) {
                    stringResource(Res.string.paywall_plan_trial_details, offer.trialDays, offer.formattedPrice)
                } else {
                    stringResource(Res.string.paywall_plan_no_trial_details)
                },
                style = JaugeTheme.typography.caption.copy(fontSize = 12.5.sp),
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(offer.formattedPrice, style = JaugeTheme.typography.numberSmall.copy(fontSize = 16.5.sp, lineHeight = 20.sp), color = colors.textPrimary)
            Text(
                stringResource(Res.string.paywall_plan_per_month),
                style = JaugeTheme.typography.caption.copy(fontSize = 12.5.sp),
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun TrialTimelineRow(timeline: TrialTimeline, formattedPrice: String) {
    val colors = JaugeTheme.colors
    val steps = listOf(
        Triple(colors.calm.accent, stringResource(Res.string.paywall_timeline_today), stringResource(Res.string.paywall_timeline_today_details)),
        Triple(colors.textPrimary, shortDate(timeline.firstPaymentOn), stringResource(Res.string.paywall_timeline_payment_details, formattedPrice)),
    )
    Row(Modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, (dotColor, title, details) ->
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(11.dp).background(dotColor, CircleShape))
                    if (index < steps.lastIndex) {
                        Box(Modifier.weight(1f).padding(horizontal = 6.dp).height(2.dp).background(colors.outline))
                    }
                }
                Text(
                    title,
                    style = JaugeTheme.typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    details,
                    style = JaugeTheme.typography.caption.copy(fontSize = 12.5.sp),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp, end = 6.dp),
                )
            }
        }
    }
}

/**
 * Short date of the timeline ("25 oct." in French, "Oct 25" in English).
 *
 * @param date the date.
 */
@Composable
internal fun shortDate(date: LocalDate): String {
    val months = stringArrayResource(Res.array.paywall_short_months)
    val month = months.getOrNull(date.month.ordinal).orEmpty()
    return stringResource(Res.string.paywall_short_date, date.day, month)
}

@Composable
private fun OfferLoading() {
    val colors = JaugeTheme.colors
    val shape = RoundedCornerShape(JaugeTheme.radii.button)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.5.dp, colors.outline, shape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JaugeLottieAnimation(animation = JaugeAnimation.LOADING, size = 56.dp)
        Text(stringResource(Res.string.paywall_offer_loading), style = JaugeTheme.typography.body, color = colors.textSecondary)
    }
}

@Composable
private fun OfferError(error: JaugeError, onRetry: () -> Unit) {
    val colors = JaugeTheme.colors
    val message = error.toFriendlyMessage()
    val shape = RoundedCornerShape(JaugeTheme.radii.button)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.5.dp, colors.outline, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(message.title), style = JaugeTheme.typography.bodyStrong, color = colors.textPrimary)
        Text(stringResource(message.message), style = JaugeTheme.typography.bodySmall, color = colors.textSecondary)
        val actionLabel = message.actionLabel
        if (message.action == FriendlyAction.RETRY && actionLabel != null) {
            Text(
                text = stringResource(actionLabel),
                style = JaugeTheme.typography.label,
                color = colors.action,
                modifier = Modifier.clickable(role = Role.Button, onClick = onRetry).padding(top = 6.dp, bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun AlreadyPlusCard(state: PaywallUiState, onManageSubscription: () -> Unit) {
    val colors = JaugeTheme.colors
    val subscription = state.subscription ?: return
    val shape = RoundedCornerShape(JaugeTheme.radii.button)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.calm.container, shape)
            .border(1.5.dp, colors.calm.accent, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(Res.string.paywall_already_plus_title),
            style = JaugeTheme.typography.bodyStrong.copy(fontSize = 16.5.sp),
            color = colors.textPrimary,
        )
        val trialEnd = subscription.trialEndsOn
        val renewal = subscription.renewsOn
        Text(
            text = when {
                subscription.status == SubscriptionStatus.TRIAL && trialEnd != null ->
                    stringResource(Res.string.paywall_already_plus_trial, shortDate(trialEnd))
                !subscription.willRenew && renewal != null -> stringResource(Res.string.paywall_already_plus_ends, shortDate(renewal))
                renewal != null -> stringResource(Res.string.paywall_already_plus_renews, shortDate(renewal))
                else -> stringResource(Res.string.paywall_already_plus_active)
            },
            style = JaugeTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        Text(
            text = stringResource(Res.string.paywall_manage_subscription),
            style = JaugeTheme.typography.label,
            color = colors.calm.accent,
            modifier = Modifier.clickable(role = Role.Button, onClick = onManageSubscription).padding(top = 6.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun BottomArea(state: PaywallUiState, actions: PaywallActions, onPurchase: () -> Unit) {
    val offer = state.offer.content
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = JaugeTheme.spacing.screenGutter)
            .padding(top = 6.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.isAlreadyPlus) {
            JaugeButton(text = stringResource(Res.string.paywall_continue), onClick = actions.onUnlocked, modifier = Modifier.fillMaxWidth())
        } else {
            val trialDays = offer?.trialDays ?: SubscriptionOffer.TRIAL_DAYS
            JaugeButton(
                text = if (offer == null || trialDays > 0) {
                    stringResource(Res.string.paywall_start_trial, trialDays)
                } else {
                    stringResource(Res.string.paywall_subscribe, offer.formattedPrice)
                },
                onClick = onPurchase,
                modifier = Modifier.fillMaxWidth(),
                style = JaugeButtonStyle.PRIMARY,
                enabled = state.canPurchase,
                isLoading = state.isPurchasing,
                shine = true,
            )
            if (offer != null) {
                LegalText(offer, state.platform)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(top = 8.dp)) {
            LegalLink(Res.string.paywall_terms, actions.onOpenTerms)
            LegalLink(Res.string.paywall_privacy, actions.onOpenPrivacy)
        }
    }
}

/**
 * Auto renewal terms required by the App Store and Google Play: price, trial length, when the payment is taken, how
 * to cancel. No reminder is promised before the end of the trial.
 */
@Composable
private fun LegalText(offer: SubscriptionOffer, platform: Platform) {
    val isApple = platform == Platform.IOS
    val store = stringResource(if (isApple) Res.string.paywall_store_apple else Res.string.paywall_store_google)
    val settings = stringResource(if (isApple) Res.string.paywall_store_settings_apple else Res.string.paywall_store_settings_google)
    Text(
        text = if (offer.trialDays > 0) {
            stringResource(Res.string.paywall_legal_trial, offer.trialDays, offer.formattedPrice, store, settings)
        } else {
            stringResource(Res.string.paywall_legal_no_trial, offer.formattedPrice, store, settings)
        },
        style = JaugeTheme.typography.caption.copy(fontSize = 11.5.sp, lineHeight = 16.5.sp),
        color = JaugeTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun LegalLink(label: StringResource, onClick: () -> Unit) {
    Text(
        text = stringResource(label),
        style = JaugeTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
        color = JaugeTheme.colors.textPrimary,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
    )
}

@Composable
private fun UnlockedContent(title: StringResource, message: String, onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(JaugeTheme.spacing.screenGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        JaugeLottieAnimation(animation = JaugeAnimation.SUCCESS, size = 150.dp)
        Text(
            text = stringResource(title),
            style = JaugeTheme.typography.headline,
            color = JaugeTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp).semantics { heading() },
        )
        Text(
            text = message,
            style = JaugeTheme.typography.bodyLarge,
            color = JaugeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        Spacer(Modifier.weight(1f))
        JaugeButton(text = stringResource(Res.string.paywall_continue), onClick = onContinue, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ErrorDialog(error: JaugeError, onDismiss: () -> Unit, onAction: (FriendlyAction) -> Unit) {
    val message = error.toFriendlyMessage()
    val colors = JaugeTheme.colors
    val actionLabel = message.actionLabel
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = { Text(stringResource(message.title), style = JaugeTheme.typography.sectionTitle) },
        text = { Text(stringResource(message.message), style = JaugeTheme.typography.body) },
        confirmButton = {
            if (actionLabel != null) {
                TextButton(onClick = { onAction(message.action) }) {
                    Text(stringResource(actionLabel), style = JaugeTheme.typography.label, color = colors.action)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.paywall_error_dismiss), style = JaugeTheme.typography.label, color = colors.action)
                }
            }
        },
        dismissButton = if (actionLabel != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.paywall_error_dismiss), style = JaugeTheme.typography.label, color = colors.textSecondary)
                }
            }
        } else {
            null
        },
    )
}
