package org.cheburnet.passdpi.lib

import kotlinx.coroutines.flow.Flow
import org.cheburnet.passdpi.store.PassDpiOptionsStorage

interface PassDpiVPNServiceLauncher {
    val connectionState: Flow<ServiceLauncherState>
    suspend fun startService()

    suspend fun stopService()
}

expect fun PassDpiVPNServiceLauncher(
    optionsStorage: PassDpiOptionsStorage
): PassDpiVPNServiceLauncher