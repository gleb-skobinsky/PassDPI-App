package org.cheburnet.passdpi.tunfd

// from <sys/kern_control.h>
internal const val UTUN_CONTROL_NAME = "com.apple.net.utun_control"

expect fun findTunnelFileDescriptor(): Int?