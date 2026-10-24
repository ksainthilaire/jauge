package io.jauge.feature.account.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.designsystem.component.JaugeLogo
import io.jauge.core.designsystem.component.JaugeTopBar
import io.jauge.core.designsystem.component.RoundIconButton
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugePalette
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.AuthenticationProvider
import io.jauge.feature.account.components.ConsentCheckBox
import io.jauge.feature.account.components.InlineError
import io.jauge.feature.account.components.ProviderButton
import io.jauge.feature.account.components.ProviderLogos
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.account_back
import io.jauge.feature.account.resources.account_continue_apple
import io.jauge.feature.account.resources.account_continue_email
import io.jauge.feature.account.resources.account_continue_google
import io.jauge.feature.account.resources.account_continue_without
import io.jauge.feature.account.resources.account_headline
import io.jauge.feature.account.resources.account_legal_end
import io.jauge.feature.account.resources.account_legal_middle
import io.jauge.feature.account.resources.account_legal_privacy
import io.jauge.feature.account.resources.account_legal_start
import io.jauge.feature.account.resources.account_legal_terms
import io.jauge.feature.account.resources.account_marketing_consent
import io.jauge.feature.account.resources.account_marketing_optional
import io.jauge.feature.account.resources.account_privacy_promise
import io.jauge.feature.account.resources.account_text
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Account and consents screen (mockup 11), always dark: sign in with Apple, Google or an email code, or continue
 * without account.
 *
 * @param onBack back button.
 * @param onDone called once signed in or after "Continuer sans compte".
 * @param onContinueWithEmail opens the email sign in, with the state of the optional consent.
 * @param onOpenTerms opens the terms of use.
 * @param onOpenPrivacyPolicy opens the privacy policy.
 */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onContinueWithEmail: (marketingEmails: Boolean) -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    val viewModel: AccountViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isDone) { if (state.isDone) onDone() }
    AccountContent(
        state = state,
        onBack = onBack,
        onSignIn = viewModel::signIn,
        onContinueWithEmail = { onContinueWithEmail(state.marketingEmails) },
        onMarketingEmailsChange = viewModel::setMarketingEmails,
        onContinueWithoutAccount = viewModel::continueWithoutAccount,
        onOpenTerms = onOpenTerms,
        onOpenPrivacyPolicy = onOpenPrivacyPolicy,
    )
}

/**
 * Stateless content of the account screen, also used by the screenshot tests.
 *
 * @param state state to draw.
 * @param onBack back button.
 * @param onSignIn Apple or Google button.
 * @param onContinueWithEmail email button.
 * @param onMarketingEmailsChange optional consent box.
 * @param onContinueWithoutAccount "Continuer sans compte".
 * @param onOpenTerms terms of use link.
 * @param onOpenPrivacyPolicy privacy policy link.
 * @param modifier modifier of the screen.
 */
@Composable
fun AccountContent(
    state: AccountUiState,
    onBack: () -> Unit,
    onSignIn: (AuthenticationProvider) -> Unit,
    onContinueWithEmail: () -> Unit,
    onMarketingEmailsChange: (Boolean) -> Unit,
    onContinueWithoutAccount: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JaugeTheme(darkTheme = true) {
        val colors = JaugeTheme.colors
        val typography = JaugeTheme.typography
        val gutter = JaugeTheme.spacing.screenGutter
        Column(modifier = modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
            JaugeTopBar(
                title = null,
                navigationIcon = {
                    RoundIconButton(icon = JaugeIcons.Back, contentDescription = stringResource(Res.string.account_back), onClick = onBack)
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = gutter)
                    .padding(top = 34.dp, bottom = 16.dp),
            ) {
                JaugeLogo(modifier = Modifier.size(60.dp))
                Text(
                    text = stringResource(Res.string.account_headline),
                    style = typography.headline.copy(fontSize = 34.sp, lineHeight = 36.sp),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 22.dp),
                )
                Text(
                    text = stringResource(Res.string.account_text),
                    style = typography.body.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 10.dp),
                )
                state.error?.let { InlineError(error = it, modifier = Modifier.padding(top = 20.dp)) }
                Column(modifier = Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProviderButton(
                        text = stringResource(Res.string.account_continue_apple),
                        icon = ProviderLogos.Apple,
                        onClick = { onSignIn(AuthenticationProvider.APPLE) },
                        containerColor = JaugePalette.White,
                        contentColor = Color.Black,
                        enabled = !state.isBusy,
                        isLoading = state.signingInWith == AuthenticationProvider.APPLE,
                    )
                    ProviderButton(
                        text = stringResource(Res.string.account_continue_google),
                        icon = ProviderLogos.Google,
                        onClick = { onSignIn(AuthenticationProvider.GOOGLE) },
                        containerColor = colors.surface,
                        contentColor = colors.textPrimary,
                        borderColor = colors.outline,
                        enabled = !state.isBusy,
                        isLoading = state.signingInWith == AuthenticationProvider.GOOGLE,
                    )
                    ProviderButton(
                        text = stringResource(Res.string.account_continue_email),
                        icon = JaugeIcons.Mail,
                        onClick = onContinueWithEmail,
                        containerColor = colors.surface,
                        contentColor = colors.textPrimary,
                        borderColor = colors.outline,
                        iconTint = colors.textPrimary,
                        enabled = !state.isBusy,
                    )
                }
                Row(
                    modifier = Modifier
                        .padding(top = 28.dp)
                        .toggleable(value = state.marketingEmails, role = Role.Checkbox, onValueChange = onMarketingEmailsChange),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ConsentCheckBox(checked = state.marketingEmails)
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(Res.string.account_marketing_consent))
                            append(" ")
                            withStyle(SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.SemiBold)) {
                                append(stringResource(Res.string.account_marketing_optional))
                            }
                        },
                        style = typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(JaugeIcons.Shield, contentDescription = null, tint = colors.calm.accent, modifier = Modifier.size(17.dp))
                    Text(
                        text = stringResource(Res.string.account_privacy_promise),
                        style = typography.label.copy(fontWeight = FontWeight.Normal, lineHeight = 18.sp),
                        color = colors.textSecondary,
                    )
                }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = gutter).padding(bottom = 28.dp)) {
                Text(
                    text = stringResource(Res.string.account_continue_without),
                    style = typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.isBusy, role = Role.Button, onClick = onContinueWithoutAccount)
                        .padding(vertical = 6.dp),
                )
                val linkStyle = TextLinkStyles(SpanStyle(color = colors.textPrimary, textDecoration = TextDecoration.Underline))
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(Res.string.account_legal_start))
                        withLink(LinkAnnotation.Clickable("terms", linkStyle) { onOpenTerms() }) {
                            append(stringResource(Res.string.account_legal_terms))
                        }
                        append(stringResource(Res.string.account_legal_middle))
                        withLink(LinkAnnotation.Clickable("privacy", linkStyle) { onOpenPrivacyPolicy() }) {
                            append(stringResource(Res.string.account_legal_privacy))
                        }
                        append(stringResource(Res.string.account_legal_end))
                    },
                    style = typography.caption.copy(lineHeight = 17.sp),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}
