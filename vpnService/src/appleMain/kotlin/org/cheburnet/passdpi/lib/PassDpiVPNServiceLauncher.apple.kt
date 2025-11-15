package org.cheburnet.passdpi.lib

import co.touchlab.kermit.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

class PassDpiVPNServiceLauncherApple(
    private val optionsStorage: PassDpiOptionsStorage,
) : PassDpiVPNServiceLauncher {
    private val logger = Logger.withTag("PassDpiVPNServiceLauncherApple")

    private val mutex = Mutex()
    private val backgroundDispatcher = Dispatchers.IO.limitedParallelism(2)
    private val statusFromInteraction = MutableStateFlow(ServiceLauncherState.Stopped)

    override val connectionState = merge(
        statusFromInteraction,
        observeConnectionStatus()
    )

    private val proxyScope = CoroutineScope(backgroundDispatcher)
    private var proxyJob: Job? = null

    private var currentTunnelManager: NETunnelProviderManager? = null

    private val proxy = ByeDpiProxyManager {
        println("PassDpiVPNServiceLauncher $it")
    }

    override suspend fun startService() {
        //mutex.withLock(owner = this) {
        val commandLineArgs = optionsStorage.getCommandLineArgs()
        startServiceWithManager(commandLineArgs)
        logger.d("Service start complete")
        startProxy(commandLineArgs)
        logger.d("Proxy start complete")
        //}
    }

    override suspend fun stopService() {
        return mutex.withLock(owner = this) {
            withContext(backgroundDispatcher) {
                stopProxySafe()
                proxyJob?.cancel()
                loadOrCreateManager().connection.stopVPNTunnel()
                statusFromInteraction.value = ServiceLauncherState.Stopped
            }
        }
    }

    private fun startProxy(args: String) {
        proxyJob = proxyScope.launch {
            try {
                withContext(backgroundDispatcher) {
                    proxy.startProxy(args)
                }
            } catch (e: Exception) {
                ensureActive()
                logger.d(
                    messageString = "Failed to start proxy",
                    throwable = e
                )
            }
        }
    }

    private suspend fun stopProxySafe() {
        try {
            proxy.stopProxy()
        } catch (e: Exception) {
            currentCoroutineContext()[Job]?.ensureActive()
            logger.d(
                messageString = "Failed to stop proxy",
                throwable = e
            )
        }
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private suspend fun startServiceWithManager(args: String) {
        withContext(backgroundDispatcher) {
            mutex.withLock(owner = this) {
                statusFromInteraction.value = ServiceLauncherState.Loading

                val tunnelManager = loadOrCreateManager()
                currentTunnelManager = tunnelManager

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
                                                OPTIONS_CMD_LINE_ARGS to args,
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
): PassDpiVPNServiceLauncher = PassDpiVPNServiceLauncherApple(optionsStorage)