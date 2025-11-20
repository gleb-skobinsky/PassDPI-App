package org.cheburnet.passdpi.lib

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.cheburnet.passdpi.store.PassDpiOptionsStorage
import org.cheburnet.passdpi.tunfd.findTunnelFileDescriptor
import org.cheburnet.passdpi.tunnel.TunnelAccessor
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocalizedDescriptionKey
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToURL
import platform.NetworkExtension.NEDNSSettings
import platform.NetworkExtension.NEIPv4Route
import platform.NetworkExtension.NEIPv4Settings
import platform.NetworkExtension.NEIPv6Route
import platform.NetworkExtension.NEIPv6Settings
import platform.NetworkExtension.NEPacketTunnelFlow
import platform.NetworkExtension.NEPacketTunnelNetworkSettings
import platform.NetworkExtension.NETunnelNetworkSettings
import platform.posix.IFNAMSIZ
import platform.posix.getsockopt
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

@Suppress("Unused") // may be used in Swift!
object OptionsStorageProvider {
    fun getStorage(): PassDpiOptionsStorage = PassDpiOptionsStorage()
}


private const val CONFIG_FILE_NAME = "config"
private const val CONFIG_EXT = "tmp"
private const val CONFIG_FULL_NAME = "$CONFIG_FILE_NAME.$CONFIG_EXT"

private const val TUNNEL_IPV4_ADDRESS = "198.18.0.1"
private const val TUNNEL_IPV6_ADDRESS = "fc00::1"
private const val SOCKS_SERVER_HOST_IPV4 = "127.0.0.1"
private const val SOCKS_SERVER_HOST_IPV6 = "::1"

private const val TUNNEL_MTU = 8500

@Suppress("Unused")
fun interface PassDpiLogger {
    fun log(message: String)
}

private data class TunnelProviderOptions(
    val port: Long,
    val dnsIp: String,
    val enableIpV6: Boolean,
    val cmdArgs: String,
) {

    companion object {

        private fun Any?.toStringOrNull(): String? {
            if (this == null) return null
            return toString()
        }

        fun fromOptions(
            options: Map<Any?, *>?,
        ): TunnelProviderOptions? {
            if (options == null) return null
            val vpnOptionsPort = (options[OPTIONS_PORT_KEY] as? Long)
            val dnsIp = options[OPTIONS_DNS_IP].toStringOrNull()
            val cmdArgs = options[OPTIONS_CMD_LINE_ARGS].toStringOrNull()
            val enableIpV6 = options[OPTIONS_ENABLE_IPV6].toStringOrNull()
            if (vpnOptionsPort == null || enableIpV6 == null || dnsIp == null || cmdArgs == null) {
                return null
            }
            return TunnelProviderOptions(
                port = vpnOptionsPort,
                dnsIp = dnsIp,
                enableIpV6 = enableIpV6.toBooleanStrict(),
                cmdArgs = cmdArgs
            )
        }
    }
}

