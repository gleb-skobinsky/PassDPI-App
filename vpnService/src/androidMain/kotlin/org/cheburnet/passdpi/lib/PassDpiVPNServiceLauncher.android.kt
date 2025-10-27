package org.cheburnet.passdpi.lib

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.cheburnet.passdpi.store.PassDpiOptionsStorage

class PassDpiVPNServiceLauncherAndroid : PassDpiVPNServiceLauncher {
    private val _isRunning = MutableStateFlow(ServiceLauncherState.Stopped)
    override val isRunning = _isRunning.asStateFlow()

    override suspend fun startService(): Boolean {
        // TODO: Not implemented
        return false
    }

    override suspend fun stopService(): Boolean {
        // TODO: Not implemented
        return false
    }
}

actual fun PassDpiVPNServiceLauncher(
    optionsStorage: PassDpiOptionsStorage,
): PassDpiVPNServiceLauncher = PassDpiVPNServiceLauncherAndroid()