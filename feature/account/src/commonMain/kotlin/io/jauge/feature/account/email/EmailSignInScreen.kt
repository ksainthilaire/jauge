package io.jauge.feature.account.email

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.designsystem.component.JaugeButton
import io.jauge.core.designsystem.component.JaugeTextField
import io.jauge.core.designsystem.component.JaugeTopBar
import io.jauge.core.designsystem.component.RoundIconButton
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.error.JaugeError
import io.jauge.feature.account.components.InlineError
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.account_back
import io.jauge.feature.account.resources.email_change_address
import io.jauge.feature.account.resources.email_field_label
import io.jauge.feature.account.resources.email_field_placeholder
import io.jauge.feature.account.resources.email_headline
import io.jauge.feature.account.resources.email_invalid
import io.jauge.feature.account.resources.email_link_headline
import io.jauge.feature.account.resources.email_link_sent_again
import io.jauge.feature.account.resources.email_link_spam
import io.jauge.feature.account.resources.email_link_waiting
import io.jauge.feature.account.resources.email_resend
import io.jauge.feature.account.resources.email_send_link
import io.jauge.feature.account.resources.email_text
import io.jauge.feature.account.resources.email_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.jauge.core.designsystem.component.JaugeButtonSize
import io.jauge.core.designsystem.component.JaugeButtonStyle
import io.jauge.feature.account.platform.rememberMailAppOpener
import io.jauge.feature.account.resources.email_link_sent_to
import io.jauge.feature.account.resources.email_step_open
import io.jauge.feature.account.resources.email_step_tap
import io.jauge.feature.account.resources.email_step_back
import io.jauge.feature.account.resources.email_open_mail_app
import io.jauge.feature.account.resources.email_resend_in
import io.jauge.feature.account.resources.email_mail_app_unavailable

/**
 * Sign in with a link sent by email (mockup 11, "Continuer avec un e-mail"), dark like the account screen. The
 * screen leaves on its own once the link is opened and the user is signed in.
 *
 * @param marketingEmails optional consent ticked on the account screen, saved once signed in.
 * @param onBack back button.
 * @param onSignedIn called once signed in.
 */
@Composable
fun EmailSignInScreen(marketingEmails: Boolean, onBack: () -> Unit, onSignedIn: () -> Unit) {
    val viewModel: EmailSignInViewModel = koinViewModel { parametersOf(marketingEmails) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isDone) { if (state.isDone) onSignedIn() }
    EmailSignInContent(
        state = state,
        onBack = if (state.step == EmailSignInStep.LINK_SENT) viewModel::changeAddress else onBack,
        onEmailChange = viewModel::onEmailChange,
        onSendLink = viewModel::sendLink,
        onResendLink = viewModel::resendLink,
        onChangeAddress = viewModel::changeAddress,
        onOpenMailApp = rememberMailAppOpener(),
    )
}

/**
 * Stateless content of the email sign in, also used by the screenshot tests.
 *
 * @param state state to draw.
 * @param onBack back button.
 * @param onEmailChange address typed.
 * @param onSendLink "Recevoir un lien".
 * @param onResendLink "Renvoyer le lien".
 * @param onChangeAddress "Changer d'adresse".
 * @param onOpenMailApp "Ouvrir ma messagerie": opens the mail app, calls its argument when there is none.
 * @param modifier modifier of the screen.
 */
