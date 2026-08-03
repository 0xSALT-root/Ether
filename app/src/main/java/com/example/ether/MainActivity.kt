package com.example.ether

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ether.browser.GeckoManager
import com.example.ether.data.repository.DownloadRepository
import com.example.ether.data.repository.HistoryRepository
import com.example.ether.data.repository.SettingsRepository
import com.example.ether.security.SecurityManager
import com.example.ether.ui.browser.BrowserScreen
import com.example.ether.ui.home.HomeScreen
import com.example.ether.ui.settings.DownloadScreen
import com.example.ether.ui.settings.ExtensionScreen
import com.example.ether.ui.settings.SettingsScreen
import com.example.ether.ui.settings.ThemesScreen
import com.example.ether.ui.settings.vpn.VpnConfigScreen
import com.example.ether.ui.navigation.AppNavigation
import com.example.ether.ui.theme.EtherTheme
import com.example.ether.ui.video.VideoPlayerScreen
import com.example.ether.ui.video.VideoViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var geckoManager: GeckoManager

    @Inject
    lateinit var securityManager: SecurityManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    lateinit var historyRepository: HistoryRepository

    @Inject
    lateinit var vpnManager: com.example.ether.browser.vpn.VpnManager

    private var isCurrentlyFullscreen = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val requestVpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            vpnManager.forceStartVpn()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkNotificationPermission()
        
        // Ensure GeckoRuntime is initialized lazily or explicitly
        geckoManager.runtime

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vpnManager.preparationNeeded.collect { intent ->
                    requestVpnLauncher.launch(intent)
                }
            }
        }

        setContent {
            val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val videoViewModel: VideoViewModel = hiltViewModel()
            var isAuthenticated by remember { mutableStateOf(false) }
            var authInitiated by remember { mutableStateOf(false) }

            LaunchedEffect(settings) {
                if (settings != null && !authInitiated) {
                    authInitiated = true
                    if (settings?.isBiometricLockEnabled == true) {
                        securityManager.authenticate(
                            activity = this@MainActivity,
                            onSuccess = { isAuthenticated = true },
                            onError = { _, isFatal -> if (isFatal) finish() }
                        )
                    } else {
                        isAuthenticated = true
                    }
                }
            }

            LaunchedEffect(settings?.isScreenshotProtectionEnabled) {
                if (settings?.isScreenshotProtectionEnabled == true) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            LaunchedEffect(isCurrentlyFullscreen) {
                val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                if (isCurrentlyFullscreen) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                    windowInsetsController?.hide(WindowInsetsCompat.Type.systemBars())
                    windowInsetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                    windowInsetsController?.show(WindowInsetsCompat.Type.systemBars())
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }

            EtherTheme(
                darkTheme = settings?.isDarkMode ?: true,
                amoledMode = settings?.isAmoledModeEnabled ?: false,
                selectedTheme = settings?.selectedTheme ?: "DEFAULT"
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isAuthenticated) {
                        Box(Modifier.fillMaxSize()) {
                            AppNavigation(
                                geckoManager = geckoManager,
                                settingsRepository = settingsRepository,
                                downloadRepository = downloadRepository,
                                historyRepository = historyRepository,
                                videoViewModel = videoViewModel,
                                onFullScreenChanged = { isCurrentlyFullscreen = it }
                            )

                            videoViewModel.activeVideoUri?.let { uri ->
                                VideoPlayerScreen(
                                    videoUri = uri,
                                    isMinimized = videoViewModel.isMinimized,
                                    onMinimize = { videoViewModel.minimize(); isCurrentlyFullscreen = false },
                                    onClose = { videoViewModel.closeVideo(); isCurrentlyFullscreen = false },
                                    onExpand = { videoViewModel.isMinimized = false },
                                    onFullScreenChanged = { isCurrentlyFullscreen = it }
                                )
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (event.isCtrlPressed) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_R -> {
                    // Logic for reload if needed, but we don't have easy access to the session here.
                    // Instead, we could signal it via GeckoManager if we had a "reloadCurrent" flow.
                    return true
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }
}

