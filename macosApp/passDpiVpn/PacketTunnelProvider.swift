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
