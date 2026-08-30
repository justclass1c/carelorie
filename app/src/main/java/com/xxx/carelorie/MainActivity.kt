package com.xxx.carelorie

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.xxx.carelorie.data.ThemeManager
import com.xxx.carelorie.ui.BottomNavBar
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
            CarelorieTheme(darkTheme = darkTheme) {
                // Recalculated whenever the window changes, so rotating a tablet or entering
                // split screen swaps the layout without restarting anything.
                val windowSizeClass = calculateWindowSizeClass(this)
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BottomNavBar(widthSizeClass = windowSizeClass.widthSizeClass)
                }
            }
        }
    }
}