@Composable
fun EmailSignInContent(
    state: EmailSignInUiState,
    onBack: () -> Unit,
    onEmailChange: (String) -> Unit,
    onSendLink: () -> Unit,
    onResendLink: () -> Unit,
    onChangeAddress: () -> Unit,
    onOpenMailApp: (onUnavailable: () -> Unit) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    JaugeTheme(darkTheme = true) {
        val colors = JaugeTheme.colors
        val typography = JaugeTheme.typography
        val gutter = JaugeTheme.spacing.screenGutter
        val isLinkSent = state.step == EmailSignInStep.LINK_SENT
        var isMailAppMissing by remember(state.step) { mutableStateOf(false) }
        Column(modifier = modifier.fillMaxSize().background(colors.background).navigationBarsPadding().imePadding()) {
            JaugeTopBar(
                title = stringResource(Res.string.email_title),
                navigationIcon = {
                    RoundIconButton(icon = JaugeIcons.Back, contentDescription = stringResource(Res.string.account_back), onClick = onBack)
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = gutter)
                    .padding(top = if (isLinkSent) 8.dp else 28.dp, bottom = 16.dp),
            ) {
                if (isLinkSent) {
                    LinkSentBody(state = state, isMailAppMissing = isMailAppMissing)
                    state.error?.takeUnless { it == JaugeError.InvalidEmail }?.let { InlineError(error = it, modifier = Modifier.padding(top = 16.dp)) }
                } else {
                    Text(
                        text = stringResource(Res.string.email_headline),
                        style = typography.headline,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = stringResource(Res.string.email_text),
                        style = typography.body.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    state.error?.takeUnless { it == JaugeError.InvalidEmail }?.let { InlineError(error = it, modifier = Modifier.padding(top = 20.dp)) }
                    JaugeTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = stringResource(Res.string.email_field_label),
                        leadingIcon = JaugeIcons.Mail,
                        placeholder = stringResource(Res.string.email_field_placeholder),
                        errorMessage = if (state.isAddressInvalid) stringResource(Res.string.email_invalid) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSendLink() }),
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = gutter).padding(bottom = 28.dp, top = 8.dp)) {
                if (isLinkSent) {
                    JaugeButton(
                        text = stringResource(Res.string.email_open_mail_app),
                        onClick = { isMailAppMissing = false; onOpenMailApp { isMailAppMissing = true } },
                        leadingIcon = JaugeIcons.Mail,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val waitSeconds = state.resendAvailableInSeconds
                        JaugeButton(
                            text = if (waitSeconds > 0) {
                                stringResource(Res.string.email_resend_in, formatCountdown(waitSeconds))
                            } else {
                                stringResource(Res.string.email_resend)
                            },
                            onClick = onResendLink,
                            style = JaugeButtonStyle.OUTLINED,
                            size = JaugeButtonSize.MEDIUM,
                            enabled = waitSeconds == 0,
                            isLoading = state.isLoading,
                            modifier = Modifier.fillMaxWidth().background(colors.surface, JaugeTheme.radii.buttonShape),
                        )
                        JaugeButton(
                            text = stringResource(Res.string.email_change_address),
                            onClick = onChangeAddress,
                            style = JaugeButtonStyle.OUTLINED,
                            size = JaugeButtonSize.MEDIUM,
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth().background(colors.surface, JaugeTheme.radii.buttonShape),
                        )
                    }
                } else {
                    JaugeButton(
                        text = stringResource(Res.string.email_send_link),
                        onClick = onSendLink,
                        isLoading = state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}


/**
 * Body of the "Regarde ta boîte mail" step: illustration, address highlighted, the three steps, the waiting state
 * and a short help line.
 */
@Composable
private fun LinkSentBody(state: EmailSignInUiState, isMailAppMissing: Boolean) {
    val colors = JaugeTheme.colors
    val typography = JaugeTheme.typography
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        MailIllustration(modifier = Modifier.fillMaxWidth().height(156.dp))
        Text(
            text = stringResource(Res.string.email_link_headline),
            style = typography.headline,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = stringResource(Res.string.email_link_sent_to),
            style = typography.body.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier
                .padding(top = 10.dp)
                .background(colors.surface, JaugeTheme.radii.pillShape)
                .border(1.dp, colors.outline, JaugeTheme.radii.pillShape)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(JaugeIcons.Mail, contentDescription = null, tint = colors.calm.accent, modifier = Modifier.size(18.dp))
            Text(
                text = state.email.trim(),
                style = typography.body.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Column(
            modifier = Modifier
                .padding(top = 22.dp)
                .fillMaxWidth()
                .background(colors.surface, JaugeTheme.radii.cardShape)
                .border(1.dp, colors.outline, JaugeTheme.radii.cardShape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepRow(number = 1, text = stringResource(Res.string.email_step_open))
            StepRow(number = 2, text = stringResource(Res.string.email_step_tap))
            StepRow(number = 3, text = stringResource(Res.string.email_step_back))
        }
        Row(modifier = Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.calm.accent, strokeWidth = 2.dp)
            Text(
                text = stringResource(Res.string.email_link_waiting),
                style = typography.bodySmall,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        val help = when {
            isMailAppMissing -> stringResource(Res.string.email_mail_app_unavailable)
            state.linkSentAgain -> stringResource(Res.string.email_link_sent_again)
            else -> stringResource(Res.string.email_link_spam)
        }
        Text(
            text = help,
            style = typography.caption.copy(lineHeight = 17.sp),
            color = if (state.linkSentAgain && !isMailAppMissing) colors.calm.content else colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** One numbered step of the "Regarde ta boîte mail" card. */
@Composable
private fun StepRow(number: Int, text: String) {
    val colors = JaugeTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(26.dp).background(colors.calm.container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = number.toString(), style = JaugeTheme.typography.numberSmall, color = colors.calm.content)
        }
        Text(
            text = text,
            style = JaugeTheme.typography.bodySmall,
            color = colors.textPrimary,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** Seconds as "0:42". */
private fun formatCountdown(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
