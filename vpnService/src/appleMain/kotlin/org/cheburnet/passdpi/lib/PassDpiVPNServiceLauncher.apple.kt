package org.cheburnet.passdpi.lib

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.cheburnet.passdpi.store.PassDpiOptionsStorage
import platform.Foundation.NSError
import platform.NetworkExtension.NETunnelProviderManager
import platform.NetworkExtension.NETunnelProviderProtocol
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PassDpiVPNServiceLauncherMacos() : PassDpiVPNServiceLauncher {
    private val _isRunning = MutableStateFlow(ServiceLauncherState.Stopped)
    override val isRunning = _isRunning.asStateFlow()

    private val mutex = Mutex()
    private val singleThreadedDispatcher = Dispatchers.IO.limitedParallelism(1)


    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun startService() {
        return withContext(singleThreadedDispatcher) {
            mutex.withLock(owner = this) {
                _isRunning.value = ServiceLauncherState.Loading
                val tunnelManager = loadOrCreateManager()
                suspendCancellableCoroutine { continuation ->
                    tunnelManager.loadFromPreferencesWithCompletionHandler { error ->
                        error?.let {
                            continuation.resumeWithException(error.toException())
                        }
                        tunnelManager.saveToPreferencesWithCompletionHandler { error ->
                            error?.let {
                                continuation.resumeWithException(error.toException())
                            }
                            try {
                                memScoped {
                                    val errorPtr: ObjCObjectVar<NSError?> =
                                        alloc<ObjCObjectVar<NSError?>>()
                                    val isSuccess = tunnelManager.connection
                                        .startVPNTunnelAndReturnError(errorPtr.ptr)
                                    val error = errorPtr.value
                                    when {
                                        !isSuccess -> continuation.resumeWithException(
                                            IllegalStateException("Failed to start VPN service")
                                        )

                                        error != null -> continuation.resumeWithException(error.toException())
                                        else -> continuation.resume(Unit)
                                    }
                                }
                            } catch (e: Exception) {
                                continuation.resumeWithException(e)
                            }
                        }
                    }
                }
                _isRunning.value = ServiceLauncherState.Running
            }
        }
    }

    private fun NSError.toException() = IllegalStateException(localizedDescription)

    override suspend fun stopService() {
        return mutex.withLock(owner = this) {
            loadOrCreateManager().connection.stopVPNTunnel()
            _isRunning.value = ServiceLauncherState.Stopped
        }
    }

    private suspend fun loadOrCreateManager(): NETunnelProviderManager {
        return suspendCancellableCoroutine { continuation ->
            NETunnelProviderManager.loadAllFromPreferencesWithCompletionHandler { managers, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toException())
                    return@loadAllFromPreferencesWithCompletionHandler
                }

                val existing = managers?.firstOrNull() as? NETunnelProviderManager
                if (existing != null) {
                    continuation.resume(existing)
                } else {
                    val newManager = createTunnelManager()
                    newManager.saveToPreferencesWithCompletionHandler { saveError ->
                        if (saveError != null) {
                            continuation.resumeWithException(saveError.toException())
                        } else {
                            continuation.resume(newManager)
                        }
                    }
                }
            }
        }
    }


    private fun createTunnelManager() = NETunnelProviderManager().apply {
        // val options = runBlocking { optionsStorage.getVpnOptions() }
        onDemandEnabled = true
        setEnabled(true)
        val protocol = NETunnelProviderProtocol()
        protocol.providerBundleIdentifier = PROVIDER_IDENTIFIER
        protocol.serverAddress = PROVIDER_HOST
        /*
        protocol.providerConfiguration = mapOf(
            "port" to options.port,
            "server" to PROVIDER_HOST,
            "ip" to options.dnsIp,
            "subnet" to PROVIDER_SUBNET,
        )
         */
        protocolConfiguration = protocol
        localizedDescription = PROVIDER_NAME
    }

    companion object {
        private const val PROVIDER_IDENTIFIER = "org.cheburnet.passdpi.macosApp.passDpiVpn"
        private const val PROVIDER_HOST = "127.0.0.1"
        private const val PROVIDER_NAME = "PassDPI VPN"
        private const val PROVIDER_SUBNET = "255.255.255.0"
    }
}

actual fun PassDpiVPNServiceLauncher(
    optionsStorage: PassDpiOptionsStorage,
): PassDpiVPNServiceLauncher = PassDpiVPNServiceLauncherMacos()