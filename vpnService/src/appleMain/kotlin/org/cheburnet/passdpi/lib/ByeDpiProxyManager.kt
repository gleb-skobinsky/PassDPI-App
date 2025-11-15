package org.cheburnet.passdpi.lib

import org.cheburnet.passdpi.byedpiinterop.ByeDpiProxyAccessor

class ByeDpiProxyManager(
    private val logger: PassDpiLogger,
) {
    private var fd = -1

    fun startProxy(commandLineArguments: String) = startProxy(
        cmdToArgs(commandLineArguments)
    )

    fun startProxy(
        commandLineArguments: Array<String>,
    ): Int {
        try {
            logger.log("Right before socket create ${commandLineArguments.toList()}")
            val fd = createSocket(commandLineArguments)
            if (fd < 0) {
                return -1
            }
            return ByeDpiProxyAccessor.startProxy(fd)
        } catch (e: Exception) {
            logger.log(e.stackTraceToString())
            throw e
        }
    }

    fun stopProxy(): Int {
        if (fd < 0) {
            throw IllegalStateException("Proxy is not running")
        }

        val result = ByeDpiProxyAccessor.stopProxy(fd)
        if (result == 0) {
            fd = -1
        }
        return result
    }

    private fun createSocket(commandLineArguments: Array<String>): Int {
        if (fd >= 0) {
            throw IllegalStateException("Proxy is already running")
        }

        val fd = ByeDpiProxyAccessor.createSocket(commandLineArguments)
        logger.log("File descriptor after create socket: $fd")
        if (fd < 0) {
            return -1
        }
        this.fd = fd
        return fd
    }
}