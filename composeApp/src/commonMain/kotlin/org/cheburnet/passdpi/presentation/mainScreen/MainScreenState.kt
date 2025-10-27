package org.cheburnet.passdpi.presentation.mainScreen

import androidx.compose.runtime.Immutable
import org.cheburnet.passdpi.lib.ServiceLauncherState

@Immutable
data class MainScreenState(
    val vpnStatus: ServiceLauncherState,
) {
    companion object {
        val Initial = MainScreenState(
            vpnStatus = ServiceLauncherState.Stopped
        )
    }
}