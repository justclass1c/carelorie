package com.xxx.carelorie.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the app is currently drawing dark.
 *
 * Read this rather than [isSystemInDarkTheme] anywhere a colour has to react to the theme: the user
 * can force light or dark in settings, and the system flag does not know about that override.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * Both schemes define the same roles.
 *
 * `outline` in particular: it is the role every card border in this app asks for, and neither
 * scheme used to set it, so all of them silently fell back to Material's default — a grey with no
 * relationship to anything else on screen.
 */
private val DarkColorScheme = darkColorScheme(
    primary = IndigoDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2A55),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = BlueDark,
    onSecondary = Color.White,
    tertiary = Color(0xFFFF9F0A),
    onTertiary = Color.Black,
    background = GroupedBackgroundDark,
    onBackground = LabelDark,
    surface = CardDark,
    onSurface = LabelDark,
    surfaceVariant = FillDark,
    onSurfaceVariant = SecondaryLabelDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = FillDark,
    surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = SeparatorDark,
    outlineVariant = SeparatorSoftDark,
    error = RedDark,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E4FB),
    onPrimaryContainer = Color(0xFF1E1E4A),
    secondary = Blue,
    onSecondary = Color.White,
    tertiary = Color(0xFFFF9500),
    onTertiary = Color.White,
    background = GroupedBackground,
    onBackground = LabelLight,
    surface = CardLight,
    onSurface = LabelLight,
    surfaceVariant = FillLight,
    onSurfaceVariant = SecondaryLabelLight,
    surfaceContainer = CardLight,
    surfaceContainerHigh = FillLight,
    surfaceContainerHighest = Color(0xFFE5E5EA),
    outline = SeparatorLight,
    outlineVariant = SeparatorSoftLight,
    error = RedLight,
    onError = Color.White
)

@Composable
fun CarelorieTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic colour is off on purpose. The palette below is a set — wallpaper-derived colours
    // would replace the accents but not the macro key, and the two would stop agreeing.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = CarelorieShapes,
            content = content
        )
    }
}
