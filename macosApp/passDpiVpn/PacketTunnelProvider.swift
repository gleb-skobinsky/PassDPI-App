import Foundation
import NetworkExtension
import ComposeAppMac

private class PassDpiLoggerImpl: PassDpiLogger {
    init() {}
    
    func log(message: String) {
        NSLog("PassDPITunnelProvider: \(message)")
    }
}

class PassDPITunnelProvider: NEPacketTunnelProvider {
    private let delegate = PassDpiTunnelProviderDelegate(logger: PassDpiLoggerImpl())
    
    override func startTunnel(
        options: [String : NSObject]? = nil,
        completionHandler: @escaping (Error?) -> Void
    ) {
        redirectStdoutToFile()
        delegate.startPassDpiTunnel(
            packetFlow: packetFlow,
            options: options,
            completionHandler: completionHandler,
            onSetNetworkSettings: { settings, completionHandler in
                self.setTunnelNetworkSettings(settings) { error in
                    completionHandler(error)
                }
            }
        )
    }
    
    override func stopTunnel(
        with reason: NEProviderStopReason,
        completionHandler: @escaping () -> Void
    ) {
        delegate.stopTunnel()
        completionHandler()
    }
    
    deinit {
        delegate.onCleared()
    }
}

func redirectStdoutToFile() {
    let fileManager = FileManager.default
    let logFile = URL(fileURLWithPath: NSTemporaryDirectory())
        .appendingPathComponent("passdpi_tunnel.log")
    
    fileManager.createFile(atPath: logFile.path, contents: nil, attributes: nil)
    
    freopen(logFile.path, "a", stdout)
    freopen(logFile.path, "a", stderr)
    setvbuf(stdout, nil, _IONBF, 0)
    setvbuf(stderr, nil, _IONBF, 0)
    
    // Log the location so you know where to find it
    NSLog("Logs redirected to: \(logFile.path)")
}

