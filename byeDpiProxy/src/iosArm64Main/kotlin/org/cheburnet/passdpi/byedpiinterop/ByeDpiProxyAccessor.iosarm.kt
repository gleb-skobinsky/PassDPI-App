package org.cheburnet.passdpi.byedpiinterop

import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import org.cheburnet.passdpi.byedpi.listen_socket
import org.cheburnet.passdpi.byedpi.params_
import org.cheburnet.passdpi.byedpi.parse_args
import org.cheburnet.passdpi.byedpi.reset_params
import org.cheburnet.passdpi.byedpi.event_loop
import org.cheburnet.passdpi.byedpi.sockaddr_ina
import platform.posix.SHUT_RDWR
import platform.posix.close
import platform.posix.errno
import platform.posix.shutdown
import platform.posix.strerror

@OptIn(ExperimentalForeignApi::class)
actual fun parseArgs(
    argc: Int,
    argv: CValuesRef<CPointerVarOf<CPointer<ByteVarOf<Byte>>>>?,
): Int = parse_args(argc, argv)

@OptIn(ExperimentalForeignApi::class)
actual fun listenSocket(): Int {
    val laddrPtr = params_.laddr.ptr.reinterpret<sockaddr_ina>()
    return listen_socket(laddrPtr)
}

@OptIn(ExperimentalForeignApi::class)
actual fun startEventLoop(fd: Int): Int = event_loop(fd)

@OptIn(ExperimentalForeignApi::class)
actual fun shutDown(fd: Int): Int {
    val res = close(fd)
    if (res < 0) {
        throw SocketShutdownException(
            strerror(errno)?.toKString().orEmpty()
        )
    }
    return res
}

@OptIn(ExperimentalForeignApi::class)
actual fun resetParams() = reset_params()