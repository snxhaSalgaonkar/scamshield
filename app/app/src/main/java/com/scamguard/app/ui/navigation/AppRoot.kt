package com.scamguard.app.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.scamguard.app.R
import com.scamguard.app.service.GuardService
import com.scamguard.app.ui.screens.check.CheckScreen
import com.scamguard.app.ui.screens.check.MessageDetailScreen
import com.scamguard.app.ui.screens.data.DataScreen
import com.scamguard.app.ui.screens.detail.AlertDetailScreen
import com.scamguard.app.ui.screens.models.ModelStatusScreen
import com.scamguard.app.ui.screens.numbers.NumbersScreen
import com.scamguard.app.ui.screens.health.HealthScreen
import com.scamguard.app.ui.screens.history.HistoryScreen
import com.scamguard.app.ui.screens.home.HomeScreen
import com.scamguard.app.ui.screens.onboarding.OnboardingScreen
import com.scamguard.app.ui.screens.settings.SettingsScreen
import com.scamguard.app.ui.screens.simulator.SimulatorScreen
import com.scamguard.app.ui.components.LoadingState
import com.scamguard.app.ui.theme.AppBurgundy
import com.scamguard.app.ui.theme.AppSurface
import com.scamguard.app.ui.theme.AlertRed
import com.scamguard.app.ui.theme.AppText
import com.scamguard.app.ui.theme.AppTextMuted
import com.scamguard.app.ui.theme.AppInk

/** Navigation routes. */
object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val SIMULATOR = "simulator"
    const val HEALTH = "health"
    const val DETAIL = "detail/{id}"
    const val CHECK = "check"
    const val MESSAGE = "message/{id}"
    const val MODELS = "models"
    const val NUMBERS = "numbers"
    const val DATA = "data"
    fun detail(id: String) = "detail/$id"
    fun message(id: Long) = "message/$id"
}

private data class TopLevel(val route: String, val label: Int, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    TopLevel(Routes.CHECK, R.string.nav_check, Icons.Filled.Search),
    TopLevel(Routes.HISTORY, R.string.nav_history, Icons.AutoMirrored.Filled.List),
    TopLevel(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

/** Shows onboarding until it is finished, then the main navigation. */
@Composable
fun AppRoot(openWindowId: String?, onWindowOpened: () -> Unit, openMessageId: Long? = null, onMessageOpened: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val current = settings
    if (current == null) {
        Box(Modifier.fillMaxSize().background(AppInk), contentAlignment = Alignment.Center) {
            LoadingState(stringResource(R.string.model_state_loading))
        }
        return
    }
    if (!current.onboardingDone) {
        OnboardingScreen()
        return
    }
    LaunchedEffect(current.protectionEnabled) {
        if (current.protectionEnabled) GuardService.start(context)
    }

    val nav = rememberNavController()
    LaunchedEffect(openWindowId) {
        if (openWindowId != null) {
            nav.navigate(Routes.detail(openWindowId))
            onWindowOpened()
        }
    }
    LaunchedEffect(openMessageId) {
        if (openMessageId != null) {
            nav.navigate(Routes.message(openMessageId))
            onMessageOpened()
        }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (topLevel.any { it.route == route }) BottomBar(nav, route) },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenAlert = { nav.navigate(Routes.detail(it)) },
                    onOpenHealth = { nav.navigate(Routes.HEALTH) },
                    onOpenSimulator = { nav.navigate(Routes.SIMULATOR) },
                    onOpenMessage = { nav.navigate(Routes.message(it)) },
                    onOpenCheck = { nav.navigate(Routes.CHECK) },
                    onOpenModels = { nav.navigate(Routes.MODELS) },
                )
            }
            composable(Routes.CHECK) { CheckScreen() }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpen = { nav.navigate(Routes.detail(it)) }, onOpenMessage = { nav.navigate(Routes.message(it)) })
            }
            composable(Routes.SETTINGS) { SettingsScreen(
                    onOpenSimulator = { nav.navigate(Routes.SIMULATOR) },
                    onOpenHealth = { nav.navigate(Routes.HEALTH) },
                    onOpenModels = { nav.navigate(Routes.MODELS) },
                    onOpenNumbers = { nav.navigate(Routes.NUMBERS) },
                    onOpenData = { nav.navigate(Routes.DATA) },
                ) }
            composable(Routes.MESSAGE) { entry ->
                MessageDetailScreen(id = entry.arguments?.getString("id")?.toLongOrNull() ?: -1, onBack = nav::popBackStack)
            }
            composable(Routes.MODELS) { ModelStatusScreen(onBack = nav::popBackStack, onOpenHealth = { nav.navigate(Routes.HEALTH) }) }
            composable(Routes.NUMBERS) { NumbersScreen(onBack = nav::popBackStack) }
            composable(Routes.DATA) { DataScreen(onBack = nav::popBackStack) }
            composable(Routes.SIMULATOR) { SimulatorScreen(onBack = nav::popBackStack) }
            composable(Routes.HEALTH) { HealthScreen(onBack = nav::popBackStack) }
            composable(Routes.DETAIL) { entry ->
                AlertDetailScreen(windowId = entry.arguments?.getString("id").orEmpty(), onBack = nav::popBackStack)
            }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    NavigationBar(
        containerColor = AppSurface,
        contentColor = AppText,
        tonalElevation = 0.dp,
        windowInsets = NavigationBarDefaults.windowInsets,
    ) {
        topLevel.forEach { item ->
            NavigationBarItem(
                selected = route == item.route,
                onClick = {
                    nav.navigate(item.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AlertRed,
                    selectedTextColor = AppText,
                    indicatorColor = AppBurgundy,
                    unselectedIconColor = AppTextMuted,
                    unselectedTextColor = AppTextMuted,
                ),
            )
        }
    }
}
