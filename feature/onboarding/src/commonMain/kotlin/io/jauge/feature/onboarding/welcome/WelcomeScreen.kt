package io.jauge.feature.onboarding.welcome

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.jauge.core.designsystem.component.JaugeButton
import io.jauge.core.designsystem.component.JaugeLogo
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.feature.onboarding.resources.Res
import io.jauge.feature.onboarding.resources.app_name
import io.jauge.feature.onboarding.resources.welcome_have_account
import io.jauge.feature.onboarding.resources.welcome_page_indicator
import io.jauge.feature.onboarding.resources.welcome_page_one_text
import io.jauge.feature.onboarding.resources.welcome_page_one_title_highlight
import io.jauge.feature.onboarding.resources.welcome_page_one_title_start
import io.jauge.feature.onboarding.resources.welcome_page_three_text
import io.jauge.feature.onboarding.resources.welcome_page_three_title_highlight
import io.jauge.feature.onboarding.resources.welcome_page_three_title_start
import io.jauge.feature.onboarding.resources.welcome_page_two_text
import io.jauge.feature.onboarding.resources.welcome_page_two_title_highlight
import io.jauge.feature.onboarding.resources.welcome_page_two_title_start
import io.jauge.feature.onboarding.resources.welcome_sign_in
import io.jauge.feature.onboarding.resources.welcome_skip
import io.jauge.feature.onboarding.resources.welcome_start
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * One page of the welcome pager.
 *
 * @property titleStart first line of the title.
 * @property titleHighlight coral second line of the title.
 * @property text paragraph under the title.
 * @property score score shown by the gauge of the illustration.
 */
private data class WelcomePage(
    val titleStart: StringResource,
    val titleHighlight: StringResource,
    val text: StringResource,
    val score: Int,
)

private val welcomePages = listOf(
    WelcomePage(Res.string.welcome_page_one_title_start, Res.string.welcome_page_one_title_highlight, Res.string.welcome_page_one_text, 78),
    WelcomePage(Res.string.welcome_page_two_title_start, Res.string.welcome_page_two_title_highlight, Res.string.welcome_page_two_text, 49),
    WelcomePage(Res.string.welcome_page_three_title_start, Res.string.welcome_page_three_title_highlight, Res.string.welcome_page_three_text, 16),
)

/**
 * Welcome screen with the promise of the app (mockup 01), always dark.
 *
 * @param onStartAnalysis called once the welcome is passed with "Analyser ma première offre".
 * @param onSignIn called once the welcome is passed with "Me connecter".
 * @param onSkip called once the first launch is skipped with "Passer".
 */
@Composable
fun WelcomeScreen(
    onStartAnalysis: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
) {
    val viewModel: WelcomeViewModel = koinViewModel()
    WelcomeContent(
        onStartAnalysis = { viewModel.passWelcome(onStartAnalysis) },
        onSignIn = { viewModel.passWelcome(onSignIn) },
        onSkip = { viewModel.skip(onSkip) },
    )
}

/**
 * Stateless content of the welcome screen, also used by the screenshot tests.
 *
 * @param onStartAnalysis "Analyser ma première offre".
 * @param onSignIn "Me connecter".
 * @param onSkip "Passer".
 * @param modifier modifier of the screen.
 */
@Composable
fun WelcomeContent(
    onStartAnalysis: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JaugeTheme(darkTheme = true) {
        val colors = JaugeTheme.colors
        val typography = JaugeTheme.typography
        val gutter = JaugeTheme.spacing.screenGutter
        val pagerState = rememberPagerState(pageCount = { welcomePages.size })
        Column(modifier = modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JaugeLogo(modifier = Modifier.size(34.dp))
                Text(
                    text = stringResource(Res.string.app_name),
                    style = typography.title.copy(fontSize = 22.sp, letterSpacing = 0.em),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                )
                Text(
                    text = stringResource(Res.string.welcome_skip),
                    style = typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onSkip).padding(8.dp),
                )
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pageIndex ->
                val page = welcomePages[pageIndex]
                Column(modifier = Modifier.fillMaxSize()) {
                    WelcomeHero(score = page.score, modifier = Modifier.padding(top = 20.dp))
                    Column(modifier = Modifier.padding(horizontal = gutter).padding(top = 6.dp)) {
                        Text(
                            text = buildAnnotatedString {
                                append(stringResource(page.titleStart))
                                append("\n")
                                withStyle(SpanStyle(color = colors.action)) { append(stringResource(page.titleHighlight)) }
                            },
                            style = typography.display,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = stringResource(page.text),
                            style = typography.bodyLarge,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                }
            }
            PageIndicator(
                pageCount = welcomePages.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier.padding(horizontal = gutter, vertical = 18.dp),
            )
            Column(modifier = Modifier.padding(horizontal = gutter).padding(bottom = 28.dp)) {
                JaugeButton(
                    text = stringResource(Res.string.welcome_start),
                    onClick = onStartAnalysis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(Res.string.welcome_have_account))
                        append(" ")
                        withStyle(SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.Bold)) {
                            append(stringResource(Res.string.welcome_sign_in))
                        }
                    },
                    style = typography.body.copy(fontWeight = FontWeight.Normal),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable(role = Role.Button, onClick = onSignIn)
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.welcome_page_indicator, currentPage + 1, pageCount)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(if (selected) 26.dp else 6.dp, label = "dot")
            val color by animateColorAsState(if (selected) JaugeTheme.colors.action else JaugeTheme.colors.outline, label = "dotColor")
            Box(Modifier.width(width).height(6.dp).background(color, JaugeTheme.radii.pillShape))
        }
    }
}
