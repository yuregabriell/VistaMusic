import SwiftUI

@main
struct VistaMusicApp: App {
    var body: some Scene {
        WindowGroup {
            MusicRootView()
                .preferredColorScheme(.dark)
        }
    }
}
