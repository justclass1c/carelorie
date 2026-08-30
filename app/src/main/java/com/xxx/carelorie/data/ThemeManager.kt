package com.xxx.carelorie.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the light/dark/system choice.
 *
 * The value is mirrored into SharedPreferences as well as [UserProfile.theme] so the app opens
 * in the right theme immediately, before the profile has been read from Room or Supabase —
 * otherwise every cold start flashes the system theme first.
 */
class ThemeManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("carelorie_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        val safe = if (mode in ALL) mode else THEME_SYSTEM
        if (_themeMode.value == safe) return
        prefs.edit().putString(KEY_THEME, safe).apply()
        _themeMode.value = safe
    }

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        private const val KEY_THEME = "theme_mode"
        private val ALL = setOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK)
    }
}
