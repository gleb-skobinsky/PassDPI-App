package org.cheburnet.passdpi.lib

import org.cheburnet.passdpi.byedpiinterop.ByeDpiProxyAccessor
import org.cheburnet.passdpi.store.EditableSettings
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class ByeDpiProxyManager(
    private val logger: PassDpiLogger,
) {

    private val fd = AtomicInt(-1)

    fun startProxy(settings: EditableSettings) = startProxy(
        proxyIp = settings.proxyIp,
        commandLineArguments = cmdToArgs(settings.commandLineArgs)
    )

    fun startProxy(
        proxyIp: String,
        commandLineArguments: MutableList<String>,
    ): Int {
        // Exclude ciadpi from search
        if (!commandLineArguments.drop(1).any { "i" in it || "ip" in it }) {
            logger.log("No ip found. Adding ip: $proxyIp")
//            commandLineArguments.addAll(listOf("--ip", proxyIp))
        }
        try {
            logger.log("Right before socket create $commandLineArguments")
            val fd = createSocket(commandLineArguments.toTypedArray())
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