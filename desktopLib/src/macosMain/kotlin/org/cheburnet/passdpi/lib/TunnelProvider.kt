package org.cheburnet.passdpi.lib

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import org.cheburnet.passdpi.store.PassDpiOptionsStorage
import org.cheburnet.passdpi.tunnel.TunnelAccessor
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataUsingEncoding
import platform.Foundation.fileDescriptor
import platform.Foundation.valueForKey
import platform.Foundation.valueForKeyPath
import platform.Foundation.writeToURL
import platform.NetworkExtension.NEDNSSettings
import platform.NetworkExtension.NEIPv4Route
import platform.NetworkExtension.NEIPv4Settings
import platform.NetworkExtension.NEIPv6Route
import platform.NetworkExtension.NEIPv6Settings
import platform.NetworkExtension.NEPacketTunnelNetworkSettings
import platform.NetworkExtension.NEPacketTunnelProvider
import platform.NetworkExtension.NEProviderStopReason
import platform.osx.raw
import platform.posix.err

internal const val VPN_ARGS_KEY = "PASSVPN_ARGS"
internal const val PORT_KEY = "PASSVPN_PORT"
internal const val CONFIG_FILE_NAME = "config"
internal const val CONFIG_EXT = "tmp"
private const val CONFIG_FULL_NAME = "$CONFIG_FILE_NAME.$CONFIG_EXT"

@OptIn(ExperimentalForeignApi::class)
class TunnelProvider(
    private val optionsStorage: PassDpiOptionsStorage,
) : NEPacketTunnelProvider() {

    private val keyCandidates = listOf(
        "socket.fileDescriptor",
        "socket",
        "socket.handle"
    )

    override fun startTunnelWithOptions(
        options: Map<Any?, *>?,
        completionHandler: (NSError?) -> Unit
    ) {
        val options = runBlocking { optionsStorage.getVpnOptions() }
        val tun2socksConfig = """
        | misc:
        |   task-stack-size: 81920
        | socks5:
        |   mtu: 8500
        |   address: 127.0.0.1
        |   port: ${options.port}
        |   udp: udp
        """.trimMargin("| ")
        val configPath = writeConfigToFile(tun2socksConfig)
        val settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress = "10.10.10.10")
        val ipV4 = NEIPv4Settings(addresses = listOf("10.10.10.10"), subnetMasks = listOf("255.255.255.255"))
        ipV4.includedRoutes = listOf(NEIPv4Route.defaultRoute())
        settings.IPv4Settings = ipV4

        if (options.enableIpV6) {
            val ipV6 = NEIPv6Settings(addresses = listOf("fd00::1"), networkPrefixLengths = listOf(128))
            ipV6.includedRoutes = listOf(NEIPv6Route.defaultRoute())
        }

        val dnsSettings = NEDNSSettings(servers = listOf(options.dnsIp))
        settings.DNSSettings = dnsSettings

        setTunnelNetworkSettings(settings) { error ->
            if (error != null) {
                completionHandler(error)
            }
            val fd = obtainTunFd() ?: error("Couldn't obtain fd from packets")
            TunnelAccessor.startTunnel(
                configPath = configPath,
                fd = fd
            )
        }
    }

    private fun obtainTunFd(): Int? {
        for (keyPath in keyCandidates) {
            when (val rawValue = packetFlow.valueForKeyPath(keyPath)) {
                is NSNumber -> return rawValue.intValue
                is Int -> return rawValue
                is NSFileHandle -> return rawValue.fileDescriptor
            }
        }
        return null
    }

    override fun stopTunnelWithReason(
        reason: NEProviderStopReason,
        completionHandler: () -> Unit
    ) {
        TunnelAccessor.stopTunnel()
    }

    private fun writeConfigToFile(
        tunConfig: String,
    ): String {
        val configUrl: NSURL = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )?.URLByAppendingPathComponent(CONFIG_FULL_NAME) ?: configFileError()
        val nsString = tunConfig as NSString
        val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: configFileError()
        val success = data.writeToURL(configUrl, atomically = true)
        if (!success) {
            error("Failed to write config file to ${configUrl.path}")
        }
        return configUrl.absoluteString!!
    }

    private fun startNativeTunnel(
        configPath: String,
        tunnelFd: Int,
    ) {

    }

    private fun configFileError(): Nothing = error("Could not create config file")
}