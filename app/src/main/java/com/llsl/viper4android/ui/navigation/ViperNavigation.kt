package com.llsl.viper4android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.llsl.viper4android.headunit.VehicleAudioScreen
import com.llsl.viper4android.ui.screens.main.MainScreen

@Composable
fun ViperNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main",
    ) {
        composable("main") {
            MainScreen(onOpenVehicle = { navController.navigate("vehicle") })
        }
        composable("vehicle") {
            VehicleAudioScreen(onBack = { navController.popBackStack() })
        }
    }
}
