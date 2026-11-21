package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai.configureAppCheck
import com.example.ui.components.DisplayEdgeGlowOverlay
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.MemoryProfilesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.KothaTheme
import com.example.ui.viewmodel.AssistantViewModel

const val ROUTE_ASSISTANT = "assistant"
const val ROUTE_MEMORY = "memory"
const val ROUTE_SETTINGS = "settings"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val viewModel: AssistantViewModel = viewModel()
            val prefs by viewModel.preferences.collectAsStateWithLifecycle()

            KothaTheme(accessibilityLarge = prefs.accessibilityLargeMode) {
                KothaAppShell(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun KothaAppShell(viewModel: AssistantViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_ASSISTANT
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val liveTalkState by viewModel.liveTalkState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val isListening by viewModel.voiceManager.isListening.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.voiceManager.isSpeaking.collectAsStateWithLifecycle()
    val rmsLevel by viewModel.voiceManager.rmsLevel.collectAsStateWithLifecycle()

    val navItems = listOf(
        Triple(
            ROUTE_ASSISTANT,
            stringResource(R.string.nav_assistant),
            Icons.Rounded.GraphicEq to Icons.Outlined.GraphicEq
        ),
        Triple(
            ROUTE_MEMORY,
            stringResource(R.string.nav_memory),
            Icons.Rounded.Psychology to Icons.Outlined.Psychology
        ),
        Triple(
            ROUTE_SETTINGS,
            stringResource(R.string.nav_settings),
            Icons.Rounded.Settings to Icons.Outlined.Settings
        )
    )

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        navItems.forEach { (route, label, icons) ->
                            val selected = currentRoute == route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) icons.first else icons.second,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        navItems.forEach { (route, label, icons) ->
                            val selected = currentRoute == route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) icons.first else icons.second,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_ASSISTANT,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_ASSISTANT) {
                        AssistantScreen(viewModel = viewModel)
                    }
                    composable(ROUTE_MEMORY) {
                        MemoryProfilesScreen(
                            viewModel = viewModel,
                            onOpenThreadInAssistant = { navigateTo(ROUTE_ASSISTANT) }
                        )
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }

        // Full-display perimeter edge glow rendered over the entire UI
        DisplayEdgeGlowOverlay(
            enabled = prefs.edgeGlowEnabled,
            alwaysOn = prefs.edgeGlowAlwaysOn,
            uiState = uiState,
            liveTalkState = liveTalkState,
            isListening = isListening,
            isSpeaking = isSpeaking,
            rmsLevel = rmsLevel
        )
    }
}
