package org.cheburnet.passdpi.byedpiinterop

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString

@OptIn(ExperimentalForeignApi::class)
object ByeDpiProxyAccessor {
    fun createSocket(args: Array<String>): Int = memScoped {
        val argc = args.size
        val nativeArgs = args.toPersistentCStringArray()
        return try {
            val res = parseArgs(
                argc = argc,
                argv = nativeArgs
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
            freeArgv(nativeArgs, argc)
        }
    }

    fun dumpArgv(argv: CPointer<CPointerVar<ByteVar>>, argc: Int) {
        println("argc = $argc, argv ptr = $argv")
        for (i in 0 until argc) {
            val p = argv[i]
            println("argv[$i] ptr = $p")
            println("argv[$i] = ${p?.toKString()}")
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


    fun Array<String>.toPersistentCStringArray(): CPointer<CPointerVar<ByteVar>> {
        val argc = size
        val argv = nativeHeap.allocArray<CPointerVar<ByteVar>>(argc)
        for (i in indices) {
            argv[i] = this[i].toNativeHeapCString()
        }
        return argv
    }

    fun String.toNativeHeapCString(): CPointer<ByteVar> {
        val bytes = this.encodeToByteArray()
        val ptr = nativeHeap.allocArray<ByteVar>(bytes.size + 1)
        for (i in bytes.indices) ptr[i] = bytes[i]
        ptr[bytes.size] = 0
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