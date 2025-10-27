package org.cheburnet.passdpi.lib

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.cheburnet.passdpi.store.PassDpiOptionsStorage
import platform.NetworkExtension.NEProviderStopReasonUserSwitch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PassDpiVPNServiceLauncherMacos(
    optionsStorage: PassDpiOptionsStorage,
) : PassDpiVPNServiceLauncher {
    private val _isRunning = MutableStateFlow(ServiceLauncherState.Stopped)
    override val isRunning = _isRunning.asStateFlow()

    private val mutex = Mutex()
    private val singleThreadedDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val tunnelProvider by lazy { TunnelProvider(optionsStorage) }

    override suspend fun startService(): Boolean {
        return withContext(singleThreadedDispatcher) {
            mutex.withLock {
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
    }

    override suspend fun stopService(): Boolean {
        return mutex.withLock {
            suspendCancellableCoroutine { cont ->
                tunnelProvider.stopTunnelWithReason(NEProviderStopReasonUserSwitch) {
                    cont.resume(true)
                }
            }
        }
    }
}

actual fun PassDpiVPNServiceLauncher(
    optionsStorage: PassDpiOptionsStorage,
): PassDpiVPNServiceLauncher = PassDpiVPNServiceLauncherMacos(optionsStorage)