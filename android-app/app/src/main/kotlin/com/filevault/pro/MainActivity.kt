package com.filevault.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.filevault.pro.data.preferences.AppPreferences
import com.filevault.pro.navigation.AppNavGraph
import com.filevault.pro.presentation.screen.lock.AppLockScreen
import com.filevault.pro.presentation.theme.FileVaultTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appPreferences: AppPreferences

    private var backgroundedAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var keepSplash = true
        splashScreen.setKeepOnScreenCondition { keepSplash }

        lifecycleScope.launch {
            appPreferences.themeMode.first()
            keepSplash = false
        }

        setContent {
            val themeMode by appPreferences.themeMode.collectAsState("SYSTEM")
            val appLockEnabled by appPreferences.appLockEnabled.collectAsState(false)
            val lockTimeoutMinutes by appPreferences.lockTimeoutMinutes.collectAsState(5)
            val darkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            var isUnlocked by remember { mutableStateOf(!appLockEnabled) }

            androidx.compose.runtime.LaunchedEffect(appLockEnabled) {
                if (!appLockEnabled) isUnlocked = true
            }

            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    when (event) {
                        androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                            backgroundedAt = System.currentTimeMillis()
                        }
                        androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                            if (appLockEnabled && isUnlocked && backgroundedAt > 0L) {
                                val elapsedMinutes = (System.currentTimeMillis() - backgroundedAt) / 60_000L
                                if (lockTimeoutMinutes > 0 && elapsedMinutes >= lockTimeoutMinutes) {
                                    isUnlocked = false
                                }
                            }
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            FileVaultTheme(darkTheme = darkTheme, dynamicColor = false) {
                if (appLockEnabled && !isUnlocked) {
                    AppLockScreen(
                        appPreferences = appPreferences,
                        onUnlocked = { isUnlocked = true }
                    )
                } else {
                    AppNavGraph(appPreferences = appPreferences)
                }
            }
        }
    }
}
