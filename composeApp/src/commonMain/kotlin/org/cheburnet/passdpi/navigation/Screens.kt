package org.cheburnet.passdpi.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Screens {
    @Serializable
    data object MainScreen : Screens
}