import SwiftUI
import UIKit
import ComposeApp

/// Receives the APNs device token and hands it to the shared code, which saves it in Supabase (`devices` table).
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let hexadecimalToken = deviceToken.map { String(format: "%02x", $0) }.joined()
        PlatformBridgeKt.onApnsTokenReceived(hexadecimalToken: hexadecimalToken)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // No push on this device (simulator, missing entitlement): the app keeps the in app alerts of Realtime.
    }
}

/// Entry point of the iOS app: shows the shared Compose user interface and forwards the sign in links.
@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Sign in callback of Supabase (io.jauge.app://login-callback), then Universal Links
                // https://jauge.io/a/{analysisId} (applinks:jauge.io in iosApp.entitlements).
                .onOpenURL { url in
                    if !PlatformBridgeKt.handleDeepLink(url: url.absoluteString) {
                        MainViewControllerKt.openIncomingLink(url: url.absoluteString)
                    }
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL {
                        MainViewControllerKt.openIncomingLink(url: url.absoluteString)
                    }
                }
        }
    }
}
