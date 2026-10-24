import SwiftUI
import UIKit
import ComposeApp

/// Bridges the Compose root view controller (`MainViewController` in `composeApp/src/iosMain`) into SwiftUI.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Root view: the Compose user interface, drawn edge to edge (Compose handles the safe areas).
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
