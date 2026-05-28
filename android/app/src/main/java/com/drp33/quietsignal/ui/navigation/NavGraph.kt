package com.drp33.quietsignal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.drp33.quietsignal.ui.screens.ElderlyScreen
import com.drp33.quietsignal.ui.screens.ThankYouScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.ELDERLY
    ){
        composable(Routes.ELDERLY) {
            ElderlyScreen(
                onOkayClick = {
                    navController.navigate(Routes.THANK_YOU) {
                        popUpTo(Routes.ELDERLY) { inclusive = true}
                    }
                },
                onNotTodayClick = {
                    navController.navigate(Routes.THANK_YOU)
                },
                onReplyLaterClick = {
                    navController.navigate(Routes.THANK_YOU)
                }
            )
        }

        composable(Routes.THANK_YOU) {
            ThankYouScreen()
        }

    }
}