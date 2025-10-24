package org.cheburnet.passdpi.lib

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PassDpiVPNServiceLauncherLinux : PassDpiVPNServiceLauncher {
    private val _isRunning = MutableStateFlow(ServiceLauncherState.Stopped)
    override val isRunning = _isRunning.asStateFlow()

    override suspend fun startService(args: String): Boolean {
        // TODO: Not implemented
        return false
    }

    override suspend fun stopService(): Boolean {
        // TODO: Not implemented
        return false
    }
}

actual fun PassDpiVPNServiceLauncher(): PassDpiVPNServiceLauncher = PassDpiVPNServiceLauncherLinux()