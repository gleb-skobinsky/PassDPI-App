package org.cheburnet.passdpi.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

val LocalNavigator = staticCompositionLocalOf<NavHostController> { error("No NavHostController provided") }