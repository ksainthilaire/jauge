package io.jauge.feature.account.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.designsystem.component.JaugeButton
import io.jauge.core.designsystem.component.JaugeButtonStyle
import io.jauge.core.designsystem.component.JaugeCard
import io.jauge.core.designsystem.component.JaugeChip
import io.jauge.core.designsystem.component.JaugeChipStyle
import io.jauge.core.designsystem.component.JaugeScreenScaffold
import io.jauge.core.designsystem.component.JaugeSwitch
import io.jauge.core.designsystem.component.JaugeTextField
import io.jauge.core.designsystem.component.RoundIconButton
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.SearchArea
import io.jauge.feature.account.components.SettingsGroupTitle
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.account_back
import io.jauge.feature.account.resources.settings_alerts_system
import io.jauge.feature.account.resources.settings_alerts_text
import io.jauge.feature.account.resources.settings_alerts_title
import io.jauge.feature.account.resources.settings_area_city
import io.jauge.feature.account.resources.settings_area_city_placeholder
import io.jauge.feature.account.resources.settings_area_radius
import io.jauge.feature.account.resources.settings_area_radius_value
import io.jauge.feature.account.resources.settings_area_text
import io.jauge.feature.account.resources.settings_area_title
import io.jauge.feature.account.resources.settings_save
import io.jauge.feature.account.resources.settings_saved
import io.jauge.feature.account.resources.settings_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * App settings: calm alerts and search area (mockup 13, "Réglages").
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onBack = onBack,
        onCalmAlertsChange = viewModel::setCalmAlertsEnabled,
        onCityChange = viewModel::onCityChange,
        onRadiusChange = viewModel::onRadiusChange,
        onSave = viewModel::saveSearchArea,
    )
}

/**
 * Stateless content of the settings.
 *
 * @param state state to draw.
 * @param onBack back button.
 * @param onCalmAlertsChange alerts switch.
 * @param onCityChange city typed.
 * @param onRadiusChange radius chosen.
 * @param onSave "Enregistrer".
 * @param modifier modifier of the screen.
 */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    onCalmAlertsChange: (Boolean) -> Unit,
    onCityChange: (String) -> Unit,
    onRadiusChange: (Int) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JaugeTheme.colors
    val typography = JaugeTheme.typography
    JaugeScreenScaffold(
        modifier = modifier,
        title = stringResource(Res.string.settings_title),
        navigationIcon = { RoundIconButton(JaugeIcons.Back, stringResource(Res.string.account_back), onBack) },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = JaugeTheme.spacing.screenGutter)
                .padding(bottom = 24.dp),
        ) {
            SettingsGroupTitle(stringResource(Res.string.settings_alerts_title))
            JaugeCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.settings_alerts_title),
                        style = typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    JaugeSwitch(checked = state.calmAlertsEnabled, onCheckedChange = onCalmAlertsChange)
                }
                Text(
                    text = stringResource(Res.string.settings_alerts_text),
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = stringResource(Res.string.settings_alerts_system),
                    style = typography.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            SettingsGroupTitle(stringResource(Res.string.settings_area_title))
            Text(text = stringResource(Res.string.settings_area_text), style = typography.bodySmall, color = colors.textSecondary)
            JaugeTextField(
                value = state.city,
                onValueChange = onCityChange,
                label = stringResource(Res.string.settings_area_city),
                leadingIcon = JaugeIcons.Pin,
                placeholder = stringResource(Res.string.settings_area_city_placeholder),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = stringResource(Res.string.settings_area_radius),
                style = typography.label,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 14.dp, start = 4.dp),
            )
            Row(
                modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchArea.RADII_KILOMETERS.forEach { radius ->
                    JaugeChip(
                        text = stringResource(Res.string.settings_area_radius_value, radius),
                        selected = radius == state.radiusKilometers,
                        onClick = { onRadiusChange(radius) },
                        style = JaugeChipStyle.SEGMENT,
                    )
                }
            }
            JaugeButton(
                text = stringResource(Res.string.settings_save),
                onClick = onSave,
                style = JaugeButtonStyle.NEUTRAL,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            )
            if (state.isSaved) {
                Text(
                    text = stringResource(Res.string.settings_saved),
                    style = typography.bodySmall,
                    color = colors.calm.content,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                )
            }
        }
    }
}
