package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ui.MainViewModel
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.EmailDetailScreen
import com.example.ui.screens.InboxScreen
import com.example.ui.screens.MailboxHistoryScreen
import com.example.ui.screens.PrivacyPolicyScreen
import com.example.ui.screens.SettingsScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: MainViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Inbox.route
    ) {
        composable(Screen.Inbox.route) {
            InboxScreen(
                viewModel = viewModel,
                onEmailClick = { emailId ->
                    navController.navigate(Screen.EmailDetail.createRoute(emailId))
                },
                onHistoryClick = {
                    navController.navigate(Screen.MailboxHistory.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.EmailDetail.route,
            arguments = listOf(
                navArgument("emailId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val emailId = backStackEntry.arguments?.getInt("emailId") ?: 0
            EmailDetailScreen(
                emailId = emailId,
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.MailboxHistory.route) {
            MailboxHistoryScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onAboutClick = { navController.navigate(Screen.About.route) },
                onPrivacyPolicyClick = { navController.navigate(Screen.PrivacyPolicy.route) }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
