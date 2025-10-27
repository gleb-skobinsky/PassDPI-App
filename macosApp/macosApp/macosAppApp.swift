import SwiftUI
import ComposeAppMac

@main
struct macosAppApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    
    var body: some Scene {}
}

class AppDelegate: NSObject, NSApplicationDelegate {
    var window: NSWindow!

    func applicationDidFinishLaunching(_ aNotification: Notification) {
        InitializeKoinKt.initializeKoin()
        
        // Create a window
        window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 1000, height: 1000),
            styleMask: [.titled, .closable, .miniaturizable, .resizable],
            backing: .buffered,
            defer: false
        )
        window.title = "PassDPI"
        window.center()
        
        let composeDelegate = ComposeViewKt.AttachMainComposeView(window: window)

        // Create an instance of your custom delegate
        let windowDelegate = MyWindowDelegate(composeDelegate: composeDelegate)

        // Assign the delegate to the window
        window.delegate = windowDelegate

        window.orderFrontRegardless()
        window.zoom(nil)
    }
    
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        return true
    }
}

class MyWindowDelegate: NSObject, NSWindowDelegate {
    let composeDelegate: ComposeNSViewDelegate
    
    init(composeDelegate: ComposeNSViewDelegate) {
        self.composeDelegate = composeDelegate
    }
    
    func windowDidExpose(_ notification: Notification) {
        composeDelegate.start()
    }
    
    func windowDidBecomeMain(_ notification: Notification) {
        composeDelegate.resume()
    }
    
    func windowDidResignMain(_ notification: Notification) {
        composeDelegate.pause()
    }
    
    func windowShouldClose(_ sender: NSWindow) -> Bool {
        composeDelegate.stop()
        return true
    }

    func windowWillClose(_ notification: Notification) {
        composeDelegate.destroy()
    }
}
