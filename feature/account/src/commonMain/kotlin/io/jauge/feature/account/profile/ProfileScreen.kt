package io.jauge.feature.account.profile

import androidx.compose.foundation.background
import io.jauge.core.designsystem.component.JaugeButtonSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.common.link.WebLinks
import io.jauge.core.designsystem.component.JaugeButton
import io.jauge.core.designsystem.component.JaugeButtonStyle
import io.jauge.core.designsystem.component.JaugeCard
import io.jauge.core.designsystem.component.JaugePill
import io.jauge.core.designsystem.component.JaugeRefreshContainer
import io.jauge.core.designsystem.component.JaugeScreenScaffold
import io.jauge.core.designsystem.component.SkeletonCard
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugePalette
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Subscription
import io.jauge.core.domain.model.SignInMethod
import io.jauge.core.domain.model.SubscriptionStatus
import io.jauge.core.ui.error.FriendlyAction
import io.jauge.core.ui.error.FriendlyErrorView
import io.jauge.core.ui.error.toFriendlyMessage
import io.jauge.feature.account.components.SettingsGroupTitle
import io.jauge.feature.account.components.SettingsListCard
import io.jauge.feature.account.components.SettingsRowItem
import io.jauge.feature.account.components.shortDate
import io.jauge.feature.account.platform.rememberTextSharer
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.export_header
import io.jauge.feature.account.resources.export_title
import io.jauge.feature.account.resources.profile_about
import io.jauge.feature.account.resources.profile_active_text
import io.jauge.feature.account.resources.profile_analysis_count
import io.jauge.feature.account.resources.profile_calm_alerts
import io.jauge.feature.account.resources.profile_calm_alerts_off
import io.jauge.feature.account.resources.profile_calm_alerts_on
import io.jauge.feature.account.resources.profile_cancel
import io.jauge.feature.account.resources.profile_create_account
import io.jauge.feature.account.resources.profile_keep_message
import io.jauge.feature.account.resources.profile_keep_message_without_count
import io.jauge.feature.account.resources.profile_keep_title
import io.jauge.feature.account.resources.profile_delete_account
import io.jauge.feature.account.resources.profile_discover_plus
import io.jauge.feature.account.resources.profile_ending_text
import io.jauge.feature.account.resources.profile_expired_text
import io.jauge.feature.account.resources.profile_export
import io.jauge.feature.account.resources.profile_export_empty
import io.jauge.feature.account.resources.profile_free_text
import io.jauge.feature.account.resources.profile_group_about
import io.jauge.feature.account.resources.profile_group_data
import io.jauge.feature.account.resources.profile_group_settings
import io.jauge.feature.account.resources.profile_legal_notice
import io.jauge.feature.account.resources.profile_manage_subscription
import io.jauge.feature.account.resources.profile_methodology
import io.jauge.feature.account.resources.profile_no_account_name
import io.jauge.feature.account.resources.profile_plus_name
import io.jauge.feature.account.resources.profile_plus_no_commitment
import io.jauge.feature.account.resources.profile_plus_resume
import io.jauge.feature.account.resources.profile_plus_start_trial
import io.jauge.feature.account.resources.profile_plus_price
import io.jauge.feature.account.resources.profile_plus_then_price
import io.jauge.feature.account.resources.profile_plus_trial_badge
import io.jauge.feature.account.resources.profile_plus_tagline
import io.jauge.feature.account.resources.profile_privacy
import io.jauge.feature.account.resources.profile_provider_apple
import io.jauge.feature.account.resources.profile_provider_email
import io.jauge.feature.account.resources.profile_provider_google
import io.jauge.feature.account.resources.profile_restore
import io.jauge.feature.account.resources.profile_restored
import io.jauge.feature.account.resources.profile_restoring
import io.jauge.feature.account.resources.profile_search_area
import io.jauge.feature.account.resources.profile_search_area_none
import io.jauge.feature.account.resources.profile_search_area_value
import io.jauge.feature.account.resources.profile_sign_out
import io.jauge.feature.account.resources.profile_sign_out_confirm
import io.jauge.feature.account.resources.profile_sign_out_message
import io.jauge.feature.account.resources.profile_sign_out_title
import io.jauge.feature.account.resources.profile_signed_in_with
import io.jauge.feature.account.resources.profile_signed_out
import io.jauge.feature.account.resources.profile_status_active
import io.jauge.feature.account.resources.profile_status_expired
import io.jauge.feature.account.resources.profile_status_free
import io.jauge.feature.account.resources.profile_status_trial
import io.jauge.feature.account.resources.profile_terms
import io.jauge.feature.account.resources.profile_trial_text
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.launch

