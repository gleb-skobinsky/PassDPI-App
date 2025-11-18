package org.cheburnet.passdpi.tunnel

import kotlinx.cinterop.ExperimentalForeignApi
import org.cheburnet.passdpi.tunnelinterop.hev_socks5_tunnel_main
import org.cheburnet.passdpi.tunnelinterop.hev_socks5_tunnel_quit

@OptIn(ExperimentalForeignApi::class)
actual object TunnelAccessor {

    actual fun startTunnel(configPath: String, fd: Int): Int {
        return hev_socks5_tunnel_main(configPath, fd)
    }

    actual fun stopTunnel() {
        hev_socks5_tunnel_quit()
    }
}