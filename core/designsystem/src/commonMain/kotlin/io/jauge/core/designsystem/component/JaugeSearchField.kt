package io.jauge.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme

/**
 * Search field of the long lists: a rounded card with a magnifying glass, a one line input and a button that clears
 * the text. The keyboard action "Rechercher" closes the keyboard.
 *
 * @param query current text.
 * @param onQueryChange called with the new text.
 * @param placeholder hint shown while empty ("Poste, entreprise ou ville"), from `strings.xml`.
 * @param clearDescription spoken description of the clear button, from `strings.xml`.
 * @param onClear called by the clear button.
 * @param modifier modifier of the field.
 */
@Composable
fun JaugeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    clearDescription: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JaugeTheme.colors
    val typography = JaugeTheme.typography
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var isFocused by remember { mutableStateOf(false) }
    val shape = JaugeTheme.radii.pillShape
    val textStyle = typography.body.copy(color = colors.textPrimary)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(colors.surface, shape)
            .border(if (isFocused) 2.dp else 1.dp, if (isFocused) colors.textPrimary else colors.outline, shape)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(JaugeIcons.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.action),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide()
                focusManager.clearFocus()
            }),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle.copy(color = colors.textTertiary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                Box(
                    modifier = Modifier.size(24.dp).background(colors.backgroundAlternative, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(JaugeIcons.Close, contentDescription = clearDescription, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                }
            }
        } else {
            Box(Modifier.padding(end = 12.dp))
        }
    }
}

/**
 * Closes the keyboard and clears the focus as soon as the user scrolls the content below this modifier. Place it
 * before `verticalScroll`, or on a `LazyColumn`.
 */
@Composable
fun Modifier.hideKeyboardOnScroll(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val connection = remember(focusManager, keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }
                return Offset.Zero
            }
        }
    }
    return this.nestedScroll(connection)
}
