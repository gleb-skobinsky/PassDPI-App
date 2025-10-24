package org.cheburnet.passdpi.lib

interface PassDpiVPNServiceLauncher {
    suspend fun startService(args: String): Boolean

    suspend fun stopService(): Boolean
}

expect fun PassDpiVPNServiceLauncher(): PassDpiVPNServiceLauncher