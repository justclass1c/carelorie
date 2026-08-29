package com.xxx.carelorie.data

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("carelorie_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_REMEMBER_ME = "remember_me"
    }

    fun saveUserId(userId: String, rememberMe: Boolean = true) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putBoolean(KEY_REMEMBER_ME, rememberMe)
            .apply()
    }

    fun getUserId(): String {
        return prefs.getString(KEY_USER_ID, "") ?: ""
    }

    fun isRememberMe(): Boolean {
        return prefs.getBoolean(KEY_REMEMBER_ME, false)
    }

    fun clearSession() {
        prefs.edit().remove(KEY_USER_ID).remove(KEY_REMEMBER_ME).apply()
    }
}
