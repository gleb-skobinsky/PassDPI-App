import SwiftUI
import ComposeAppMac

struct ContentView: View {
    var body: some View {
        VStack {
            Image(systemName: "globe")
                .imageScale(.large)
                .foregroundStyle(.tint)
            Text(Platform_macosKt.getPlatform().name)
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
