package com.drp33.quietsignal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.drp33.quietsignal.data.RolePreferences
import com.drp33.quietsignal.data.ThreadReadStore
import com.drp33.quietsignal.data.remote.RetroFitProvider
import com.drp33.quietsignal.data.repo.CheckInRepositoryImpl
import com.drp33.quietsignal.model.UserRole
import com.drp33.quietsignal.ui.screens.AdultScreen
import com.drp33.quietsignal.ui.screens.ElderlyScreen
import com.drp33.quietsignal.ui.screens.ForestScreen
import com.drp33.quietsignal.ui.screens.MainShell
import com.drp33.quietsignal.ui.screens.RoleSelectScreen
import com.drp33.quietsignal.viewmodels.AdultViewModel
import com.drp33.quietsignal.viewmodels.AdultViewModelFactory
import com.drp33.quietsignal.viewmodels.ElderlyViewModel
import com.drp33.quietsignal.viewmodels.ElderlyViewModelFactory
import com.drp33.quietsignal.viewmodels.MemoriesViewModel
import com.drp33.quietsignal.viewmodels.MemoriesViewModelFactory
import com.drp33.quietsignal.viewmodels.PhotoMessagingViewModel
import com.drp33.quietsignal.viewmodels.PhotoMessagingViewModelFactory
import com.drp33.quietsignal.viewmodels.ThreadsViewModel
import com.drp33.quietsignal.viewmodels.ThreadsViewModelFactory
import com.drp33.quietsignal.viewmodels.TreeViewModel
import com.drp33.quietsignal.viewmodels.TreeViewModelFactory
import com.drp33.quietsignal.viewmodels.VoiceMessagingViewModel
import com.drp33.quietsignal.viewmodels.VoiceMessagingViewModelFactory

@Composable
fun NavGraph() {
    val context = LocalContext.current
    val navController = rememberNavController()

    val repository = remember {
        CheckInRepositoryImpl(RetroFitProvider.getCheckInAPI(context))
    }
    // Persistent per-thread read state (survives sign-out + restarts).
    val threadReadStore = remember { ThreadReadStore(context) }

    val elderlyViewModel: ElderlyViewModel = viewModel(factory = ElderlyViewModelFactory(repository))
    val adultViewModel: AdultViewModel = viewModel(factory = AdultViewModelFactory(repository))
    // One shared tree for both roles — same instance, polled continuously.
    val treeViewModel: TreeViewModel = viewModel(factory = TreeViewModelFactory(repository))
    // Shared memory board (all voice memos + snaps ever sent).
    val memoriesViewModel: MemoriesViewModel = viewModel(factory = MemoriesViewModelFactory(repository))

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
        startDestination = startDestination,
    ) {

        composable(Routes.ROLE_SELECT) {
            val scope = rememberCoroutineScope()
            RoleSelectScreen(
                onSelect = { role ->
                    when (role) {
                        UserRole.SADIE -> {
                            RolePreferences.save(context, UserRole.SADIE)
                            navController.navigate(Routes.ADULT) {
                                popUpTo(Routes.ROLE_SELECT) { inclusive = true }
                            }
                        }
                        UserRole.NORMAN -> {
                            RolePreferences.save(context, UserRole.NORMAN)
                            navController.navigate(Routes.ELDERLY) {
                                popUpTo(Routes.ROLE_SELECT) { inclusive = true }
                            }
                        }
                    }
                },
                // Demo helper: jump the shared tree to a stage before entering.
                onSetStage = { stage -> scope.launch { repository.setTreeStage(stage) } },
            )
        }

        composable(Routes.ELDERLY) {
            // Norman: records into mailbox 1, plays Sadie's (mailbox 2).
            val voiceVm: VoiceMessagingViewModel =
                viewModel(factory = VoiceMessagingViewModelFactory(repository, selfId = 1, peerId = 2))
            val photoVm: PhotoMessagingViewModel =
                viewModel(factory = PhotoMessagingViewModelFactory(repository, selfId = 1, peerId = 2))
            // Shared memory threads, written as Norman (id 1).
            val threadsVm: ThreadsViewModel =
                viewModel(factory = ThreadsViewModelFactory(repository, selfId = 1, readStore = threadReadStore))

            LaunchedEffect(Unit) { elderlyViewModel.postFCMToken(1) }

            // "This week" + "Threads" + "Forest" tabs share the warm Grove backdrop.
            MainShell(forestVm = memoriesViewModel, threadsVm = threadsVm) { pad ->
                ElderlyScreen(
                    treeVm = treeViewModel,
                    voiceVm = voiceVm,
                    photoVm = photoVm,
                    memoriesVm = memoriesViewModel,
                    threadsVm = threadsVm,
                    contentPadding = pad,
                    onSwitchRole = switchRole,
                    onEmergencyClick = { elderlyViewModel.sendEmergency(1) },
                )
            }
        }

        composable(Routes.ADULT) {
            // Sadie: records into mailbox 2, plays Norman's (mailbox 1).
            val voiceVm: VoiceMessagingViewModel =
                viewModel(factory = VoiceMessagingViewModelFactory(repository, selfId = 2, peerId = 1))
            val photoVm: PhotoMessagingViewModel =
                viewModel(factory = PhotoMessagingViewModelFactory(repository, selfId = 2, peerId = 1))
            // Shared memory threads, written as Sadie (id 2).
            val threadsVm: ThreadsViewModel =
                viewModel(factory = ThreadsViewModelFactory(repository, selfId = 2, readStore = threadReadStore))

            // Fetch initial state once, then rely on pushes.
            LaunchedEffect(Unit) {
                adultViewModel.postFCMToken(2)
                adultViewModel.loadInitialState(2)
                adultViewModel.loadEmergencyStatus(2)
            }

            MainShell(
                forestVm = memoriesViewModel,
                threadsVm = threadsVm,
                emergencyActive = adultViewModel.state.emergency,
                onEmergencyAck = { adultViewModel.acknowledgeEmergency(2) },
            ) { pad ->
                AdultScreen(
                    viewModel = adultViewModel,
                    treeVm = treeViewModel,
                    voiceVm = voiceVm,
                    photoVm = photoVm,
                    memoriesVm = memoriesViewModel,
                    threadsVm = threadsVm,
                    contentPadding = pad,
                    onSwitchRole = switchRole,
                )
            }
        }

        // Kept for compatibility; the forest is now a bottom tab inside MainShell.
        composable(Routes.FOREST) {
            ForestScreen(
                vm = memoriesViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
