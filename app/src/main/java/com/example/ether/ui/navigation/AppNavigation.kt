package com.example.ether.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ether.browser.GeckoManager
import com.example.ether.data.repository.DownloadRepository
import com.example.ether.data.repository.HistoryRepository
import com.example.ether.data.repository.SettingsRepository
import com.example.ether.ui.browser.BrowserScreen
import com.example.ether.ui.home.HomeScreen
import com.example.ether.ui.settings.DownloadScreen
import com.example.ether.ui.settings.ExtensionScreen
import com.example.ether.ui.settings.SettingsScreen
import com.example.ether.ui.settings.ThemesScreen
import com.example.ether.ui.settings.vpn.VpnConfigScreen
import com.example.ether.ui.util.UrlUtils
import com.example.ether.ui.video.VideoViewModel

@Composable
fun AppNavigation(
    geckoManager: GeckoManager,
    settingsRepository: SettingsRepository,
    downloadRepository: DownloadRepository,
    historyRepository: HistoryRepository,
    videoViewModel: VideoViewModel,
    onFullScreenChanged: (Boolean) -> Unit
) {
    val navController = rememberNavController()

    LaunchedEffect(geckoManager) {
        geckoManager.navigationEvents.collect { url ->
            val encodedUrl = UrlUtils.encode(url)
            navController.navigate("browser/false?url=$encodedUrl") {
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onWebsiteClick = { url, isDesktop ->
                    val encodedUrl = UrlUtils.encode(url)
                    navController.navigate("browser/$isDesktop?url=$encodedUrl")
                },
                onSettingsClick = { navController.navigate("settings") },
                onDownloadsClick = { navController.navigate("downloads") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToThemes = { navController.navigate("themes") },
                onNavigateToExtensions = { navController.navigate("extensions") },
                onNavigateToDownloads = { navController.navigate("downloads") },
                onNavigateToVpnConfig = { navController.navigate("vpn_config") }
            )
        }
        composable("vpn_config") { VpnConfigScreen(onBack = { navController.popBackStack() }) }
        composable("themes") { ThemesScreen(onBack = { navController.popBackStack() }) }
        composable("extensions") {
            ExtensionScreen(
                onBack = { navController.popBackStack() },
                onVisitStore = {
                    val storeUrl = "https://addons.mozilla.org/android/"
                    val encodedUrl = UrlUtils.encode(storeUrl)
                    navController.navigate("browser/false?url=$encodedUrl")
                },
                geckoManager = geckoManager
            )
        }
        composable("downloads") {
            DownloadScreen(
                onBack = { navController.popBackStack() },
                onPlayVideo = { videoUri -> videoViewModel.playVideo(videoUri) }
            )
        }
        composable(
            route = "browser/{isDesktop}?url={url}",
            arguments = listOf(
                navArgument("url") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("isDesktop") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            val isDesktop = backStackEntry.arguments?.getBoolean("isDesktop") ?: false
            BrowserScreen(
                url = url,
                isDesktopMode = isDesktop,
                geckoManager = geckoManager,
                settingsRepository = settingsRepository,
                downloadRepository = downloadRepository,
                historyRepository = historyRepository,
                onFullScreenChanged = onFullScreenChanged,
                onNavigateToExtensions = { navController.navigate("extensions") }
            )
        }
    }
}
