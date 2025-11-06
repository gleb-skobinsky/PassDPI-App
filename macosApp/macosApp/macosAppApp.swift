import SwiftUI
import ComposeAppMac

@main
struct macosAppApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    
    var body: some Scene {}
}

class AppDelegate: NSObject, NSApplicationDelegate {
    var window: NSWindow!
    private var composeDelegate: ComposeNSViewDelegate? = nil
    
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
        self.composeDelegate = composeDelegate
        
        // Create an instance of your custom delegate
        let windowDelegate = MyWindowDelegate(composeDelegate: composeDelegate)
        
        // Assign the delegate to the window
        window.delegate = windowDelegate
        
        window.makeKeyAndOrderFront(nil)
        window.zoom(nil)
    }
    
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        return true
    }
    
    func applicationWillFinishLaunching(_ notification: Notification) {
        composeDelegate?.create()
    }
    
    func applicationWillBecomeActive(_ notification: Notification) {
        composeDelegate?.start()
    }
    
    func applicationDidBecomeActive(_ notification: Notification) {
        composeDelegate?.resume()
    }
    
    func applicationWillResignActive(_ notification: Notification) {
        composeDelegate?.pause()
    }
    
    func applicationDidResignActive(_ notification: Notification) {
        composeDelegate?.stop()
    }
    
    func applicationWillTerminate(_ notification: Notification) {
        composeDelegate?.destroy()
    }
}

class MyWindowDelegate: NSObject, NSWindowDelegate {
    let composeDelegate: ComposeNSViewDelegate
    
    init(composeDelegate: ComposeNSViewDelegate) {
        self.composeDelegate = composeDelegate
    }
}
