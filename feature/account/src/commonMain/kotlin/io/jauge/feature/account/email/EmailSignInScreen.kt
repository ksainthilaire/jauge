package io.jauge.feature.account.email

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
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
import io.jauge.feature.account.resources.email_link_text
import io.jauge.feature.account.resources.email_link_waiting
import io.jauge.feature.account.resources.email_resend
import io.jauge.feature.account.resources.email_send_link
import io.jauge.feature.account.resources.email_text
import io.jauge.feature.account.resources.email_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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
    modifier: Modifier = Modifier,
) {
    JaugeTheme(darkTheme = true) {
        val colors = JaugeTheme.colors
        val typography = JaugeTheme.typography
        val gutter = JaugeTheme.spacing.screenGutter
        val isLinkSent = state.step == EmailSignInStep.LINK_SENT
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
                    .padding(top = 28.dp, bottom = 16.dp),
            ) {
                Text(
                    text = stringResource(if (isLinkSent) Res.string.email_link_headline else Res.string.email_headline),
                    style = typography.headline,
                    color = colors.textPrimary,
                )
                Text(
                    text = if (isLinkSent) stringResource(Res.string.email_link_text, state.email.trim()) else stringResource(Res.string.email_text),
                    style = typography.body.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 10.dp),
                )
                state.error?.takeUnless { it == JaugeError.InvalidEmail }?.let { InlineError(error = it, modifier = Modifier.padding(top = 20.dp)) }
                if (isLinkSent) {
                    Row(modifier = Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.calm.accent, strokeWidth = 2.dp)
                        Text(
                            text = stringResource(Res.string.email_link_waiting),
                            style = typography.bodySmall,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    Text(
                        text = stringResource(if (state.linkSentAgain) Res.string.email_link_sent_again else Res.string.email_link_spam),
                        style = typography.bodySmall,
                        color = if (state.linkSentAgain) colors.calm.content else colors.textSecondary,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    Row(modifier = Modifier.padding(top = 10.dp)) {
                        TextAction(text = stringResource(Res.string.email_change_address), enabled = !state.isLoading, onClick = onChangeAddress)
                    }
                } else {
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
                JaugeButton(
                    text = stringResource(if (isLinkSent) Res.string.email_resend else Res.string.email_send_link),
                    onClick = if (isLinkSent) onResendLink else onSendLink,
                    isLoading = state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TextAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = JaugeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
        color = JaugeTheme.colors.textPrimary,
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    )
}
