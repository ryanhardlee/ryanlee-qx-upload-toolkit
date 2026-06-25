package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.screens.DashboardScreen
import com.example.screens.TikTokStudioWebScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val viewModel: MainViewModel = viewModel()
        val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

        Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
          when (screen) {
            "tiktok_upload" -> TikTokStudioWebScreen(
              viewModel = viewModel,
              onClose = { viewModel.navigateTo("dashboard") }
            )
            else -> DashboardScreen(viewModel = viewModel)
          }
        }
      }
    }
  }
}
