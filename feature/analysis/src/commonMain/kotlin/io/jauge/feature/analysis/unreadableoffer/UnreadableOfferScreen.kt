package io.jauge.feature.analysis.unreadableoffer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.designsystem.component.JaugeButton
import io.jauge.core.designsystem.component.JaugeButtonStyle
import io.jauge.core.designsystem.component.JaugeCard
import io.jauge.core.designsystem.component.JaugePill
import io.jauge.core.designsystem.component.JaugeTopBar
import io.jauge.core.designsystem.component.RoundIconButton
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.feature.analysis.picker.rememberImagePicker
import io.jauge.feature.analysis.resources.Res
import io.jauge.feature.analysis.resources.home_image_unreadable
import io.jauge.feature.analysis.resources.reading_close
import io.jauge.feature.analysis.resources.reading_title
import io.jauge.feature.analysis.resources.unreadable_blurry
import io.jauge.feature.analysis.resources.unreadable_city
import io.jauge.feature.analysis.resources.unreadable_company
import io.jauge.feature.analysis.resources.unreadable_headline
import io.jauge.feature.analysis.resources.unreadable_manual
import io.jauge.feature.analysis.resources.unreadable_not_read
import io.jauge.feature.analysis.resources.unreadable_paste_link
import io.jauge.feature.analysis.resources.unreadable_position
import io.jauge.feature.analysis.resources.unreadable_read
import io.jauge.feature.analysis.resources.unreadable_retake
import io.jauge.feature.analysis.resources.unreadable_text_bold
import io.jauge.feature.analysis.resources.unreadable_text_end
import io.jauge.feature.analysis.resources.unreadable_text_start
import io.jauge.feature.analysis.resources.unreadable_tip
import io.jauge.feature.analysis.resources.unreadable_tip_bold
import io.jauge.feature.analysis.resources.unreadable_value
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The screenshot or photo could not be read (mockup 10): what was read, a tip and three ways to go on.
 *
 * @param requestId request that failed, to show its image blurred.
 * @param recognizedCompany company that could still be read, if any.
 * @param onClose close button.
 * @param onStartReading opens the reading screen for a new photo.
 * @param onPasteLink "Coller le lien à la place": back to the home screen.
 * @param onEnterManually "Saisir le poste et la ville à la main".
 */
