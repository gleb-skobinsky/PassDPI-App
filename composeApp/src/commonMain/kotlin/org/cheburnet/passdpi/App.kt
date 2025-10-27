package org.cheburnet.passdpi

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.cheburnet.passdpi.navigation.Screens
import org.cheburnet.passdpi.presentation.mainScreen.MainScreen

@Composable
fun App() {
    val navController = rememberNavController()
    MaterialTheme {
        NavHost(
            navController = navController,
            startDestination = Screens.MainScreen
        ) {
            composable<Screens.MainScreen> {
                MainScreen()
            }
        }
    }
}
