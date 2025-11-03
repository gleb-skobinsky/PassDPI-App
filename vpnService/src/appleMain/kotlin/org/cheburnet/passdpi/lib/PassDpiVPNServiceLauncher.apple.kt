package org.cheburnet.passdpi.lib

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.cheburnet.passdpi.store.PassDpiOptionsStorage
import platform.NetworkExtension.NETunnelProviderManager
import platform.NetworkExtension.NETunnelProviderProtocol
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val PROVIDER_IDENTIFIER = "org.cheburnet.passdpi.macosApp.passDpiVpn.appex"
private const val PROVIDER_HOST = "127.0.0.1"
private const val PROVIDER_NAME = "PassDPI VPN"

class PassDpiVPNServiceLauncherMacos() : PassDpiVPNServiceLauncher {
    private val _isRunning = MutableStateFlow(ServiceLauncherState.Stopped)
    override val isRunning = _isRunning.asStateFlow()

    private val mutex = Mutex()
    private val singleThreadedDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val tunnelProvider = TunnelProvider()

    private val tunnelManager by lazy {
        NETunnelProviderManager().apply {
            val proto = NETunnelProviderProtocol()
            proto.providerBundleIdentifier = PROVIDER_IDENTIFIER
            proto.serverAddress = PROVIDER_HOST
            protocolConfiguration = proto
            localizedDescription = PROVIDER_NAME
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun startService(): Boolean {
        return withContext(singleThreadedDispatcher) {
            mutex.withLock(owner = this) {
                _isRunning.value = ServiceLauncherState.Loading
                val isSuccess = suspendCancellableCoroutine { continuation ->
                    tunnelManager.saveToPreferencesWithCompletionHandler { error ->
                        error?.let {
                            continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                        }
                        tunnelManager.loadFromPreferencesWithCompletionHandler { error ->
                            error?.let {
                                continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                            }
                            try {
                                tunnelManager.connection.startVPNTunnelAndReturnError(null)
                                continuation.resume(true)
                            } catch (e: Exception) {
                                continuation.resumeWithException(e)
                            }
                        }
                    }
                }
                _isRunning.value = ServiceLauncherState.Running
                isSuccess
            }
        }
    }

    override suspend fun stopService(): Boolean {
        return mutex.withLock(owner = this) {
            tunnelManager.connection.stopVPNTunnel()
            _isRunning.value = ServiceLauncherState.Stopped
            true
        }
    }
}

actual fun PassDpiVPNServiceLauncher(
    optionsStorage: PassDpiOptionsStorage,
): PassDpiVPNServiceLauncher = PassDpiVPNServiceLauncherMacos()