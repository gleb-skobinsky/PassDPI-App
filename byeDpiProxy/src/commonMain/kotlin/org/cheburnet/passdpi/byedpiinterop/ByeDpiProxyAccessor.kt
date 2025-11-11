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
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.set
import platform.posix.strdup

@OptIn(ExperimentalForeignApi::class)
object ByeDpiProxyAccessor {
    fun createSocket(args: Array<String>): Int {
        val argc = args.size
        val argv = args.toPersistentCStringArray()
        return try {
            val res = parseArgs(
                argc = argc,
                argv = argv
            )
            if (res < 0) {
                return -1
            }

            val fd = listenSocket()
            if (fd < 0) {
                return -1
            }
            fd
        } finally {
            freeArgv(argv, argc)
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
        val argv = nativeHeap.allocArray<CPointerVar<ByteVar>>(argc)
        for (i in indices) {
            argv[i] = this[i].toNativeHeapCString()
        }
        return argv
    }

    fun String.toNativeHeapCString(): CPointer<ByteVar> {
        val bytes = this.encodeToByteArray() // UTF-8 bytes
        val ptr = nativeHeap.allocArray<ByteVar>(bytes.size + 1) // +1 for NUL
        for (i in bytes.indices) {
            ptr[i] = bytes[i] // Byte -> ByteVar assignment
        }
        ptr[bytes.size] = 0 // null terminator
        return ptr
    }

    private fun freeArgv(argv: CPointer<CPointerVar<ByteVar>>, argc: Int) {
        for (i in 0 until argc) {
            argv[i]?.let { nativeHeap.free(it.rawValue) }
        }
        nativeHeap.free(argv.rawValue)
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