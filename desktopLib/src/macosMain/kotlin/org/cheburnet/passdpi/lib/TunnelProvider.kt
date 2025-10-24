package org.cheburnet.passdpi.lib

import platform.Foundation.NSError
import platform.NetworkExtension.NEPacketTunnelProvider
import platform.NetworkExtension.NEProviderStopReason

internal const val VPN_ARGS_KEY = "PASSVPN_ARGS"

class TunnelProvider : NEPacketTunnelProvider() {

    override fun startTunnelWithOptions(
        options: Map<Any?, *>?,
        completionHandler: (NSError?) -> Unit
    ) {
        val vpnArgs = options?.get(VPN_ARGS_KEY) as? String ?: error("No args provided. Abort")
        val tun2socksConfig = """
        | misc:
        |   task-stack-size: 81920
        | socks5:
        |   mtu: 8500
        |   address: 127.0.0.1
        |   port: $port
        |   udp: udp
        """.trimMargin("| ")
    }

    override fun stopTunnelWithReason(
        reason: NEProviderStopReason,
        completionHandler: () -> Unit
    ) {

    }

    private fun writeConfigToFile(args: String) {
        val cfg = """
        # minimal example configuration
        listen_ip = \(self.defaultListenIP())
        listen_port = 1080
        # add other required options...
        """
    }
}