package org.cheburnet.passdpi.byedpiinterop

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import org.cheburnet.passdpi.byedpi.listen_socket
import org.cheburnet.passdpi.byedpi.params_
import org.cheburnet.passdpi.byedpi.parse_args
import org.cheburnet.passdpi.byedpi.reset_params
import platform.posix.SHUT_RDWR
import platform.posix.shutdown

@OptIn(ExperimentalForeignApi::class)
actual object ByeDpiProxyAccessor {
    actual fun startProxy(args: Array<String>): Int {
        return memScoped {
            val argc = args.size
            val res = parse_args(argc = argc, argv = args.toCStringArray(this))
            if (res < 0) {
                return@memScoped -1
            }

            val fd = listen_socket(params_.laddr.ptr)
            if (fd < 0) {
                return@memScoped -1
            }
            fd
        }
    }

    actual fun stopProxy(fd: Int): Int {
        val res = shutdown(fd, SHUT_RDWR)
        reset_params()
        if (res < 0) {
            return -1
        }
        return 0
    }



    fun Array<String>.toCStringArray(memScope: MemScope): CValuesRef<CPointerVar<ByteVar>> {
        val argc = this.size
        val argv = memScope.allocArray<CPointerVar<ByteVar>>(argc)

        for (i in indices) {
            val cstr = this[i].cstr.getPointer(memScope)
            argv[i] = cstr
        }

        return argv
    }
}