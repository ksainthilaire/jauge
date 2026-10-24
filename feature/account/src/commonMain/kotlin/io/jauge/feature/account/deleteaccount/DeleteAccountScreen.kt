package io.jauge.feature.account.deleteaccount

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.designsystem.component.JaugeButton
import io.jauge.core.designsystem.component.JaugeButtonStyle
import io.jauge.core.designsystem.component.JaugeCard
import io.jauge.core.designsystem.component.JaugeScreenScaffold
import io.jauge.core.designsystem.component.RoundIconButton
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.feature.account.components.ConsentCheckBox
import io.jauge.feature.account.components.InlineError
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.account_back
import io.jauge.feature.account.resources.delete_account_title
import io.jauge.feature.account.resources.delete_confirm
import io.jauge.feature.account.resources.delete_confirm_check
import io.jauge.feature.account.resources.delete_delay
import io.jauge.feature.account.resources.delete_headline
import io.jauge.feature.account.resources.delete_manage_subscription
import io.jauge.feature.account.resources.delete_subscription_warning
import io.jauge.feature.account.resources.delete_text
import io.jauge.feature.account.resources.delete_what_account
import io.jauge.feature.account.resources.delete_what_alerts
import io.jauge.feature.account.resources.delete_what_analyses
import io.jauge.feature.account.resources.delete_what_shares
import io.jauge.feature.account.resources.delete_what_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Account deletion (required by the App Store and Google Play): what is deleted, the subscription warning and a
 * confirmed, final deletion.
 *
 * @param onBack back button.
 * @param onDeleted called once the account is deleted.
 */
@Composable
fun DeleteAccountScreen(onBack: () -> Unit, onDeleted: () -> Unit) {
    val viewModel: DeleteAccountViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(state.isDeleted) { if (state.isDeleted) onDeleted() }
    LaunchedEffect(state.managementUrl) {
        state.managementUrl?.let {
            viewModel.onUrlOpened()
            runCatching { uriHandler.openUri(it) }
        }
    }
    DeleteAccountContent(
        state = state,
        onBack = onBack,
        onConfirmedChange = viewModel::setConfirmed,
        onManageSubscription = viewModel::manageSubscription,
        onDelete = viewModel::delete,
    )
}

/**
 * Stateless content of the account deletion.
 *
 * @param state state to draw.
 * @param onBack back button.
 * @param onConfirmedChange confirmation box.
 * @param onManageSubscription "Gérer mon abonnement".
 * @param onDelete "Supprimer définitivement".
 * @param modifier modifier of the screen.
 */
@Composable
fun DeleteAccountContent(
    state: DeleteAccountUiState,
    onBack: () -> Unit,
    onConfirmedChange: (Boolean) -> Unit,
    onManageSubscription: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JaugeTheme.colors
    val typography = JaugeTheme.typography
    JaugeScreenScaffold(
        modifier = modifier,
        title = stringResource(Res.string.delete_account_title),
        navigationIcon = { RoundIconButton(JaugeIcons.Back, stringResource(Res.string.account_back), onBack) },
        bottomBar = {
            Button(
                onClick = onDelete,
                enabled = state.isConfirmed && !state.isDeleting,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = JaugeTheme.radii.buttonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.crowded.content,
                    contentColor = colors.onAction,
                    disabledContainerColor = colors.crowded.content.copy(alpha = 0.35f),
                    disabledContentColor = colors.onAction.copy(alpha = 0.8f),
                ),
            ) {
                if (state.isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = colors.onAction, strokeWidth = 2.5.dp)
                } else {
                    Text(stringResource(Res.string.delete_confirm), style = typography.button, modifier = Modifier.padding(vertical = 10.dp))
                }
            }
        },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = JaugeTheme.spacing.screenGutter).padding(top = 16.dp, bottom = 16.dp),
        ) {
            Text(text = stringResource(Res.string.delete_headline), style = typography.headline, color = colors.textPrimary)
            Text(
                text = stringResource(Res.string.delete_text),
                style = typography.body.copy(fontWeight = FontWeight.Normal),
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
            state.error?.let { InlineError(error = it, modifier = Modifier.padding(top = 16.dp)) }
            JaugeCard(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Text(text = stringResource(Res.string.delete_what_title), style = typography.bodyStrong, color = colors.textPrimary)
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    listOf(
                        Res.string.delete_what_analyses,
                        Res.string.delete_what_alerts,
                        Res.string.delete_what_shares,
                        Res.string.delete_what_account,
                    ).forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(JaugeIcons.Trash, contentDescription = null, tint = colors.crowded.content, modifier = Modifier.size(17.dp))
                            Text(text = stringResource(item), style = typography.bodySmall, color = colors.inverseSurfaceSecondary)
                        }
                    }
                }
                Text(
                    text = stringResource(Res.string.delete_delay),
                    style = typography.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .background(colors.disputed.container, RoundedCornerShape(JaugeTheme.radii.medium))
                    .padding(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(JaugeIcons.Warning, contentDescription = null, tint = colors.disputed.content, modifier = Modifier.size(18.dp))
                    Text(text = stringResource(Res.string.delete_subscription_warning), style = typography.bodySmall, color = colors.textPrimary)
                }
                JaugeButton(
                    text = stringResource(Res.string.delete_manage_subscription),
                    onClick = onManageSubscription,
                    style = JaugeButtonStyle.OUTLINED,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
            Row(
                modifier = Modifier
                    .padding(top = 18.dp)
                    .toggleable(value = state.isConfirmed, role = Role.Checkbox, onValueChange = onConfirmedChange)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConsentCheckBox(checked = state.isConfirmed)
                Text(text = stringResource(Res.string.delete_confirm_check), style = typography.body, color = colors.textPrimary)
            }
        }
    }
}
