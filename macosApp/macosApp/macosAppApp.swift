import SwiftUI
import ComposeAppMac

@main
struct macosAppApp: App {
    init() {
        InitializeKoinKt.initializeKoin()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
