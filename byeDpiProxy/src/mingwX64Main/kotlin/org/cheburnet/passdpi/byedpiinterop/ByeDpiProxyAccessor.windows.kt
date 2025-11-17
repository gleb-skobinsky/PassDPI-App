package org.cheburnet.passdpi.byedpiinterop

import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import org.cheburnet.passdpi.byedpi.default_params
import org.cheburnet.passdpi.byedpi.listen_socket
import org.cheburnet.passdpi.byedpi.params_
import org.cheburnet.passdpi.byedpi.parse_args
import org.cheburnet.passdpi.byedpi.reset_params
import org.cheburnet.passdpi.byedpi.event_loop
import org.cheburnet.passdpi.byedpi.params
import org.cheburnet.passdpi.byedpi.sockaddr_ina
import platform.posix.memcpy
import platform.posix.shutdown

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

actual fun shutDown(fd: Int): Int = shutdown(fd.toULong(), 2) // TODO: Fixme

@OptIn(ExperimentalForeignApi::class)
actual fun resetParams() = reset_params()

@OptIn(ExperimentalForeignApi::class)
actual fun saveParamsToDefaultParams() {
    memcpy(
        _Dst = default_params.ptr,
        _Src = params_.ptr,
        _Size = sizeOf<params>().convert()
    )
}