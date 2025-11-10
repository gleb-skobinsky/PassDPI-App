package org.cheburnet.passdpi.byedpiinterop

import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ptr
import org.cheburnet.passdpi.byedpi.listen_socket
import org.cheburnet.passdpi.byedpi.params_
import org.cheburnet.passdpi.byedpi.parse_args
import org.cheburnet.passdpi.byedpi.reset_params
import org.cheburnet.passdpi.byedpi.start_event_loop
import platform.posix.SHUT_RDWR
import platform.posix.shutdown

@OptIn(ExperimentalForeignApi::class)
actual fun parseArgs(
    argc: Int,
    argv: CValuesRef<CPointerVarOf<CPointer<ByteVarOf<Byte>>>>?,
): Int = parse_args(argc, argv)

@OptIn(ExperimentalForeignApi::class)
actual fun listenSocket(): Int = listen_socket(params_.laddr.ptr)

@OptIn(ExperimentalForeignApi::class)
actual fun startEventLoop(fd: Int): Int = start_event_loop(fd)

actual fun shutDown(fd: Int): Int = shutdown(fd, SHUT_RDWR)

@OptIn(ExperimentalForeignApi::class)
actual fun resetParams() = reset_params()