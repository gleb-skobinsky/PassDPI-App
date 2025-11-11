package org.cheburnet.passdpi.byedpiinterop

import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.set
import platform.posix.strdup

@OptIn(ExperimentalForeignApi::class)
object ByeDpiProxyAccessor {
    fun createSocket(args: Array<String>): Int {
        return memScoped {
            val argc = args.size

            val res = parseArgs(
                argc = argc,
                argv = args.toCArray(this)
            )
            if (res < 0) {
                return@memScoped -1
            }

            val fd = listenSocket()
            if (fd < 0) {
                return@memScoped -1
            }
            fd
        }
    }

    fun startProxy(fd: Int): Int {
        if (startEventLoop(fd) < 0) {
            return -1
        }
        return 0
    }

    fun stopProxy(fd: Int): Int {
        val res = shutDown(fd)
        resetParams()
        if (res < 0) {
            return -1
        }
        return 0
    }


    private fun Array<String>.toCStringArray(
        memScope: MemScope,
    ): CPointer<CPointerVar<ByteVar>> {
        val argc = this.size
        val argv = memScope.allocArray<CPointerVar<ByteVar>>(argc)

        for (i in indices) {
            //argv[i] = this[i].cstr.getPointer(memScope)
            argv[i] = strdup(this[i])
        }
        argv[argc] = null
        return argv
    }

    fun Array<String>.toCArray(autofreeScope: AutofreeScope): CPointer<CPointerVar<ByteVar>> =
        autofreeScope.allocArrayOf(this.map { it.cstr.getPointer(autofreeScope) })


    fun Array<String>.toPersistentCStringArray(): CPointer<CPointerVar<ByteVar>> {
        val argc = size
        val argv = nativeHeap.allocArray<CPointerVar<ByteVar>>(argc + 1)
        for (i in indices) {
            argv[i] = strdup(this[i])
        }
        argv[argc] = null
        return argv
    }
}

@OptIn(ExperimentalForeignApi::class)
expect fun parseArgs(
    argc: Int,
    argv: CValuesRef<CPointerVarOf<CPointer<ByteVarOf<Byte>>>>?,
): Int

expect fun listenSocket(): Int

expect fun startEventLoop(fd: Int): Int

expect fun shutDown(fd: Int): Int

expect fun resetParams()