package org.cheburnet.passdpi.presentation.mainScreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.cheburnet.passdpi.lib.PassDpiVPNServiceLauncher
import org.cheburnet.passdpi.lib.ServiceLauncherState

class MainViewModel(
    private val vpnLauncher: PassDpiVPNServiceLauncher
) : ViewModel() {

    private val _state = MutableStateFlow(MainScreenState.Initial)
    val state = _state.asStateFlow()

    private var toggleVpnJob: Job? = null
    fun toggleVpn() {
        if (toggleVpnJob?.isActive == true) return
        toggleVpnJob = viewModelScope.launch {
            val currentState = _state.value
            when (currentState.vpnStatus) {
                ServiceLauncherState.Stopped -> vpnLauncher.startService()
                ServiceLauncherState.Running -> vpnLauncher.stopService()
                ServiceLauncherState.Loading -> Unit
            }
        }
    }

    init {
        observeVpnStatus()
    }

    private fun observeVpnStatus() {
        vpnLauncher.isRunning.onEach { status ->
            _state.update { it.copy(vpnStatus = status) }
        }.launchIn(viewModelScope)
    }
}