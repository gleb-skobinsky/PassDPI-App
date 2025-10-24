package org.cheburnet.passdpi.lib

interface PassDpiVPNServiceLauncher {
    fun startService(args: String): Boolean

    fun stopService(): Boolean
}

expect fun PassDpiVPNServiceLauncher(): PassDpiVPNServiceLauncher