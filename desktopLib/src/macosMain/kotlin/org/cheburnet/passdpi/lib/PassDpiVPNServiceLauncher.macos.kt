package org.cheburnet.passdpi.lib

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.cheburnet.passdpi.store.PassDpiOptionsStorage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PassDpiVPNServiceLauncherMacos(
    optionsStorage: PassDpiOptionsStorage,
) : PassDpiVPNServiceLauncher {
    private val _isRunning = MutableStateFlow(ServiceLauncherState.Stopped)
    override val isRunning = _isRunning.asStateFlow()

    private val tunnelProvider = TunnelProvider()

    override suspend fun startService(args: String): Boolean {
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    tunnelProvider.startTunnelWithOptions(null) { error ->
                        val isSuccess = error == null
                        continuation.resume(isSuccess)
                    }
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    override suspend fun stopService(): Boolean {
        // TODO: Not implemented
        return false
    }
}

actual fun PassDpiVPNServiceLauncher(): PassDpiVPNServiceLauncher = PassDpiVPNServiceLauncherMacos()