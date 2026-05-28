package com.drp33.quietsignal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.drp33.quietsignal.data.remote.RetroFitProvider
import com.drp33.quietsignal.data.repo.CheckInRepository
import com.drp33.quietsignal.data.repo.CheckInRepositoryImpl
import com.drp33.quietsignal.ui.screens.ElderlyScreen
import com.drp33.quietsignal.ui.screens.ThankYouScreen
import com.drp33.quietsignal.viewmodels.ElderlyViewModel
import com.drp33.quietsignal.viewmodels.ElderlyViewModelFactory

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    val repository = remember {
        CheckInRepositoryImpl(RetroFitProvider.checkInAPI)
    }

    val elderlyViewModel: ElderlyViewModel = viewModel(factory = ElderlyViewModelFactory(repository))

    NavHost(
        navController = navController,
        startDestination = Routes.ELDERLY
    ){
        composable(Routes.ELDERLY) {
            ElderlyScreen(
                onOkayClick = {
                    elderlyViewModel.onOkayClick(1) {
                        navController.navigate(Routes.THANK_YOU) {
                            popUpTo(Routes.ELDERLY) { inclusive = true}
                        }
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