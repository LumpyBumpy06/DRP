package com.drp33.quietsignal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import com.drp33.quietsignal.data.RolePreferences
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
    val context = LocalContext.current
    val navController = rememberNavController()

    val repository = remember {
        CheckInRepositoryImpl(RetroFitProvider.checkInAPI)
    }

    val elderlyViewModel: ElderlyViewModel = viewModel(factory = ElderlyViewModelFactory(repository))
    val adultViewModel: AdultViewModel = viewModel(factory = AdultViewModelFactory(repository))

    // Reopen on the previously chosen role's screen, if any.
    val startDestination = remember {
        when (RolePreferences.get(context)) {
            UserRole.NORMAN -> Routes.ELDERLY
            UserRole.SADIE -> Routes.ADULT
            null -> Routes.ROLE_SELECT
        }
    }

    // Forget the saved role and return to role selection, clearing the back stack.
    val switchRole: () -> Unit = {
        RolePreferences.clear(context)
        navController.navigate(Routes.ROLE_SELECT) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ){

        composable(Routes.ROLE_SELECT) {
            RoleSelectScreen { role ->

                when (role) {
                    UserRole.SADIE -> {
                        RolePreferences.save(context, UserRole.SADIE)
                        navController.navigate(Routes.ADULT) {
                            popUpTo(Routes.ROLE_SELECT) {inclusive = true}
                        }
                    }
                    UserRole.NORMAN -> {
                        RolePreferences.save(context, UserRole.NORMAN)
                        navController.navigate(Routes.ELDERLY) {
                            popUpTo(Routes.ROLE_SELECT) {inclusive = true}
                        }
                    }
                }
            }
        }

        composable(Routes.ELDERLY) {

            // Re-poll so Norman's screen also resets after the day window: once his
            // check-in expires he sees the check-in (and voice) UI again.
            LaunchedEffect(Unit) {
                elderlyViewModel.postFCMToken(1)
                var first = true
                while (true) {
                    elderlyViewModel.loadCheckIn(1, showLoading = first)
                    first = false
                    delay(5000)
                }
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
                },
                onVoiceRecorded = { audio ->
                    elderlyViewModel.onVoiceRecorded(1, audio)
                },
                onSwitchRole = switchRole
            )
        }

        composable(Routes.ADULT) {

            // Re-poll the check-in status so the screen reflects the day window
            // expiring (Norman drops back to "not checked in" after ~30s).
            LaunchedEffect(Unit) {
                adultViewModel.postFCMToken(2)
                while (true) {
                    adultViewModel.loadInitialState(1)
                    delay(5000)
                }
            }

            AdultScreen(adultViewModel, onSwitchRole = switchRole)
        }

        composable(Routes.THANK_YOU) {
            ThankYouScreen()
        }

    }
}