/** Price shown while the store has not answered, from the offer decided by the user. */
private const val DEFAULT_MONTHLY_PRICE = "6,99 €"

/**
 * Destinations opened from the profile.
 *
 * @property onOpenAccount sign in (mockup 11).
 * @property onOpenPaywall Jauge Plus (mockup 08).
 * @property onOpenSettings settings (alerts, search area).
 * @property onOpenMethodology "Comment on estime" for an analysis.
 * @property onOpenPrivacyPolicy privacy policy.
 * @property onOpenTerms terms of use.
 * @property onOpenLegalNotice legal notice.
 * @property onOpenAbout about.
 * @property onOpenDeleteAccount account deletion.
 */
class ProfileNavigation(
    val onOpenAccount: () -> Unit,
    val onOpenPaywall: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenMethodology: (analysisId: String) -> Unit,
    val onOpenPrivacyPolicy: () -> Unit,
    val onOpenTerms: () -> Unit,
    val onOpenLegalNotice: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onOpenDeleteAccount: () -> Unit,
)

/**
 * Profile and subscription (mockup 13), tab root: who is signed in, Jauge Plus, settings, data and legal pages.
 *
 * @param navigation destinations opened from the profile.
 */
@Composable
fun ProfileScreen(navigation: ProfileNavigation) {
    val viewModel: ProfileViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val share = rememberTextSharer()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var isSignOutDialogShown by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.managementUrl) {
        state.managementUrl?.let { url ->
            viewModel.onUrlOpened()
            WebLinks.safeWebLinkOrNull(url)?.let { runCatching { uriHandler.openUri(it) } }
        }
    }
    LaunchedEffect(state.exportedAnalyses) {
        state.exportedAnalyses?.let { analyses ->
            viewModel.onExportShared()
            share(getString(Res.string.export_title), analysesToCsv(getString(Res.string.export_header), analyses))
        }
    }
    LaunchedEffect(state.message, state.actionError) {
        val error = state.actionError
        val text = when {
            error == JaugeError.NothingToRestore -> getString(error.toFriendlyMessage().title)
            error != null -> getString(error.toFriendlyMessage().message)
            state.message == ProfileMessage.PURCHASES_RESTORED -> getString(Res.string.profile_restored)
            state.message == ProfileMessage.SIGNED_OUT -> getString(Res.string.profile_signed_out)
            state.message == ProfileMessage.NOTHING_TO_EXPORT -> getString(Res.string.profile_export_empty)
            else -> null
        }
        if (text != null) {
            // Shown in a scope that outlives this effect: dismissing the message changes the keys and would
            // cancel the snackbar before it is visible.
            snackbarScope.launch { snackbarHostState.showSnackbar(text) }
            viewModel.dismissMessage()
        }
    }
    if (isSignOutDialogShown) {
        AlertDialog(
            onDismissRequest = { isSignOutDialogShown = false },
            title = { Text(stringResource(Res.string.profile_sign_out_title)) },
            text = { Text(stringResource(Res.string.profile_sign_out_message)) },
            confirmButton = {
                TextButton(onClick = {
                    isSignOutDialogShown = false
                    viewModel.confirmSignOut()
                }) { Text(stringResource(Res.string.profile_sign_out_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { isSignOutDialogShown = false }) { Text(stringResource(Res.string.profile_cancel)) }
            },
        )
    }
    JaugeScreenScaffold(showTopBar = false, snackbarHostState = snackbarHostState) {
        ProfileContent(
            state = state,
            navigation = navigation,
            onRefresh = viewModel::refresh,
            onManageSubscription = viewModel::manageSubscription,
            onRestore = viewModel::restore,
            onExport = viewModel::export,
            onSignOut = { isSignOutDialogShown = true },
        )
    }
}

/**
 * Stateless content of the profile, also used by the screenshot tests.
 *
 * @param state state to draw.
 * @param navigation destinations opened from the profile.
 * @param onRefresh pull to refresh and "Réessayer".
 * @param onManageSubscription "Gérer ou annuler l'abonnement".
 * @param onRestore "Restaurer les achats".
 * @param onExport "Exporter mes analyses".
 * @param onSignOut "Se déconnecter".
 * @param modifier modifier of the content.
 */
@Composable
fun ProfileContent(
    state: ProfileUiState,
    navigation: ProfileNavigation,
    onRefresh: () -> Unit,
    onManageSubscription: () -> Unit,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JaugeTheme.colors
    val content = state.content.content
    JaugeRefreshContainer(isRefreshing = state.content.isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = JaugeTheme.spacing.screenGutter)
                .padding(top = 12.dp, bottom = 24.dp),
        ) {
            ProfileHeader(state = state)
            if (state.user == null) {
                CreateAccountCard(
                    analysisCount = content?.account?.analysisCount,
                    onCreateAccount = navigation.onOpenAccount,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            when {
                state.content.isLoading -> SkeletonCard(modifier = Modifier.padding(top = 14.dp), height = 118.dp)
                state.content.showsFullScreenError -> FriendlyErrorView(
                    error = state.content.error!!,
                    onAction = { action -> if (action == FriendlyAction.SIGN_IN) navigation.onOpenAccount() else onRefresh() },
                )
                else -> SubscriptionCard(
                    subscription = content?.subscription,
                    price = content?.formattedPrice ?: DEFAULT_MONTHLY_PRICE,
                    onManage = onManageSubscription,
                    onDiscover = navigation.onOpenPaywall,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            SettingsGroupTitle(stringResource(Res.string.profile_group_settings))
            val area = state.preferences.searchArea
            val settingsRows = buildList {
                add(
                    SettingsRowItem(
                        icon = JaugeIcons.Bell,
                        label = stringResource(Res.string.profile_calm_alerts),
                        value = stringResource(if (state.preferences.calmAlertsEnabled) Res.string.profile_calm_alerts_on else Res.string.profile_calm_alerts_off),
                        onClick = navigation.onOpenSettings,
                    ),
                )
                add(
                    SettingsRowItem(
                        icon = JaugeIcons.Pin,
                        label = stringResource(Res.string.profile_search_area),
                        value = area?.let { stringResource(Res.string.profile_search_area_value, it.city, it.radiusKilometers) }
                            ?: stringResource(Res.string.profile_search_area_none),
                        onClick = navigation.onOpenSettings,
                    ),
                )
                content?.latestAnalysisId?.let { analysisId ->
                    add(SettingsRowItem(JaugeIcons.Info, stringResource(Res.string.profile_methodology)) { navigation.onOpenMethodology(analysisId) })
                }
                add(
                    SettingsRowItem(
                        icon = JaugeIcons.Document,
                        label = stringResource(Res.string.profile_restore),
                        value = if (state.isRestoring) stringResource(Res.string.profile_restoring) else null,
                        onClick = { if (!state.isRestoring) onRestore() },
                    ),
                )
            }
            SettingsListCard(settingsRows)
            SettingsGroupTitle(stringResource(Res.string.profile_group_data))
            SettingsListCard(
                buildList {
                    add(SettingsRowItem(JaugeIcons.Shield, stringResource(Res.string.profile_privacy), onClick = navigation.onOpenPrivacyPolicy))
                    add(SettingsRowItem(JaugeIcons.Download, stringResource(Res.string.profile_export), onClick = onExport))
                    if (state.user != null) add(SettingsRowItem(JaugeIcons.SignOut, stringResource(Res.string.profile_sign_out), onClick = onSignOut))
                    add(
                        SettingsRowItem(
                            JaugeIcons.Trash,
                            stringResource(Res.string.profile_delete_account),
                            isDestructive = true,
                            onClick = navigation.onOpenDeleteAccount,
                        ),
                    )
                },
            )
            SettingsGroupTitle(stringResource(Res.string.profile_group_about))
            SettingsListCard(
                listOf(
                    SettingsRowItem(JaugeIcons.Document, stringResource(Res.string.profile_terms), onClick = navigation.onOpenTerms),
                    SettingsRowItem(JaugeIcons.Info, stringResource(Res.string.profile_legal_notice), onClick = navigation.onOpenLegalNotice),
                    SettingsRowItem(JaugeIcons.Gauge, stringResource(Res.string.profile_about), onClick = navigation.onOpenAbout),
                ),
            )
            Box(Modifier.height(8.dp).background(colors.background))
        }
    }
}

@Composable
private fun ProfileHeader(state: ProfileUiState) {
    val colors = JaugeTheme.colors
    val user = state.user
    val name = user?.displayName?.takeIf { it.isNotBlank() } ?: user?.email ?: stringResource(Res.string.profile_no_account_name)
    val initials = name.split(' ', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    val analysisCount = state.content.content?.account?.analysisCount
    val provider = when (user?.signInMethod) {
        SignInMethod.APPLE -> stringResource(Res.string.profile_provider_apple)
        SignInMethod.GOOGLE -> stringResource(Res.string.profile_provider_google)
        SignInMethod.EMAIL -> stringResource(Res.string.profile_provider_email)
        else -> null
    }
    val subtitle = listOfNotNull(
        provider?.let { stringResource(Res.string.profile_signed_in_with, it) },
        analysisCount?.let { pluralStringResource(Res.plurals.profile_analysis_count, it, it) },
    ).joinToString(" · ")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.size(52.dp).background(if (user != null) JaugePalette.Amber else colors.backgroundAlternative, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (user != null && initials.isNotEmpty()) {
                Text(text = initials, style = JaugeTheme.typography.title.copy(fontSize = 19.sp, letterSpacing = 0.em), color = JaugePalette.Ink)
            } else {
                Icon(JaugeIcons.Profile, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(24.dp))
            }
        }
        Column {
            Text(
                text = name,
                style = JaugeTheme.typography.title.copy(fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.02).em),
                color = colors.textPrimary,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = JaugeTheme.typography.label.copy(fontWeight = FontWeight.Normal),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

/**
 * Invitation to create an account for a user without one: cloud icon, the reason ("Retrouve tes 12 offres jaugées sur
 * tous tes appareils") and a filled ink (light theme) or cream (dark theme) button, visible without competing with the
 * coral Jauge Plus offer below.
 */
@Composable
private fun CreateAccountCard(analysisCount: Int?, onCreateAccount: () -> Unit, modifier: Modifier = Modifier) {
    val colors = JaugeTheme.colors
    JaugeCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).background(colors.calm.container, JaugeTheme.radii.cardShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(JaugeIcons.CloudSync, contentDescription = null, tint = colors.calm.content, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.profile_keep_title),
                    style = JaugeTheme.typography.bodyStrong,
                    color = colors.textPrimary,
                )
                Text(
                    text = if (analysisCount != null && analysisCount > 0) {
                        pluralStringResource(Res.plurals.profile_keep_message, analysisCount, analysisCount)
                    } else {
                        stringResource(Res.string.profile_keep_message_without_count)
                    },
                    style = JaugeTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        JaugeButton(
            text = stringResource(Res.string.profile_create_account),
            onClick = onCreateAccount,
            style = JaugeButtonStyle.NEUTRAL,
            size = JaugeButtonSize.MEDIUM,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}

@Composable
private fun SubscriptionCard(
    subscription: Subscription?,
    price: String,
    onManage: () -> Unit,
    onDiscover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = JaugeTheme.radii.cardShape
    val status = subscription?.status
    val isPlus = subscription?.isPlus == true
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(JaugePalette.Ink, shape)
            // The ink card sits on an ink background in the dark theme: a thin outline keeps its edges visible.
            .then(if (JaugeTheme.colors.isDark) Modifier.border(1.dp, JaugePalette.Ink3, shape) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.profile_plus_name),
                style = JaugeTheme.typography.sectionTitle.copy(fontSize = 17.sp),
                color = JaugePalette.Paper,
                modifier = Modifier.weight(1f),
            )
            if (status != null) {
                JaugePill(
                    text = stringResource(
                        when (status) {
                            SubscriptionStatus.TRIAL -> Res.string.profile_status_trial
                            SubscriptionStatus.ACTIVE -> Res.string.profile_status_active
                            SubscriptionStatus.FREE -> Res.string.profile_status_free
                            SubscriptionStatus.EXPIRED -> Res.string.profile_status_expired
                        },
                    ),
                    containerColor = if (isPlus) JaugePalette.Mint.copy(alpha = 0.16f) else JaugePalette.Ink2,
                    contentColor = if (isPlus) JaugePalette.Mint else JaugePalette.Sage2,
                )
            }
        }
        val description = subscriptionText(subscription, price)
        if (description != null) {
            Text(
                text = description,
                style = JaugeTheme.typography.label.copy(fontWeight = FontWeight.Normal, lineHeight = 18.sp),
                color = JaugePalette.Sage2,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (isPlus) {
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(1.dp).background(JaugePalette.Ink3))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onManage)
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.profile_manage_subscription),
                    style = JaugeTheme.typography.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
                    color = JaugePalette.Paper,
                    modifier = Modifier.weight(1f),
                )
                Icon(JaugeIcons.ChevronRight, contentDescription = null, tint = JaugePalette.Sage2, modifier = Modifier.size(18.dp))
            }
        } else {
            PlusOffer(
                price = price,
                offersTrial = status != SubscriptionStatus.EXPIRED,
                onDiscover = onDiscover,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

/**
 * Highlighted Jauge Plus offer of the profile for users without Plus: coral star, promise, "3 jours offerts" and
 * the monthly price, and a coral call to action that opens the paywall.
 *
 * @param price monthly price formatted by the store.
 * @param offersTrial true to promise the free trial (not after an ended subscription).
 * @param onDiscover opens the paywall.
 * @param modifier modifier of the offer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlusOffer(price: String, offersTrial: Boolean, onDiscover: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(JaugePalette.Coral.copy(alpha = 0.24f), JaugePalette.Amber.copy(alpha = 0.06f)),
                ),
            )
            .border(1.dp, JaugePalette.Coral.copy(alpha = 0.45f), shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).background(JaugePalette.Coral, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(JaugeIcons.StarFilled, contentDescription = null, tint = JaugePalette.White, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = stringResource(Res.string.profile_discover_plus),
                    style = JaugeTheme.typography.sectionTitle.copy(fontSize = 16.5.sp),
                    color = JaugePalette.Paper,
                )
                Text(
                    text = stringResource(Res.string.profile_plus_tagline),
                    style = JaugeTheme.typography.label.copy(fontWeight = FontWeight.Normal, lineHeight = 18.sp),
                    color = JaugePalette.Sage2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        FlowRow(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (offersTrial) {
                JaugePill(
                    text = stringResource(Res.string.profile_plus_trial_badge),
                    containerColor = JaugePalette.Coral,
                    contentColor = JaugePalette.White,
                )
            }
            Text(
                text = stringResource(if (offersTrial) Res.string.profile_plus_then_price else Res.string.profile_plus_price, price),
                style = JaugeTheme.typography.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
                color = JaugePalette.Paper,
            )
        }
        JaugeButton(
            text = stringResource(if (offersTrial) Res.string.profile_plus_start_trial else Res.string.profile_plus_resume),
            onClick = onDiscover,
            size = JaugeButtonSize.MEDIUM,
            trailingIcon = JaugeIcons.ArrowRight,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Text(
            text = stringResource(Res.string.profile_plus_no_commitment),
            style = JaugeTheme.typography.caption,
            color = JaugePalette.Sage2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun subscriptionText(subscription: Subscription?, price: String) = subscription?.let {
    val bold = SpanStyle(color = JaugePalette.Paper, fontWeight = FontWeight.Bold)
    fun highlighted(template: String, date: String) = buildAnnotatedString {
        val start = template.indexOf(date)
        if (start < 0) {
            append(template)
        } else {
            append(template.substring(0, start))
            pushStyle(bold)
            append(date)
            pop()
            append(template.substring(start + date.length))
        }
    }
    when (it.status) {
        SubscriptionStatus.TRIAL -> it.trialEndsOn?.let { end ->
            val date = shortDate(end)
            highlighted(stringResource(Res.string.profile_trial_text, date, price), date)
        }
        SubscriptionStatus.ACTIVE -> it.renewsOn?.let { renewal ->
            val date = shortDate(renewal)
            if (it.willRenew) {
                highlighted(stringResource(Res.string.profile_active_text, date, price), date)
            } else {
                highlighted(stringResource(Res.string.profile_ending_text, date), date)
            }
        }
        SubscriptionStatus.FREE -> buildAnnotatedString {
            append(pluralStringResource(Res.plurals.profile_free_text, it.freeAnalysesLeftThisWeek, it.freeAnalysesLeftThisWeek))
        }
        SubscriptionStatus.EXPIRED -> buildAnnotatedString { append(stringResource(Res.string.profile_expired_text)) }
    }
}
