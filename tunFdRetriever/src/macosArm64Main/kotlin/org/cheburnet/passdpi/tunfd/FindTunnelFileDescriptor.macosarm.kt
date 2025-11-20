package org.cheburnet.passdpi.tunfd

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import platform.posix.AF_SYSTEM
import platform.posix.getpeername
import platform.posix.ioctl
import platform.posix.socklen_t
import platform.posix.socklen_tVar

@OptIn(ExperimentalForeignApi::class)
actual fun findTunnelFileDescriptor(): Int? = memScoped {
    // Allocate ctl_info
    val ctlInfo = alloc<ctl_info>()
    // Write control name into ctlInfo.ctl_name
    UTUN_CONTROL_NAME.cstr.place(ctlInfo.ctl_name)

    for (fd in 0..1024) {
        // sockaddr_ctl struct
        val addr = alloc<sockaddr_ctl>()
        val len: socklen_t = sizeOf<sockaddr_ctl>().convert()

        // getpeername wants sockaddr*, so reinterpret
        val ret = getpeername(
            fd,
            addr.ptr.reinterpret(),
            alloc<socklen_tVar>().apply { value = len }.ptr
        )

        if (ret != 0) continue
        if (addr.sc_family.toInt() != AF_SYSTEM) continue

        // Fetch UTUN control id on first match
        if (ctlInfo.ctl_id == 0.toUInt()) {
            val ioctlRet = ioctl(fd, CTLIOCGINFO, ctlInfo.ptr)
            if (ioctlRet != 0) {
                continue
            }
        }

        // Check if this file descriptor is an utun socket
        if (addr.sc_id == ctlInfo.ctl_id) {
            return fd
        }
    }

    return null
}