package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.navigation.AppNavigation
import com.example.ui.navigation.Screen
import com.example.ui.theme.TempMailAiTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val useDarkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            TempMailAiTheme(darkTheme = useDarkTheme) {
                val navController = rememberNavController()

                // Handle notification launch with target email ID
                val targetEmailId = intent?.getIntExtra("email_id", -1) ?: -1
                if (targetEmailId > 0) {
                    navController.navigate(Screen.EmailDetail.createRoute(targetEmailId))
                }

                AppNavigation(
                    navController = navController,
                    viewModel = viewModel
                )
            }
        }
    }
}
