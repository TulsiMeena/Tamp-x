package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Inbox : Screen("inbox")
    object EmailDetail : Screen("email_detail/{emailId}") {
        fun createRoute(emailId: Int) = "email_detail/$emailId"
    }
    object MailboxHistory : Screen("mailbox_history")
    object Settings : Screen("settings")
    object About : Screen("about")
    object PrivacyPolicy : Screen("privacy_policy")
}