@Composable
fun UnreadableOfferScreen(
    requestId: String,
    recognizedCompany: String?,
    onClose: () -> Unit,
    onStartReading: (String) -> Unit,
    onPasteLink: () -> Unit,
    onEnterManually: () -> Unit,
) {
    val viewModel: UnreadableOfferViewModel = koinViewModel { parametersOf(requestId, recognizedCompany) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberImagePicker(onResult = viewModel::onImagePicked)
    LaunchedEffect(state.startedRequestId) {
        state.startedRequestId?.let {
            viewModel.onReadingOpened()
            onStartReading(it)
        }
    }
    UnreadableOfferContent(
        state = state,
        onClose = onClose,
        onRetakePhoto = picker::takePhoto,
        onPasteLink = onPasteLink,
        onEnterManually = onEnterManually,
    )
}

/**
 * Stateless content of the unreadable offer screen, also used by the screenshot tests.
 *
 * @param state state to draw.
 * @param onClose close button.
 * @param onRetakePhoto "Reprendre en photo".
 * @param onPasteLink "Coller le lien à la place".
 * @param onEnterManually "Saisir le poste et la ville à la main".
 * @param modifier modifier of the screen.
 */
@Composable
fun UnreadableOfferContent(
    state: UnreadableOfferUiState,
    onClose: () -> Unit,
    onRetakePhoto: () -> Unit,
    onPasteLink: () -> Unit,
    onEnterManually: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JaugeTheme(darkTheme = false) {
        val colors = JaugeTheme.colors
        val typography = JaugeTheme.typography
        val gutter = JaugeTheme.spacing.screenGutter
        Column(modifier = modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
            JaugeTopBar(
                title = stringResource(Res.string.reading_title),
                navigationIcon = {
                    RoundIconButton(icon = JaugeIcons.Close, contentDescription = stringResource(Res.string.reading_close), onClick = onClose)
                },
            )
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = gutter).padding(top = 12.dp)) {
                BlurredPreview(imageBytes = state.imageBytes)
                Text(
                    text = stringResource(Res.string.unreadable_headline),
                    style = typography.headline.copy(fontSize = 28.sp, lineHeight = 30.sp),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 20.dp),
                )
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(Res.string.unreadable_text_start))
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(Res.string.unreadable_text_bold)) }
                        append(stringResource(Res.string.unreadable_text_end))
                    },
                    style = typography.body.copy(fontWeight = FontWeight.Normal, lineHeight = 22.sp),
                    color = colors.inverseSurfaceSecondary,
                    modifier = Modifier.padding(top = 10.dp),
                )
                JaugeCard(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), contentPadding = PaddingValues(0.dp)) {
                    ReadLine(label = stringResource(Res.string.unreadable_company), value = state.recognizedCompany)
                    HorizontalDivider(color = colors.outline)
                    ReadLine(label = stringResource(Res.string.unreadable_position), value = null)
                    HorizontalDivider(color = colors.outline)
                    ReadLine(label = stringResource(Res.string.unreadable_city), value = null)
                }
                Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(JaugeIcons.Image, contentDescription = null, tint = colors.disputed.content, modifier = Modifier.size(17.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.Bold)) {
                                append(stringResource(Res.string.unreadable_tip_bold))
                            }
                            append(stringResource(Res.string.unreadable_tip))
                        },
                        style = typography.label.copy(fontWeight = FontWeight.Normal, lineHeight = 18.sp),
                        color = colors.textSecondary,
                    )
                }
                if (state.isImageUnreadable) {
                    Text(
                        text = stringResource(Res.string.home_image_unreadable),
                        style = typography.bodySmall,
                        color = colors.crowded.content,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = gutter).padding(top = 12.dp, bottom = 20.dp)) {
                JaugeButton(
                    text = stringResource(Res.string.unreadable_retake),
                    onClick = onRetakePhoto,
                    style = JaugeButtonStyle.NEUTRAL,
                    leadingIcon = JaugeIcons.Camera,
                    modifier = Modifier.fillMaxWidth(),
                )
                JaugeButton(
                    text = stringResource(Res.string.unreadable_paste_link),
                    onClick = onPasteLink,
                    style = JaugeButtonStyle.OUTLINED,
                    leadingIcon = JaugeIcons.Link,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(colors.surface, JaugeTheme.radii.buttonShape),
                )
                Text(
                    text = stringResource(Res.string.unreadable_manual),
                    style = typography.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clickable(role = Role.Button, onClick = onEnterManually)
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun BlurredPreview(imageBytes: ByteArray?) {
    val colors = JaugeTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val image = remember(imageBytes) { imageBytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(shape)
            .background(Color(0xFFE9E3D7))
            .border(1.dp, colors.outline, shape),
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize().blur(3.dp),
            )
        } else {
            BlurredOfferSketch()
        }
        JaugePill(
            text = stringResource(Res.string.unreadable_blurry),
            containerColor = colors.disputed.container,
            contentColor = colors.disputed.content,
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        )
    }
}

/** Grey bars standing for an offer, drawn when the image itself is not available. */
@Composable
private fun BlurredOfferSketch() {
    val bar = Color(0xFFC9BFAE)
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp).rotate(-2f).blur(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).background(bar, RoundedCornerShape(8.dp)))
            Box(Modifier.padding(start = 10.dp).width(140.dp).height(10.dp).background(bar, RoundedCornerShape(5.dp)))
        }
        Box(Modifier.padding(top = 14.dp).width(230.dp).height(16.dp).background(Color(0xFF8F877A), RoundedCornerShape(6.dp)))
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(9.dp).background(bar, RoundedCornerShape(5.dp)))
        Box(Modifier.padding(top = 8.dp).fillMaxWidth(0.8f).height(9.dp).background(bar, RoundedCornerShape(5.dp)))
    }
}

@Composable
private fun ReadLine(label: String, value: String?) {
    val colors = JaugeTheme.colors
    val stateDescription = stringResource(if (value != null) Res.string.unreadable_read else Res.string.unreadable_not_read)
    Row(
        modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 16.dp).semantics(mergeDescendants = true) {
            contentDescription = "$label, ${value ?: ""}, $stateDescription"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = label, style = JaugeTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.width(90.dp))
        Text(
            text = value ?: stringResource(Res.string.unreadable_value),
            style = JaugeTheme.typography.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
            color = if (value != null) colors.textPrimary else colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (value != null) JaugeIcons.Check else JaugeIcons.Close,
            contentDescription = null,
            tint = if (value != null) colors.calm.content else colors.crowded.content,
            modifier = Modifier.size(18.dp),
        )
    }
}
