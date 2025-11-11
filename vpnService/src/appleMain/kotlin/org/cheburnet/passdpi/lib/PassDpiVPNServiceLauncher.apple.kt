package org.cheburnet.passdpi.lib

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.cheburnet.passdpi.store.PassDpiOptionsStorage
import platform.Foundation.NSError
import platform.NetworkExtension.NETunnelProviderManager
import platform.NetworkExtension.NETunnelProviderProtocol
import platform.NetworkExtension.NEVPNStatusConnected
import platform.NetworkExtension.NEVPNStatusDisconnected
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

internal const val OPTIONS_CMD_LINE_ARGS = "OPTIONS_CMD_LINE_ARGS_KEY"

internal const val OPTIONS_PORT_KEY = "VPN_OPTIONS_PORT_KEY"
internal const val OPTIONS_DNS_IP = "OPTIONS_DNS_IP_KEY"

internal const val OPTIONS_ENABLE_IPV6 = "OPTIONS_ENABLE_IPV6_KEY"

class PassDpiVPNServiceLauncherMacos(
    private val optionsStorage: PassDpiOptionsStorage,
) : PassDpiVPNServiceLauncher {

    private val mutex = Mutex()
    private val singleThreadedDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val statusFromInteraction = MutableStateFlow(ServiceLauncherState.Stopped)

    override val connectionState = merge(
        statusFromInteraction,
        observeConnectionStatus()
    )

    private var currentTunnelManager: NETunnelProviderManager? = null


    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun startService() {
        return withContext(singleThreadedDispatcher) {
            mutex.withLock(owner = this) {
                statusFromInteraction.value = ServiceLauncherState.Loading
                val tunnelManager = loadOrCreateManager()
                currentTunnelManager = tunnelManager
                val commandLineArgs = optionsStorage.getCommandLineArgs()
                val options = optionsStorage.getVpnOptions()
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
                                        .startVPNTunnelWithOptions(
                                            options = mapOf(
                                                OPTIONS_CMD_LINE_ARGS to commandLineArgs,
                                                OPTIONS_PORT_KEY to options.port,
                                                OPTIONS_DNS_IP to options.dnsIp,
                                                OPTIONS_ENABLE_IPV6 to options.enableIpV6.toString()
                                            ),
                                            andReturnError = errorPtr.ptr
                                        )
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
                statusFromInteraction.value = ServiceLauncherState.Running
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeConnectionStatus(): Flow<ServiceLauncherState> = callbackFlow {
        val listenerJob = launch {
            while (isActive) {
                val manager = currentTunnelManager
                send(manager?.connection?.status.toState())
                delay(1.seconds)
            }
        }
        awaitClose {
            listenerJob.cancel()
        }
    }


    private fun Long?.toState(): ServiceLauncherState {
        return when (this) {
            null -> ServiceLauncherState.Stopped
            NEVPNStatusConnected -> ServiceLauncherState.Running
            NEVPNStatusDisconnected -> ServiceLauncherState.Stopped
            else -> ServiceLauncherState.Loading
        }
    }

    private fun NSError.toException() = IllegalStateException(localizedDescription)

    override suspend fun stopService() {
        return mutex.withLock(owner = this) {
            loadOrCreateManager().connection.stopVPNTunnel()
            statusFromInteraction.value = ServiceLauncherState.Stopped
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
        onDemandEnabled = true
        setEnabled(true)
        val protocol = NETunnelProviderProtocol()
        protocol.providerBundleIdentifier = PROVIDER_IDENTIFIER
        protocol.serverAddress = PROVIDER_HOST
        protocolConfiguration = protocol
        localizedDescription = PROVIDER_NAME
    }

    companion object {
        private const val PROVIDER_IDENTIFIER = "org.cheburnet.passdpi.macosApp.passDpiVpn"
        private const val PROVIDER_HOST = "127.0.0.1"
        private const val PROVIDER_NAME = "PassDPI VPN"
    }
}

actual fun PassDpiVPNServiceLauncher(
    optionsStorage: PassDpiOptionsStorage,
): PassDpiVPNServiceLauncher = PassDpiVPNServiceLauncherMacos(optionsStorage)