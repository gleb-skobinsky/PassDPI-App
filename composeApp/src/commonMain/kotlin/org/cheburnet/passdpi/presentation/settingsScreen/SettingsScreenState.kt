package org.cheburnet.passdpi.presentation.settingsScreen

import androidx.compose.runtime.Immutable
import org.cheburnet.passdpi.store.DEFAULT_PROXY_IP

@Immutable
data class SettingsScreenState(
    val commandLineArgs: String,
    val proxyIp: String,
) {
    companion object {
        val Initial = SettingsScreenState(
            commandLineArgs = "",
            proxyIp = DEFAULT_PROXY_IP
        )
    }
}
