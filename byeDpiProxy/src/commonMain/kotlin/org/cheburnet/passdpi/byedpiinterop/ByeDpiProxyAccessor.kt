package org.cheburnet.passdpi.byedpiinterop

expect object ByeDpiProxyAccessor {
    fun startProxy(args: Array<String>): Int

    fun stopProxy(fd: Int): Int
}