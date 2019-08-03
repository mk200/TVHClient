package org.tvheadend.tvhclient.ui.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import timber.log.Timber
import java.lang.reflect.InvocationTargetException

enum class NetworkStatus {
    NETWORK_UNKNOWN,
    NETWORK_IS_DOWN,
    NETWORK_IS_UP,
    NETWORK_IS_STILL_DOWN,
    NETWORK_IS_STILL_UP
}

fun isConnectionAvailable(context: Context): Boolean {
    return isNetworkAvailable(context) || isWifiApEnabled(context)
}

fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetworkInfo = connectivityManager.activeNetworkInfo
    return activeNetworkInfo != null && activeNetworkInfo.isConnected
}

private fun isWifiApEnabled(context: Context): Boolean {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val wmMethods = wifiManager.javaClass.declaredMethods
    for (method in wmMethods) {
        if (method.name == "isWifiApEnabled") {
            try {
                return method.invoke(wifiManager) as Boolean
            } catch (e: IllegalArgumentException) {
                Timber.d(e, "Illegal argument error while invoking method 'isWifiApEnabled'")
            } catch (e: IllegalAccessException) {
                Timber.d(e, "Illegal access error while invoking method 'isWifiApEnabled'")
            } catch (e: InvocationTargetException) {
                Timber.d(e, "Invocation error while invoking method 'isWifiApEnabled'")
            }
        }
    }
    return false
}

fun getNetworkStatus(previousStatus: NetworkStatus?, isAvailable: Boolean): NetworkStatus {
    Timber.d("Previous network status is $previousStatus")
    val status = when (previousStatus) {
        NetworkStatus.NETWORK_IS_DOWN -> {
            if (isAvailable) NetworkStatus.NETWORK_IS_UP else NetworkStatus.NETWORK_IS_STILL_DOWN
        }
        NetworkStatus.NETWORK_IS_UP -> {
            if (isAvailable) NetworkStatus.NETWORK_IS_STILL_UP else NetworkStatus.NETWORK_IS_DOWN
        }
        NetworkStatus.NETWORK_IS_STILL_DOWN -> {
            if (isAvailable) NetworkStatus.NETWORK_IS_UP else NetworkStatus.NETWORK_IS_STILL_DOWN
        }
        NetworkStatus.NETWORK_IS_STILL_UP -> {
            if (isAvailable) NetworkStatus.NETWORK_IS_STILL_UP else NetworkStatus.NETWORK_IS_DOWN
        }
        NetworkStatus.NETWORK_UNKNOWN -> {
            if (isAvailable) NetworkStatus.NETWORK_IS_UP else NetworkStatus.NETWORK_IS_DOWN
        }
        else -> return NetworkStatus.NETWORK_UNKNOWN
    }
    Timber.d("Current network status is $status")
    return status
}