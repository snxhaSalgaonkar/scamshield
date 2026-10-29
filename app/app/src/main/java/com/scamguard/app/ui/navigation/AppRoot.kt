package com.scamguard.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.scamguard.app.ui.screens.listen.ListenScreen
import com.scamguard.app.ui.screens.eval.EvaluationScreen

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
    const val LISTEN = "listen"
    const val EVALUATION = "evaluation"
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
fun AppRoot(
    openWindowId: String?,
    onWindowOpened: () -> Unit,
    openMessageId: Long? = null,
    onMessageOpened: () -> Unit = {},
    openListen: Boolean = false,
    onListenOpened: () -> Unit = {},
    sharedImage: Uri? = null,
    onSharedImageOpened: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val current = settings ?: return
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
    LaunchedEffect(openListen) {
        if (openListen) {
            nav.navigate(Routes.LISTEN) { launchSingleTop = true }
            onListenOpened()
        }
    }
    var pendingImage by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(sharedImage) {
        if (sharedImage != null) {
            pendingImage = sharedImage
            nav.navigate(Routes.CHECK) { launchSingleTop = true }
            onSharedImageOpened()
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
            composable(Routes.CHECK) { CheckScreen(pendingImage, onSharedImageChecked = { pendingImage = null }) }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpen = { nav.navigate(Routes.detail(it)) }, onOpenMessage = { nav.navigate(Routes.message(it)) })
            }
            composable(Routes.SETTINGS) { SettingsScreen(
                    onOpenSimulator = { nav.navigate(Routes.SIMULATOR) },
                    onOpenHealth = { nav.navigate(Routes.HEALTH) },
                    onOpenModels = { nav.navigate(Routes.MODELS) },
                    onOpenNumbers = { nav.navigate(Routes.NUMBERS) },
                    onOpenData = { nav.navigate(Routes.DATA) },
                    onOpenListen = { nav.navigate(Routes.LISTEN) },
                    onOpenEvaluation = { nav.navigate(Routes.EVALUATION) },
                ) }
            composable(Routes.MESSAGE) { entry ->
                MessageDetailScreen(id = entry.arguments?.getString("id")?.toLongOrNull() ?: -1, onBack = nav::popBackStack)
            }
            composable(Routes.MODELS) { ModelStatusScreen(onBack = nav::popBackStack, onOpenHealth = { nav.navigate(Routes.HEALTH) }) }
            composable(Routes.NUMBERS) { NumbersScreen(onBack = nav::popBackStack) }
            composable(Routes.DATA) { DataScreen(onBack = nav::popBackStack) }
            composable(Routes.SIMULATOR) { SimulatorScreen(onBack = nav::popBackStack) }
            composable(Routes.LISTEN) {
                ListenScreen(onBack = nav::popBackStack, onOpenCall = { nav.navigate(Routes.detail(it)) })
            }
            composable(Routes.HEALTH) { HealthScreen(onBack = nav::popBackStack) }
            composable(Routes.EVALUATION) { EvaluationScreen(onBack = nav::popBackStack) }
            composable(Routes.DETAIL) { entry ->
                AlertDetailScreen(windowId = entry.arguments?.getString("id").orEmpty(), onBack = nav::popBackStack)
            }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    NavigationBar {
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
            )
        }
    }
}
