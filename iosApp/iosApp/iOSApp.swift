import SwiftUI
import ComposeApp

/// Entry point of the iOS app: shows the shared Compose user interface.
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // Universal Links https://jauge.io/a/{analysisId} (applinks:jauge.io in iosApp.entitlements).
                .onOpenURL { url in
                    MainViewControllerKt.openIncomingLink(url: url.absoluteString)
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL {
                        MainViewControllerKt.openIncomingLink(url: url.absoluteString)
                    }
                }
        }
    }
}
