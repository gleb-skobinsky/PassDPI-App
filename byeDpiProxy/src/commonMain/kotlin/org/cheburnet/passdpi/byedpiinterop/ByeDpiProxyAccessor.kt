package org.cheburnet.passdpi.byedpiinterop

expect object ByeDpiProxyAccessor {
    fun startProxy(fd: Int)

    fun stopProxy()
}