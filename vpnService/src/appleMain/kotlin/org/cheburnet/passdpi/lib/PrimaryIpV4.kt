package org.cheburnet.passdpi.lib

import kotlinx.cinterop.*
import kotlinx.cinterop.ptr
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.darwin.inet_ntop
import platform.posix.*

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

/**
 * Returns the IPv4 gateway address used for the system default route that is bound to the
 * requested interface (e.g. "en0"). Returns null on failure.
 *
 * This implementation shells out to `route get default` and parses the output.
 */
@OptIn(ExperimentalForeignApi::class)
fun getGatewayAddressForInterface(iface: String): String? {
    // Try "route get default" first and parse for "gateway:" and "interface:".
    val routeCmds = listOf("route -n get default", "route get default")
    for (cmd in routeCmds) {
        val fp = popen(cmd, "r") ?: continue
        memScoped {
            val bufSize = 1024
            val buffer = allocArray<ByteVar>(bufSize)

            var foundGateway: String? = null
            var foundInterface: String? = null

            while (fgets(buffer, bufSize.convert(), fp) != null) {
                val line = buffer.toKString().trim()
                if (line.isEmpty()) continue

                // lines look like: "   gateway: 192.168.0.1"
                if (line.startsWith("gateway:", ignoreCase = true) ||
                    line.contains("gateway:")) {
                    // try to extract IP after "gateway:"
                    val after = line.substringAfter("gateway:", missingDelimiterValue = "").trim()
                    if (after.isNotEmpty()) {
                        // sometimes additional text follows, take first token
                        foundGateway = after.split(Regex("\\s+"))[0]
                    }
                }

                // lines look like: "   interface: en0"
                if (line.startsWith("interface:", ignoreCase = true) ||
                    line.contains("interface:")) {
                    val after = line.substringAfter("interface:", missingDelimiterValue = "").trim()
                    if (after.isNotEmpty()) {
                        foundInterface = after.split(Regex("\\s+"))[0]
                    }
                }

                // If we have both, break early
                if (foundGateway != null && foundInterface != null) break
            }

            pclose(fp)

            if (foundGateway != null) {
                // If interface matched or interface line missing (best-effort), return gateway
                if (foundInterface == null || foundInterface == iface) {
                    return foundGateway
                }
                // else interface differs => fallthrough to netstat scan below
            }
        }
    }

    // Fallback: parse "netstat -rn -f inet" looking for a default line that references iface.
    // A sample netstat default line:
    // default            192.168.0.1        UGScIg             en0
    run {
        val cmd = "netstat -rn -f inet"
        val fp = popen(cmd, "r") ?: return null
        memScoped {
            val bufSize = 4096
            val buffer = allocArray<ByteVar>(bufSize)

            while (fgets(buffer, bufSize.convert(), fp) != null) {
                val line = buffer.toKString().trim()
                if (line.isEmpty()) continue
                // We look for lines starting with "default" and containing the iface at the end.
                // Split whitespace and analyze columns.
                val cols = line.split(Regex("\\s+"))
                if (cols.isEmpty()) continue
                if (cols[0] == "default") {
                    // Typical columns: Destination Gateway Flags Netif Expire
                    // We try to locate gateway and netif columns heuristically.
                    // If table has at least 5 columns, Gateway is cols[1], Netif usually cols[3] or last.
                    val gateway = if (cols.size >= 2) cols[1] else null
                    val netif = when {
                        cols.size >= 5 -> cols[3]
                        cols.size >= 2 -> cols.last()
                        else -> null
                    }
                    if (gateway != null && netif != null) {
                        if (netif == iface) {
                            pclose(fp)
                            return gateway
                        }
                    }
                }
            }
            pclose(fp)
        }
    }

    // Nothing found
    return null
}
