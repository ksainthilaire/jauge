package io.jauge.app

import androidx.compose.ui.window.ComposeUIViewController
import io.jauge.app.di.startJaugeKoin
import io.jauge.core.common.configuration.AppEnvironment
import io.jauge.core.common.platform.Platform
import platform.UIKit.UIViewController
import kotlin.experimental.ExperimentalNativeApi

private var isKoinStarted = false

/**
 * Entry point used by the Swift code of `iosApp`: starts Koin once and returns the Compose root view controller.
 */
@OptIn(ExperimentalNativeApi::class)
fun MainViewController(): UIViewController {
    if (!isKoinStarted) {
        startJaugeKoin(
            AppEnvironment.fromBuildConfiguration(
                platform = Platform.IOS,
                isDebugBuild = kotlin.native.Platform.isDebugBinary,
            ),
        )
        isKoinStarted = true
    }
    return ComposeUIViewController { JaugeApp() }
}
