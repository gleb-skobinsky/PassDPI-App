package org.cheburnet.passdpi.lib

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import org.cheburnet.passdpi.store.PassDpiOptionsStorage
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToURL
import platform.NetworkExtension.NEPacketTunnelProvider
import platform.NetworkExtension.NEProviderStopReason

internal const val VPN_ARGS_KEY = "PASSVPN_ARGS"
internal const val PORT_KEY = "PASSVPN_PORT"
internal const val CONFIG_FILE_NAME = "config"
internal const val CONFIG_EXT = "tmp"
private const val CONFIG_FULL_NAME = "$CONFIG_FILE_NAME.$CONFIG_EXT"

@OptIn(ExperimentalForeignApi::class)
class TunnelProvider(
    private val optionsStorage: PassDpiOptionsStorage,
) : NEPacketTunnelProvider() {

    override fun startTunnelWithOptions(
        options: Map<Any?, *>?,
        completionHandler: (NSError?) -> Unit
    ) {
        val port = runBlocking { optionsStorage.getPort() }
        val tun2socksConfig = """
        | misc:
        |   task-stack-size: 81920
        | socks5:
        |   mtu: 8500
        |   address: 127.0.0.1
        |   port: $port
        |   udp: udp
        """.trimMargin("| ")
        writeConfigToFile(tun2socksConfig)
    }

    override fun stopTunnelWithReason(
        reason: NEProviderStopReason,
        completionHandler: () -> Unit
    ) {

    }

    private fun writeConfigToFile(
        tunConfig: String,
    ) {
        val configUrl: NSURL = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )?.URLByAppendingPathComponent(CONFIG_FULL_NAME) ?: error("Could not create config file")
        val nsString = tunConfig as NSString
        val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: error("Could not create config file")
        val success = data.writeToURL(configUrl, atomically = true)
        if (!success) {
            error("Failed to write config file to ${configUrl.path}")
        }
    }
}