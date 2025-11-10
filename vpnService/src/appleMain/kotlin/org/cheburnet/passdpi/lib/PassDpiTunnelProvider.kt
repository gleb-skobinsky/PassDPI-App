package org.cheburnet.passdpi.lib

import co.touchlab.kermit.Logger
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
import org.cheburnet.passdpi.tunnel.TunnelAccessor
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataUsingEncoding
import platform.Foundation.valueForKey
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

@Suppress("Unused") // may be used in Swift!
object OptionsStorageProvider {
    fun getStorage(): PassDpiOptionsStorage = PassDpiOptionsStorage()
}


private const val CONFIG_FILE_NAME = "config"
private const val CONFIG_EXT = "tmp"
private const val CONFIG_FULL_NAME = "$CONFIG_FILE_NAME.$CONFIG_EXT"

@Suppress("Unused") // Used in Swift!
@OptIn(ExperimentalForeignApi::class)
class PassDpiTunnelProviderDelegate {
    private val optionsStorage: PassDpiOptionsStorage by lazy { PassDpiOptionsStorage() }
    private val byeDpiProxy: ByeDpiProxyManager by lazy { ByeDpiProxyManager() }

    private val coroutineScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1))
    private val mutex = Mutex()

    private val logger = Logger.withTag("PassDpiTunnelProviderDelegate")

    fun startTunnelWithOptions(
        packetFlow: NEPacketTunnelFlow,
        options: Map<Any?, *>?,
        completionHandler: (NSError?) -> Unit,
        onSetNetworkSettings: (NETunnelNetworkSettings?, (NSError?) -> Unit) -> Unit,
    ) {
        coroutineScope.launch {
            mutex.withLock {
                logger.i("Received command to start tunnel with options: $options")
                val options = optionsStorage.getVpnOptions()
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
                val ipV4 = NEIPv4Settings(
                    addresses = listOf("10.10.10.10"),
                    subnetMasks = listOf("255.255.255.255")
                )
                ipV4.includedRoutes = listOf(NEIPv4Route.defaultRoute())
                settings.IPv4Settings = ipV4

                if (options.enableIpV6) {
                    val ipV6 = NEIPv6Settings(
                        addresses = listOf("fd00::1"),
                        networkPrefixLengths = listOf(128)
                    )
                    ipV6.includedRoutes = listOf(NEIPv6Route.defaultRoute())
                }

                val dnsSettings = NEDNSSettings(servers = listOf(options.dnsIp))
                settings.DNSSettings = dnsSettings

                val commandLineArgs = optionsStorage.getCommandLineArgs()
                onSetNetworkSettings(settings) { error ->
                    if (error != null) {
                        completionHandler(error)
                    }
                    val fd = obtainTunFd(packetFlow) ?: error("Couldn't obtain fd from packets")

                    launch {
                        TunnelAccessor.startTunnel(
                            configPath = configPath,
                            fd = fd
                        )
                    }
                    launch {
                        byeDpiProxy.startProxy(
                            cmdToArgs(commandLineArgs)
                        )
                    }
                    logger.i("Start tunnel complete")
                    completionHandler(null)
                }
            }
        }
    }

    fun stopTunnel() {
        coroutineScope.launch {
            mutex.withLock {
                TunnelAccessor.stopTunnel()
                logger.i("Stop tunnel complete")
            }
        }
    }

    fun onCleared() {
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

        return@memScoped packetFlow.valueForKey("socket.fileDescriptor") as? Int
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
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

    private fun configFileError(): Nothing = error("Could not create config file")
}