@Suppress("Unused") // Used in Swift!
@OptIn(ExperimentalForeignApi::class, ObsoleteWorkersApi::class)
class PassDpiTunnelProviderDelegate(
    private val logger: PassDpiLogger,
) {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val tunnelWorker = Worker.start()

    private val proxyWorker = Worker.start()

    private val proxy by lazy {
        ByeDpiProxyManager(logger)
    }

    private val mutex = Mutex()

    fun startPassDpiTunnel(
        packetFlow: NEPacketTunnelFlow,
        options: Map<Any?, *>?,
        completionHandler: (NSError?) -> Unit,
        onSetNetworkSettings: (NETunnelNetworkSettings?, (NSError?) -> Unit) -> Unit,
    ) {
        coroutineScope.launch {
            mutex.withLock {
                logger.log("Received command to start tunnel with options: $options")
                val vpnOptions = TunnelProviderOptions.fromOptions(options)
                if (vpnOptions == null) {
                    completionHandler(
                        logAndGetError("Some options not provided!")
                    )
                    return@launch
                }
                logger.log("Retrieved options successfully: $vpnOptions")
                logger.log("IPv6 mode: ${vpnOptions.enableIpV6}")

                val configPath = writeHevSocks5TunnelConfig(
                    port = vpnOptions.port,
                    enableIpv6 = vpnOptions.enableIpV6
                ) ?: run {
                    completionHandler(logAndGetError("Failed to write config to file"))
                    return@withLock
                }

                val settings = createSettings(vpnOptions.enableIpV6, vpnOptions.dnsIp) ?: run {
                    completionHandler(logAndGetError("Failed to create tunnel network settings!"))
                    return@withLock
                }

                logger.log("Command line args: ${vpnOptions.cmdArgs} config path: $configPath")
                onSetNetworkSettings(settings) { error ->
                    if (error != null) {
                        logger.log("Set settings error")
                        completionHandler(error)
                    }
                    val fd = findTunnelFileDescriptor() ?: run {
                        completionHandler(logAndGetError("Couldn't obtain fd from packets"))
                        return@onSetNetworkSettings
                    }
                    logger.log("Tunnel file descriptor: $fd")
                    logger.log("Before proxy start")

                    tunnelWorker.launch {
                        logger.log("Right before start tunnel!")
                        val resultCode = TunnelAccessor.startTunnel(
                            configPath = configPath,
                            fd = fd
                        )
                        logger.log("Start tunnel exited with code $resultCode")
                    }
                    proxyWorker.launch {
                        logger.log("Right before start proxy")
                        val result = proxy.startProxy(vpnOptions.cmdArgs)
                        logger.log("Start proxy result code: $result")
                    }
                    logger.log("Start tunnel complete")
                    completionHandler(null)
                }
            }
        }
    }

    private fun createSettings(
        enableIpV6: Boolean,
        dnsIp: String,
    ): NEPacketTunnelNetworkSettings? {
        val subnetMask = "255.255.255.0"   // usually /24 is expected for 198.18.x.x
        val ipv6Prefix = 64                // typical for ULA fc00::/64

        val primaryEn0Gateway = getGatewayAddressForInterface("en0") ?: return null
        logger.log("Detected gateway: $primaryEn0Gateway")
        val settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress = TUNNEL_IPV4_ADDRESS)

        // IPv4
        val ipv4 = NEIPv4Settings(
            addresses = listOf(TUNNEL_IPV4_ADDRESS),
            subnetMasks = listOf(subnetMask)
        )

        // Route everything into tunnel
        ipv4.includedRoutes = listOf(NEIPv4Route.defaultRoute())

        val primaryIp = getPrimaryIPv4Address()

        val excluded = mutableListOf<NEIPv4Route>()

        // Exclude SOCKS server (127.0.0.1)
        excluded.add(NEIPv4Route(SOCKS_SERVER_HOST_IPV4, "255.255.255.255"))
        excluded.add(NEIPv4Route(primaryEn0Gateway, "255.255.255.255"))
        primaryIp?.let {
            excluded.add(NEIPv4Route(it, "255.255.255.255"))
        }

        ipv4.excludedRoutes = excluded

        settings.IPv4Settings = ipv4

        // IPv6
        if (enableIpV6) {
            val ipV6 = NEIPv6Settings(
                addresses = listOf(TUNNEL_IPV6_ADDRESS),
                networkPrefixLengths = listOf(ipv6Prefix)
            )
            ipV6.includedRoutes = listOf(NEIPv6Route.defaultRoute())

            // Exclude IPv6 localhost (::1) to prevent routing loop with SOCKS proxy
            val ipv6Excluded = mutableListOf<NEIPv6Route>()
            ipv6Excluded.add(NEIPv6Route(SOCKS_SERVER_HOST_IPV6, NSNumber(128)))
            ipV6.excludedRoutes = ipv6Excluded

            settings.IPv6Settings = ipV6
            logger.log("IPv6 enabled with routes, excluded ::1 for SOCKS")
        } else {
            logger.log("IPv6 disabled")
        }
        // MTU
        settings.MTU = NSNumber(TUNNEL_MTU)

        // DNS
        settings.DNSSettings = NEDNSSettings(servers = listOf(dnsIp))

        return settings
    }

    private fun Worker.launch(block: () -> Unit) {
        execute(TransferMode.SAFE, { block }) { it() }
    }

    fun stopTunnel() {
        coroutineScope.launch {
            mutex.withLock {
                TunnelAccessor.stopTunnel()
                proxy.stopProxy()
                logger.log("Stop tunnel and proxy complete")
            }
        }
    }

    fun onCleared() {
        logger.log("Tunnel provider cleared")
        coroutineScope.cancel()
    }

    private fun obtainTunFd(packetFlow: NEPacketTunnelFlow): Int? = memScoped {
        // Allocate a buffer for interface name
        val buf = allocArray<ByteVar>(IFNAMSIZ)

        for (fd in 0..1024) {
            val len = IFNAMSIZ.toUInt()
            // 2, 2 => IPPROTO_IP / IGMP option combination used to get utun name
            val result = getsockopt(fd, 2, 2, buf, cValuesOf(len))

            if (result == 0) {
                val name = buf.toKString()
                if (name.startsWith("utun")) {
                    return@memScoped fd
                }
            }
        }

        return@memScoped null // packetFlow.valueForKey("socket.fileDescriptor") as? Int
    }

    private fun logAndGetError(
        msg: String,
    ): NSError {
        logger.log(msg)
        return NSError(
            domain = "PassDpiTunnelProvider",
            code = -2,
            userInfo = mapOf(NSLocalizedDescriptionKey to msg)
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
@Suppress("CAST_NEVER_SUCCEEDS")
private fun writeConfigToFile(
    tunConfig: String,
): String? {
    val configUrl: NSURL = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )?.URLByAppendingPathComponent(CONFIG_FULL_NAME) ?: return null
    val nsString = tunConfig as NSString
    val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: return null

    val success = data.writeToURL(configUrl, atomically = true)
    if (!success) {
        return null
    }
    return configUrl.path
}

internal fun writeHevSocks5TunnelConfig(
    port: Long,
    enableIpv6: Boolean = true,
): String? {
    // Always use IPv4 localhost for SOCKS connection to avoid IPv6 binding issues
    // The proxy binds to 0.0.0.0 which accepts IPv4 connections reliably
    val socksAddress = SOCKS_SERVER_HOST_IPV4

    val ipv6Line = if (enableIpv6) "  ipv6: '$TUNNEL_IPV6_ADDRESS'" else ""

    val tun2socksConfig = """
    | misc:
    |   task-stack-size: 81920
    | tunnel:
    |   name: tun0
    |   mtu: $TUNNEL_MTU
    |   multi-queue: false
    |   ipv4: $TUNNEL_IPV4_ADDRESS
    |$ipv6Line
    | socks5:
    |   mtu: $TUNNEL_MTU
    |   address: $socksAddress
    |   port: $port
    |   udp: udp
    """.trimMargin("| ")
    return writeConfigToFile(
        tunConfig = tun2socksConfig,
    )
}

private fun createSettings2(
    enableIpV6: Boolean,
    dnsIp: String,
): NEPacketTunnelNetworkSettings? {
    val settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress = "198.18.0.254")
    val ipv4Settings = NEIPv4Settings(
        addresses = listOf("198.18.0.1"), // TUNNEL_IPV4_ADDRESS
        subnetMasks = listOf("255.255.255.0")
    )
    val defaultIPv4Route = NEIPv4Route.defaultRoute()
    val socksServerIP = "10.0.0.1"
    val gatewayIP = "10.0.2.2"
    val socksBypassRoute = NEIPv4Route(
        destinationAddress = socksServerIP,
        subnetMask = "255.255.255.255"
    )
    socksBypassRoute.gatewayAddress = gatewayIP
    ipv4Settings.includedRoutes = listOf(socksBypassRoute, defaultIPv4Route)
    ipv4Settings.excludedRoutes = emptyList<NEIPv4Route>()
    settings.IPv4Settings = ipv4Settings

    val ipv6Settings = NEIPv6Settings(
        addresses = listOf("fc00::1"), // TUNNEL_IPV6_ADDRESS
        networkPrefixLengths = listOf(64)
    )
    val defaultIPv6Route = NEIPv6Route.defaultRoute()
    ipv6Settings.includedRoutes = listOf(defaultIPv6Route)
    settings.IPv6Settings = ipv6Settings
    val dnsSettings = NEDNSSettings(servers = listOf(dnsIp))
    dnsSettings.matchDomains = listOf("")
    settings.DNSSettings = dnsSettings
    settings.MTU = NSNumber(8500) // TUNNEL_MTU
    return settings
}