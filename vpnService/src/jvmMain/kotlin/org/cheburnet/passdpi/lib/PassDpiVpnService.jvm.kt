package org.cheburnet.passdpi.lib

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.cheburnet.passdpi.store.PassDpiOptionsStorage

class PassDpiVPNServiceLauncherWindows : PassDpiVPNServiceLauncher {
    private val _isRunning = MutableStateFlow(ServiceLauncherState.Stopped)
    override val connectionState = _isRunning.asStateFlow()

    override suspend fun startService() {
        // TODO: Not implemented
    }

    override suspend fun stopService() {
        // TODO: Not implemented
    }
}

actual fun PassDpiVPNServiceLauncher(
    optionsStorage: PassDpiOptionsStorage,
): PassDpiVPNServiceLauncher = PassDpiVPNServiceLauncherWindows()