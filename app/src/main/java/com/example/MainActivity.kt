package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.StudioViewModel
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StudioDarkBg

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val viewModel: StudioViewModel = viewModel()
        val currentScreen by viewModel.currentScreen.collectAsState()

        BackHandler(enabled = currentScreen != AppScreen.HOME) {
          when (currentScreen) {
            AppScreen.EDITOR -> {
              viewModel.saveCurrentProject()
              viewModel.navigateTo(AppScreen.HOME)
            }
            AppScreen.EXPORT -> viewModel.navigateTo(AppScreen.EDITOR)
            AppScreen.PROJECTS, AppScreen.TEMPLATES, AppScreen.CREATOR,
            AppScreen.NOTIFICATIONS, AppScreen.ACCOUNT, AppScreen.SETTINGS,
            AppScreen.AI_SUITE, AppScreen.EXPORTED_LIBRARY -> {
              viewModel.navigateTo(AppScreen.HOME)
            }
            AppScreen.HOME -> {}
          }
        }

        val isEditorOrExport = currentScreen == AppScreen.EDITOR || currentScreen == AppScreen.EXPORT
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = if (isEditorOrExport) StudioDarkBg else com.example.ui.theme.LightPageBg
        ) {
          Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
            when (screen) {
              AppScreen.HOME -> HomeScreen(viewModel = viewModel)
              AppScreen.PROJECTS -> HomeScreen(viewModel = viewModel, initialTab = com.example.ui.components.home.HomeNavTab.PROJECTS)
              AppScreen.TEMPLATES -> TemplatesScreen(viewModel = viewModel)
              AppScreen.CREATOR -> CreatorScreen(viewModel = viewModel)
              AppScreen.NOTIFICATIONS -> NotificationsScreen(viewModel = viewModel)
              AppScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
              AppScreen.ACCOUNT -> AccountScreen(viewModel = viewModel)
              AppScreen.EDITOR -> EditorScreen(viewModel = viewModel)
              AppScreen.EXPORT -> ExportScreen(viewModel = viewModel)
              AppScreen.AI_SUITE -> AISuiteScreen(viewModel = viewModel)
              AppScreen.EXPORTED_LIBRARY -> ExportedVideosScreen(viewModel = viewModel)
            }
          }
        }
      }
    }
  }
}
