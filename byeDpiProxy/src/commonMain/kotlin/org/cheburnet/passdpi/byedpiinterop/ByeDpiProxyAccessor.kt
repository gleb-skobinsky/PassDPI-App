package org.cheburnet.passdpi.byedpiinterop

expect object ByeDpiProxyAccessor {
    fun startProxy()

    fun stopProxy()
}