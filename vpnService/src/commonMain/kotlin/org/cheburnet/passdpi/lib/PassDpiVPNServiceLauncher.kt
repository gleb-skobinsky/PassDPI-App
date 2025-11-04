package org.cheburnet.passdpi.lib

import kotlinx.coroutines.flow.StateFlow
import org.cheburnet.passdpi.store.PassDpiOptionsStorage

interface PassDpiVPNServiceLauncher {
    val isRunning: StateFlow<ServiceLauncherState>
    suspend fun startService()

    suspend fun stopService()
}

expect fun PassDpiVPNServiceLauncher(
    optionsStorage: PassDpiOptionsStorage
): PassDpiVPNServiceLauncher