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
import org.cheburnet.passdpi.store.EditableSettings
import org.cheburnet.passdpi.store.PassDpiOptionsStorage
import org.cheburnet.passdpi.tunnel.TunnelAccessor
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocalizedDescriptionKey
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
                val tun2socksConfig = """
                | misc:
                |   task-stack-size: 81920
                | socks5:
                |   mtu: 8500
                |   address: 127.0.0.1
                |   port: ${vpnOptions.port}
                |   udp: udp
                """.trimMargin("| ")
                logger.log("Writing to file")
                val configPath = writeConfigToFile(
                    tunConfig = tun2socksConfig,
                    completionHandler = completionHandler
                ) ?: return@withLock
                val settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress = "10.10.10.10")
                val ipV4 = NEIPv4Settings(
                    addresses = listOf("10.10.10.10"),
                    subnetMasks = listOf("255.255.255.255")
                )
                ipV4.includedRoutes = listOf(NEIPv4Route.defaultRoute())
                settings.IPv4Settings = ipV4

                if (vpnOptions.enableIpV6) {
                    val ipV6 = NEIPv6Settings(
                        addresses = listOf("fd00::1"),
                        networkPrefixLengths = listOf(128)
                    )
                    ipV6.includedRoutes = listOf(NEIPv6Route.defaultRoute())
                }

                val dnsSettings = NEDNSSettings(servers = listOf(vpnOptions.dnsIp))
                settings.DNSSettings = dnsSettings

                logger.log("Command line args: ${vpnOptions.cmdArgs}")
                onSetNetworkSettings(settings) { error ->
                    if (error != null) {
                        logger.log("Set settings error")
                        completionHandler(error)
                    }
                    val fd = obtainTunFd(packetFlow) ?: run {
                        completionHandler(logAndGetError("Couldn't obtain fd from packets"))
                        return@onSetNetworkSettings
                    }
                    logger.log("Before proxy start")

                    /*
                    proxyWorker.launch {
                        val args = cmdToArgs(vpnOptions.cmdArgs)
                        logger.log("Args: ${args.toList()}")
                        val result = byeDpiProxy.startProxy(
                            commandLineArguments = cmdToArgs(vpnOptions.cmdArgs),
                        )
                        logger.log("Proxy setup! proxy code $result")
                    }

                     */
                    tunnelWorker.launch {
                        logger.log("Right before start tunnel!")
                        TunnelAccessor.startTunnel(
                            configPath = configPath,
                            fd = fd
                        )
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

        return@memScoped packetFlow.valueForKey("socket.fileDescriptor") as? Int
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun writeConfigToFile(
        tunConfig: String,
        completionHandler: (NSError?) -> Unit,
    ): String? {
        val configUrl: NSURL = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )?.URLByAppendingPathComponent(CONFIG_FULL_NAME) ?: run {
            completionHandler(logAndGetError("Could not create config file"))
            return null
        }
        val nsString = tunConfig as NSString
        val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: run {
            completionHandler(logAndGetError("Could not create config file"))
            return null
        }
        val success = data.writeToURL(configUrl, atomically = true)
        if (!success) {
            completionHandler(logAndGetError("Failed to write config file to ${configUrl.path}"))
            return null
        }
        return configUrl.absoluteString!!
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