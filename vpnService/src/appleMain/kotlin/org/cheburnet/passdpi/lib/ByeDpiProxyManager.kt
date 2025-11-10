package org.cheburnet.passdpi.lib

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ByeDpiProxyManager {
    private val mutex = Mutex()
    private var fd = -1

    suspend fun startProxy(commandLineArguments: Array<String>): Int {
        val fd = createSocket(commandLineArguments)
        if (fd < 0) {
            return -1
        }
        return jniStartProxy(fd)
    }

    private suspend fun createSocket(commandLineArguments: Array<String>): Int {
        return mutex.withLock {
            if (fd >= 0) {
                throw IllegalStateException("Proxy is already running")
            }

            val fd = createSocketFromPreferences(preferences)
            if (fd < 0) {
                return -1
            }
            this.fd = fd
            fd
        }
    }


}