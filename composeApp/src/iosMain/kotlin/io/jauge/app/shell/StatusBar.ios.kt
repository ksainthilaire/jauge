package io.jauge.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.setStatusBarStyle

/**
 * iOS: application wide status bar style (Info.plist sets `UIViewControllerBasedStatusBarAppearance` to false so that
 * the app, not the Compose view controller, decides).
 */
@Composable
actual fun StatusBarIconsEffect(lightBackground: Boolean) {
    SideEffect {
        UIApplication.sharedApplication.setStatusBarStyle(
            if (lightBackground) UIStatusBarStyleDarkContent else UIStatusBarStyleLightContent,
            animated = true,
        )
    }
}
