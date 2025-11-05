package org.cheburnet.passdpi.byedpiinterop

import kotlinx.cinterop.ExperimentalForeignApi
import org.cheburnet.passdpi.byedpi.start_event_loop

@OptIn(ExperimentalForeignApi::class)
actual object ByeDpiProxyAccessor {
    actual fun startProxy(fd: Int) {
        start_event_loop(fd)
    }

    actual fun stopProxy() {}
}