import SwiftUI
import ComposeApp

@main
struct PassDPIApp: App {
    init() {
        InitializeKoinKt.initializeKoin()
    }
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
