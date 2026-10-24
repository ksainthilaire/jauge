package io.jauge.feature.account.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSBundle
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/** iOS: UIActivityViewController above the top view controller. */
@Composable
actual fun rememberTextSharer(): (title: String, text: String) -> Unit = remember {
    { _, text ->
        val controller = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        var top: UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        top?.presentViewController(controller, animated = true, completion = null)
    }
}

/** iOS: `CFBundleShortVersionString` of the main bundle. */
@Composable
actual fun rememberAppVersion(): String = remember {
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""
}
