package com.xxx.carelorie.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Whether the device currently has a usable internet connection.
 *
 * The sync layer used to infer this from "did the request throw?", which reported a parse error,
 * a permissions error or a bad column name as "you are offline" — so the food log showed
 * "Showing saved history" while sitting on wifi. Asking the platform separates the two.
 */
interface ConnectivityChecker {
    fun isOnline(): Boolean
}

class AndroidConnectivityChecker(context: Context) : ConnectivityChecker {

    private val appContext = context.applicationContext

    override fun isOnline(): Boolean {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true // Cannot tell — assume online rather than showing a false warning.
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

/** Used by tests and previews, where there is no Android framework to ask. */
class AlwaysOnlineChecker : ConnectivityChecker {
    override fun isOnline(): Boolean = true
}
