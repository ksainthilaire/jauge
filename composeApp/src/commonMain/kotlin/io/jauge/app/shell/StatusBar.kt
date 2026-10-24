package io.jauge.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import io.jauge.core.designsystem.theme.JaugePalette
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.feature.account.navigation.AccountRoute
import io.jauge.feature.account.navigation.EmailSignInRoute
import io.jauge.feature.analysis.navigation.ReadingRoute
import io.jauge.feature.onboarding.navigation.AlertsPermissionRoute
import io.jauge.feature.onboarding.navigation.WelcomeRoute
import io.jauge.feature.paywall.navigation.PaywallRoute
import io.jauge.feature.paywall.navigation.QuotaPaywallRoute
import io.jauge.feature.share.navigation.ShareRoute
import kotlin.reflect.KClass

/** Screens that are always dark in the mockups (01, 03, 08, 11): ink behind the status bar, light icons. */
private val darkRoutes: List<KClass<*>> = listOf(
    WelcomeRoute::class,
    AccountRoute::class,
    EmailSignInRoute::class,
    ReadingRoute::class,
    PaywallRoute::class,
    QuotaPaywallRoute::class,
)

/** Screens drawn on the alternative paper background (07 share, 12 alerts). */
private val alternativeRoutes: List<KClass<*>> = listOf(
    AlertsPermissionRoute::class,
    ShareRoute::class,
)

/**
 * Color drawn behind the status bar for a destination: the background of the screen, so that the screen seems to go
 * up to the top edge.
 *
 * @param destination current destination, null before the navigation graph is ready.
 */
@Composable
fun statusBarColorFor(destination: NavDestination?): Color = when {
    destination == null -> JaugeTheme.colors.background
    darkRoutes.any { destination.hasRoute(it) } -> JaugePalette.Ink
    destination.hasRoute(AlertsPermissionRoute::class) -> JaugePalette.Paper2
    alternativeRoutes.any { destination.hasRoute(it) } -> JaugeTheme.colors.backgroundAlternative
    else -> JaugeTheme.colors.background
}

/**
 * Frame of the app: draws the edge to edge status bar area in [statusBarColor], sets dark or light status bar icons
 * to match, then the content.
 *
 * @param statusBarColor color behind the status bar, usually [statusBarColorFor].
 * @param modifier modifier of the frame.
 * @param statusBarInsets insets of the status bar (a fixed height in screenshot tests).
 * @param content the app below the status bar.
 */
@Composable
fun JaugeShellFrame(
    statusBarColor: Color,
    modifier: Modifier = Modifier,
    statusBarInsets: WindowInsets = WindowInsets.statusBars,
    content: @Composable ColumnScope.() -> Unit,
) {
    StatusBarIconsEffect(lightBackground = statusBarColor.luminance() > LIGHT_BACKGROUND_LUMINANCE)
    Column(modifier = modifier.fillMaxSize().background(statusBarColor)) {
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(statusBarInsets).background(statusBarColor))
        content()
    }
}

/** Above this luminance a background is light and needs dark status bar icons. */
private const val LIGHT_BACKGROUND_LUMINANCE = 0.5f

/**
 * Sets the color of the status bar icons: dark icons on a light background, light icons on a dark one.
 *
 * @param lightBackground true when the background behind the status bar is light.
 */
@Composable
expect fun StatusBarIconsEffect(lightBackground: Boolean)
