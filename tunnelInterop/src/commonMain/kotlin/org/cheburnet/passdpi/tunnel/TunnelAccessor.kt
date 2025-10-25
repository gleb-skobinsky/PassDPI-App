package org.cheburnet.passdpi.tunnel

expect object TunnelAccessor {
    fun startTunnel(configPath: String, fd: Int): Boolean
    fun stopTunnel()
}