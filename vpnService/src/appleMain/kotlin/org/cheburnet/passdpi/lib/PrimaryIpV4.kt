package org.cheburnet.passdpi.lib

import platform.posix.*
import kotlinx.cinterop.*
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.darwin.inet_ntop

@OptIn(ExperimentalForeignApi::class)
fun getPrimaryIPv4Address(): String? {
    memScoped {
        val ifap = alloc<CPointerVar<ifaddrs>>()
        if (getifaddrs(ifap.ptr) != 0) return null

        var result: String? = null
        var ptr = ifap.value

        while (ptr != null) {
            val ifa = ptr.pointed
            //val name = ifa.ifa_name?.toKString()
            val sa = ifa.ifa_addr?.pointed

            val flags = ifa.ifa_flags.toInt()
            val isUp = (flags and IFF_UP) != 0
            val isLoopback = (flags and IFF_LOOPBACK) != 0

            if (isUp && !isLoopback && sa?.sa_family?.toInt() == AF_INET) {
                val addr = sa.reinterpret<sockaddr_in>().sin_addr
                val buf = ByteArray(INET_ADDRSTRLEN) { 0.toByte() }
                inet_ntop(AF_INET, addr.ptr, buf.refTo(0), INET_ADDRSTRLEN.convert())
                result = buf.toKString()
                break
            }
            ptr = ifa.ifa_next
        }

        freeifaddrs(ifap.value)
        return result
    }
}
