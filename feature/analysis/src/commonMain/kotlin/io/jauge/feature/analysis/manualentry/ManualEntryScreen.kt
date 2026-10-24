package io.jauge.feature.analysis.manualentry

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.designsystem.component.JaugeButton
import io.jauge.core.designsystem.component.JaugeButtonStyle
import io.jauge.core.designsystem.component.JaugeScreenScaffold
import io.jauge.core.designsystem.component.JaugeTextField
import io.jauge.core.designsystem.component.RoundIconButton
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.feature.analysis.resources.Res
import io.jauge.feature.analysis.resources.history_back
import io.jauge.feature.analysis.resources.manual_entry_analyze
import io.jauge.feature.analysis.resources.manual_entry_city_label
import io.jauge.feature.analysis.resources.manual_entry_city_placeholder
import io.jauge.feature.analysis.resources.manual_entry_headline
import io.jauge.feature.analysis.resources.manual_entry_job_label
import io.jauge.feature.analysis.resources.manual_entry_job_placeholder
import io.jauge.feature.analysis.resources.manual_entry_missing
import io.jauge.feature.analysis.resources.manual_entry_text
import io.jauge.feature.analysis.resources.manual_entry_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Manual entry of the position and the city, when the offer cannot be read (mockup 10).
 *
 * @param onBack back button.
 * @param onStartReading opens the reading screen for the request.
 */
@Composable
fun ManualEntryScreen(onBack: () -> Unit, onStartReading: (String) -> Unit) {
    val viewModel: ManualEntryViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.startedRequestId) {
        state.startedRequestId?.let {
            viewModel.onReadingOpened()
            onStartReading(it)
        }
    }
    ManualEntryContent(
        state = state,
        onBack = onBack,
        onJobTitleChange = viewModel::onJobTitleChange,
        onCityChange = viewModel::onCityChange,
        onAnalyze = viewModel::analyze,
    )
}

/**
 * Stateless content of the manual entry.
 *
 * @param state state to draw.
 * @param onBack back button.
 * @param onJobTitleChange job title typed.
 * @param onCityChange city typed.
 * @param onAnalyze "Jauger cette offre".
 * @param modifier modifier of the screen.
 */
@Composable
fun ManualEntryContent(
    state: ManualEntryUiState,
    onBack: () -> Unit,
    onJobTitleChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JaugeTheme.colors
    JaugeScreenScaffold(
        modifier = modifier,
        title = stringResource(Res.string.manual_entry_title),
        navigationIcon = { RoundIconButton(JaugeIcons.Back, stringResource(Res.string.history_back), onBack) },
        bottomBar = {
            JaugeButton(
                text = stringResource(Res.string.manual_entry_analyze),
                onClick = onAnalyze,
                style = JaugeButtonStyle.NEUTRAL,
                trailingIcon = JaugeIcons.ArrowRight,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = JaugeTheme.spacing.screenGutter).padding(top = 20.dp),
        ) {
            Text(text = stringResource(Res.string.manual_entry_headline), style = JaugeTheme.typography.headline, color = colors.textPrimary)
            Text(
                text = stringResource(Res.string.manual_entry_text),
                style = JaugeTheme.typography.body.copy(fontWeight = FontWeight.Normal),
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
            JaugeTextField(
                value = state.jobTitle,
                onValueChange = onJobTitleChange,
                label = stringResource(Res.string.manual_entry_job_label),
                placeholder = stringResource(Res.string.manual_entry_job_placeholder),
                errorMessage = if (state.isIncomplete && state.jobTitle.isBlank()) stringResource(Res.string.manual_entry_missing) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.padding(top = 22.dp),
            )
            JaugeTextField(
                value = state.city,
                onValueChange = onCityChange,
                label = stringResource(Res.string.manual_entry_city_label),
                leadingIcon = JaugeIcons.Pin,
                placeholder = stringResource(Res.string.manual_entry_city_placeholder),
                errorMessage = if (state.isIncomplete && state.city.isBlank()) stringResource(Res.string.manual_entry_missing) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onAnalyze() }),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
