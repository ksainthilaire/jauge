package io.jauge.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import io.jauge.app.resources.Res
import io.jauge.app.resources.tab_analyze
import io.jauge.app.resources.tab_profile
import io.jauge.app.resources.tab_shares
import io.jauge.app.resources.tab_watch
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.feature.account.navigation.ProfileRoute
import io.jauge.feature.analysis.navigation.HomeRoute
import io.jauge.feature.share.navigation.SharesRoute
import io.jauge.feature.twins.navigation.TwinOffersRoute
import org.jetbrains.compose.resources.StringResource
import kotlin.reflect.KClass

/**
 * Destinations of the tab bar (mockups 02, 06 and 13).
 *
 * @property route route opened by the tab.
 * @property routeClass class of the route, used to highlight the selected tab.
 * @property label label of the tab.
 * @property icon line icon of the tab.
 */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val label: StringResource,
    val icon: ImageVector,
) {
    /** "Jauger": paste an offer. */
    ANALYZE(HomeRoute, HomeRoute::class, Res.string.tab_analyze, JaugeIcons.Gauge),

    /** "Veille": twin offers and calm alerts. */
    WATCH(TwinOffersRoute(), TwinOffersRoute::class, Res.string.tab_watch, JaugeIcons.Radar),

    /** "Partages": shared analyses. */
    SHARES(SharesRoute, SharesRoute::class, Res.string.tab_shares, JaugeIcons.Share),

    /** "Profil": profile and subscription. */
    PROFILE(ProfileRoute, ProfileRoute::class, Res.string.tab_profile, JaugeIcons.Profile),
}
