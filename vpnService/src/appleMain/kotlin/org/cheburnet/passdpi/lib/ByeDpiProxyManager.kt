package org.cheburnet.passdpi.lib

import org.cheburnet.passdpi.byedpiinterop.ByeDpiProxyAccessor
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

interface ByeDpiProxyManager {
    fun startProxy(commandLineArguments: String): Int

    fun stopProxy(): Int
}

fun ByeDpiProxyManager(logger: PassDpiLogger): ByeDpiProxyManager {
    return ByeDpiProxyManagerImpl(logger)
}

@OptIn(ExperimentalAtomicApi::class)
private class ByeDpiProxyManagerImpl(
    private val logger: PassDpiLogger,
) : ByeDpiProxyManager {

    private val fd = AtomicInt(-1)

    override fun startProxy(commandLineArguments: String): Int = startProxy(
        commandLineArguments = cmdToArgs(commandLineArguments)
    )

    private fun startProxy(
        commandLineArguments: List<String>,
    ): Int {
        ByeDpiProxyAccessor.maybeLoad()
        val finalArgs = if (!commandLineArguments.any { it == "-i" || it == "--ip" }) {
            //logger.log("No IP binding specified. Binding to :: (dual-stack IPv6)")
            commandLineArguments + listOf("--ip", "::", "--conn-ip", "::", )
        } else {
            commandLineArguments
        }

        try {
            logger.log("Right before socket create $finalArgs")
            val fd = createSocket(finalArgs.toTypedArray())
            if (fd < 0) {
                return -1
            }
            return ByeDpiProxyAccessor.startProxy(fd)
        } catch (e: Exception) {
            logger.log(e.stackTraceToString())
            throw e
        }
    }

    override fun stopProxy(): Int {
        ByeDpiProxyAccessor.maybeLoad()
        val currentFd = fd.load()
        if (currentFd < 0) {
            throw IllegalStateException("Proxy is not running")
        }

        val result = ByeDpiProxyAccessor.stopProxy(currentFd)
        logger.log("Stop proxy result int code: $result")
        if (result == 0) {
            fd.store(-1)
        } else {
            throw IllegalStateException("Failed to stop proxy")
        }
        return result
    }

    private fun createSocket(commandLineArguments: Array<String>): Int {
        val currentFd = fd.load()
        if (currentFd >= 0) {
            throw IllegalStateException("Proxy is already running")
        }

        val fd = ByeDpiProxyAccessor.createSocket(commandLineArguments)
        logger.log("File descriptor after create socket: $fd")
        if (fd < 0) {
            return -1
        }
        this.fd.store(fd)
        return fd
    }
}