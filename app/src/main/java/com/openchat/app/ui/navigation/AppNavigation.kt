package com.openchat.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.openchat.app.ui.screens.*
import com.openchat.app.util.SettingsManager
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Chat : Screen("chat/{sessionId}") {
        fun createRoute(sessionId: String) = "chat/$sessionId"
    }
    object Workspace : Screen("workspace/{sessionId}") {
        fun createRoute(sessionId: String) = "workspace/$sessionId"
    }
    object Settings : Screen("settings")
    object FileEditor : Screen("file_editor/{sessionId}/{fileId}") {
        fun createRoute(sessionId: String, fileId: String) = "file_editor/$sessionId/$fileId"
    }
    object Memory : Screen("memory")
    object RecycleBin : Screen("recycle_bin/{sessionId}") {
        fun createRoute(sessionId: String) = "recycle_bin/$sessionId"
    }
}

@Composable
fun AppNavigation(
    settingsManager: SettingsManager
) {
    val navController = rememberNavController()
    val isFirstLaunch by settingsManager.isFirstLaunch.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    if (isFirstLaunch == null) return

    NavHost(
        navController = navController,
        startDestination = if (isFirstLaunch == true) "onboarding" else Screen.Chat.createRoute("new")
    ) {
        composable("onboarding") {
            OnboardingScreen(onFinish = {
                scope.launch {
                    settingsManager.setFirstLaunchCompleted()
                    navController.navigate(Screen.Chat.createRoute("new")) {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            })
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: "new"
            ChatScreen(
                sessionId = sessionId,
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onOpenWorkspace = { navController.navigate(Screen.Workspace.createRoute(it)) },
                onSessionSelected = { navController.navigate(Screen.Chat.createRoute(it)) }
            )
        }

        composable(
            route = Screen.Workspace.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            WorkspaceScreen(
                sessionId = sessionId,
                onClose = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Settings.route,
            enterTransition = { slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) },
            exitTransition = { slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) }
        ) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
