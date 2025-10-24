package org.cheburnet.passdpi.lib

import kotlinx.coroutines.flow.StateFlow

interface PassDpiVPNServiceLauncher {
    val isRunning: StateFlow<ServiceLauncherState>
    suspend fun startService(args: String): Boolean

    suspend fun stopService(): Boolean
}

expect fun PassDpiVPNServiceLauncher(): PassDpiVPNServiceLauncher