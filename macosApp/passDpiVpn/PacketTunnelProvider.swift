import Foundation
import NetworkExtension
import ComposeAppMac

private let CONFIG_FILE_NAME = "config"
private let CONFIG_EXT = "tmp"
private let CONFIG_FULL_NAME = "\(CONFIG_FILE_NAME).\(CONFIG_EXT)"

class PassDPITunnelProvider: NEPacketTunnelProvider {
    
    private var optionsStorage: OptionsStoragePassDpiOptionsStorage? = nil
    
    // Lazily create the storage
    private func getOrCreateStorage() -> OptionsStoragePassDpiOptionsStorage {
        if let existing = optionsStorage {
            return existing
        } else {
            let new = OptionsStorageProvider.shared.getStorage()
            optionsStorage = new
            return new
        }
    }
    
    override func startTunnel(
        options: [String : NSObject]? = nil,
        completionHandler: @escaping (Error?) -> Void
    ) {
        Task {
            do {
                NSLog("Received command to start tunnel with options")
                let vpnOptions = try await getOrCreateStorage().getVpnOptions()
                self.startTunnelInternal(vpnOptions: vpnOptions, completionHandler: completionHandler)
            } catch {
                NSLog("Error occurred while retrieving options: \(error)")
                completionHandler(error)
            }
        }
    }
    
    private func startTunnelInternal(
        vpnOptions: OptionsStoragePassDpiVPNOptions,
        completionHandler: @escaping (Error?) -> Void
    ) {
        // Generate tun2socks YAML config
        let tun2socksConfig = """
        misc:
          task-stack-size: 81920
        socks5:
          mtu: 8500
          address: 127.0.0.1
          port: \(vpnOptions.port)
          udp: udp
        """
        guard let configPath = writeConfigToFile(tunConfig: tun2socksConfig) else {
            completionHandler(NSError(domain: "PassDpiTunnelProvider", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to write config file"]))
            return
        }
        
        // Configure virtual interface
        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "10.10.10.10")
        
        let ipv4Settings = NEIPv4Settings(addresses: ["10.10.10.10"], subnetMasks: ["255.255.255.255"])
        ipv4Settings.includedRoutes = [NEIPv4Route.default()]
        settings.ipv4Settings = ipv4Settings
        
        if vpnOptions.enableIpV6 {
            let ipv6Settings = NEIPv6Settings(addresses: ["fd00::1"], networkPrefixLengths: [128])
            ipv6Settings.includedRoutes = [NEIPv6Route.default()]
            settings.ipv6Settings = ipv6Settings
        }
        
        let dnsSettings = NEDNSSettings(servers: [vpnOptions.dnsIp])
        settings.dnsSettings = dnsSettings
        
        setTunnelNetworkSettings(settings) { error in
            if let error = error {
                completionHandler(error)
                return
            }
            guard let fd = self.obtainTunFd() else {
                completionHandler(NSError(domain: "PassDpiTunnelProvider", code: -2, userInfo: [NSLocalizedDescriptionKey: "Couldn't obtain fd from packets"]))
                return
            }
            DispatchQueue.global(qos: .userInitiated).async {
                TunnelAccessor.shared.startTunnel(configPath: configPath, fd: Int32(fd))
            }
            completionHandler(nil)
        }
    }
    
    override func stopTunnel(
        with reason: NEProviderStopReason,
        completionHandler: @escaping () -> Void
    ) {
        TunnelAccessor.shared.stopTunnel()
        completionHandler()
    }
    
    private func obtainTunFd() -> Int32? {
        var buf = [CChar](repeating: 0, count: Int(IFNAMSIZ))
        
        for fd: Int32 in 0 ... 1024 {
            var len = socklen_t(buf.count)
            
            if getsockopt(fd, 2 /* IGMP */, 2, &buf, &len) == 0 && String(cString: buf).hasPrefix("utun") {
                return fd
            }
        }
        
        return packetFlow.value(forKey: "socket.fileDescriptor") as? Int32
    }
    
    private func writeConfigToFile(tunConfig: String) -> String? {
        do {
            let manager = FileManager.default
            let directoryURL = try manager.url(
                for: .documentDirectory,
                in: .userDomainMask,
                appropriateFor: nil,
                create: false
            )
            let fileURL = directoryURL.appendingPathComponent(CONFIG_FULL_NAME)
            try tunConfig.write(to: fileURL, atomically: true, encoding: .utf8)
            return fileURL.path
        } catch {
            NSLog("Failed to write config: \(error)")
            return nil
        }
    }
}
