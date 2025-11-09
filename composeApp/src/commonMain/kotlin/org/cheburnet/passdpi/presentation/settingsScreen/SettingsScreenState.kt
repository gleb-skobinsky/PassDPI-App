package org.cheburnet.passdpi.presentation.settingsScreen

import androidx.compose.runtime.Immutable

@Immutable
data class SettingsScreenState(
    val commandLineArgs: String,
) {
    companion object {
        val Initial = SettingsScreenState(
            commandLineArgs = ""
        )
    }
}
