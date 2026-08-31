package com.xxx.carelorie

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.xxx.carelorie.data.ThemeManager
import com.xxx.carelorie.ui.BottomNavBar
import com.xxx.carelorie.ui.layout.LocalWindowWidthSizeClass
import com.xxx.carelorie.ui.theme.CarelorieTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themeManager = (application as CarelorieApplication).container.themeManager
        setContent {
            val themeMode by themeManager.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeManager.THEME_DARK -> true
                ThemeManager.THEME_LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            // Keep the system bar icons legible against whatever the app is actually drawing.
            // enableEdgeToEdge() picks its style once, from the *system* theme, so a user who
            // forces light mode inside the app while their phone is dark got white status-bar
            // icons on a near-white background — invisible.
            val view = LocalView.current
            SideEffect {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightNavigationBars = !darkTheme
            }

            CarelorieTheme(darkTheme = darkTheme) {
                // Recalculated whenever the window changes, so rotating a tablet or entering
                // split screen swaps the layout without restarting anything. Provided to the
                // whole tree so any screen can read it without it being threaded through the
                // shell and the navigation graph first.
                val windowSizeClass = calculateWindowSizeClass(this)
                CompositionLocalProvider(
                    LocalWindowWidthSizeClass provides windowSizeClass.widthSizeClass
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        BottomNavBar()
                    }
                }
            }
        }
    }
}
