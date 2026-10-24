package io.jauge.feature.share

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.github.takahirom.roborazzi.captureRoboImage
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.ShareFormat
import io.jauge.core.domain.model.ShareOptions
import io.jauge.core.ui.state.ContentState
import io.jauge.feature.share.share.ShareLayout
import io.jauge.feature.share.share.ShareUiState
import io.jauge.feature.share.shares.ShareItem
import io.jauge.feature.share.shares.SharesLayout
import io.jauge.feature.share.shares.SharesUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the share card (mockup 07, three formats) and the tab "Partages" into `docs/screenshots`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-w393dp-h852dp-xhdpi")
class ShareScreenshotTest {

    private fun capture(name: String, content: @Composable () -> Unit) {
        val directory = System.getProperty("jauge.screenshots.directory") ?: "build/screenshots"
        captureRoboImage("$directory/$name.png") { JaugeTheme(darkTheme = false) { content() } }
    }

    @Composable
    private fun Share(options: ShareOptions) {
        ShareLayout(
            state = ShareUiState(
                analysis = ContentState(ShareSampleData.analysis, isLoading = false),
                calmTwin = ShareSampleData.calmTwin,
                options = options,
            ),
            snackbarHostState = SnackbarHostState(),
            cardLayer = rememberGraphicsLayer(),
            onBack = {}, onRefresh = {}, onSelectFormat = {}, onHideCompanyChange = {}, onShowCalmTwinChange = {}, onDestination = {},
        )
    }

    @Test
    fun story() = capture("07-share-story") { Share(ShareOptions()) }

    @Test
    fun square() = capture("07-share-square") { Share(ShareOptions(format = ShareFormat.SQUARE, hideCompany = true)) }

    @Test
    fun link() = capture("07-share-link") { Share(ShareOptions(format = ShareFormat.LINK)) }

    @Test
    fun shares() = capture("07-shares-tab") {
        SharesLayout(
            state = SharesUiState(
                shares = ContentState(
                    listOf(ShareItem(ShareSampleData.share, ShareSampleData.summary), ShareItem(ShareSampleData.squareShare, null)),
                    isLoading = false,
                ),
            ),
            snackbarHostState = SnackbarHostState(),
            onRefresh = {}, onOpenAnalysis = {}, onCopyLink = {}, onDelete = {}, onConfirmDelete = {}, onCancelDelete = {},
        )
    }
}
