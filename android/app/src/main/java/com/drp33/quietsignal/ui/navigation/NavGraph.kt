package com.drp33.quietsignal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.drp33.quietsignal.data.remote.RetroFitProvider
import com.drp33.quietsignal.data.repo.CheckInRepositoryImpl
import com.drp33.quietsignal.model.UserRole
import com.drp33.quietsignal.ui.screens.AdultScreen
import com.drp33.quietsignal.ui.screens.ElderlyScreen
import com.drp33.quietsignal.ui.screens.RoleSelectScreen
import com.drp33.quietsignal.ui.screens.ThankYouScreen
import com.drp33.quietsignal.viewmodels.AdultViewModel
import com.drp33.quietsignal.viewmodels.AdultViewModelFactory
import com.drp33.quietsignal.viewmodels.ElderlyViewModel
import com.drp33.quietsignal.viewmodels.ElderlyViewModelFactory

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    val repository = remember {
        CheckInRepositoryImpl(RetroFitProvider.checkInAPI)
    }

    val elderlyViewModel: ElderlyViewModel = viewModel(factory = ElderlyViewModelFactory(repository))
    val adultViewModel: AdultViewModel = viewModel(factory = AdultViewModelFactory(repository))

    NavHost(
        navController = navController,
        startDestination = Routes.ROLE_SELECT
    ){

        composable(Routes.ROLE_SELECT) {
            RoleSelectScreen { role ->

                when (role) {
                    UserRole.SADIE -> {
                        adultViewModel.postFCMToken(2)
                        navController.navigate(Routes.ADULT) {
                            popUpTo(Routes.ROLE_SELECT) {inclusive = true}
                        }
                    }
                    UserRole.NORMAN -> {
                        elderlyViewModel.postFCMToken(1)
                        navController.navigate(Routes.ELDERLY) {
                            popUpTo(Routes.ROLE_SELECT) {inclusive = true}
                        }
                    }
                }
            }
        }

        composable(Routes.ELDERLY) {

            LaunchedEffect(Unit) {
                elderlyViewModel.loadCheckIn(1)
            }

            val state = elderlyViewModel.uiState.collectAsState().value

            ElderlyScreen(
                state = state,
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

        composable(Routes.ADULT) {

            LaunchedEffect(Unit) {
                adultViewModel.loadInitialState(1)
            }

            AdultScreen(adultViewModel)
        }

        composable(Routes.THANK_YOU) {
            ThankYouScreen()
        }

    }
}