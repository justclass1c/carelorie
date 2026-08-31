package com.xxx.carelorie.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The macro colour key used across the whole app.
 *
 * Each value has a light and a dark variant. They used to be single fixed hex values, which meant
 * the key was tuned for one theme and merely tolerated in the other — [Warning] was a pure yellow
 * that all but vanished against the light background it was warning on.
 *
 * These are composable properties, so a call site reads exactly as it did before
 * (`MacroColors.Protein`) but now resolves against whichever theme is actually drawing. They follow
 * the app's own theme setting rather than the system's, via [LocalIsDarkTheme].
 */
object MacroColors {

    /** systemPink — protein. */
    val Protein: Color
        @Composable @ReadOnlyComposable get() = pick(Color(0xFFFF2D55), Color(0xFFFF375F))

    /** systemBlue — carbohydrate. */
    val Carbs: Color
        @Composable @ReadOnlyComposable get() = pick(Color(0xFF007AFF), Color(0xFF0A84FF))

    /** systemGreen — fat. */
    val Fat: Color
        @Composable @ReadOnlyComposable get() = pick(Color(0xFF34C759), Color(0xFF30D158))

    /** systemOrange — calories. */
    val Calories: Color
        @Composable @ReadOnlyComposable get() = pick(Color(0xFFFF9500), Color(0xFFFF9F0A))

    /**
     * Over the limit by less than 20%.
     *
     * Deliberately not a pure yellow. Yellow is the one hue that cannot carry a light-mode warning:
     * at full saturation it fails contrast against white, and darkening it far enough to pass turns
     * it brown. An amber holds its meaning at a readable weight in both themes.
     */
    val Warning: Color
        @Composable @ReadOnlyComposable get() = pick(Color(0xFFB25E00), Color(0xFFFFB340))

    /** Over the limit by 20% or more — systemRed. */
    val Over: Color
        @Composable @ReadOnlyComposable get() = pick(Color(0xFFFF3B30), Color(0xFFFF453A))

    /** The marker for "today" in charts and calendars. Reads as selection, not as a macro. */
    val Today: Color
        @Composable @ReadOnlyComposable get() = pick(Indigo, IndigoDark)

    @Composable
    @ReadOnlyComposable
    private fun pick(light: Color, dark: Color): Color =
        if (LocalIsDarkTheme.current) dark else light
}

/**
 * Returns a warning colour when [current] has gone over [limit], or null while it is within
 * the limit. Amber means under 20% over; red means 20% or more over.
 */
@Composable
@ReadOnlyComposable
fun overLimitColor(current: Float, limit: Float): Color? {
    if (limit <= 0f || current <= limit) return null
    val overRatio = (current - limit) / limit
    return if (overRatio < 0.20f) MacroColors.Warning else MacroColors.Over
}
