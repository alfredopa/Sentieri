package com.apstudio.sentieri

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class NetworkType {
    NONE,
    WIFI,
    CELLULAR,
    ETHERNET
}

object NetworkUtils {

    /**
     * Restituisce il tipo di rete attualmente attiva (WIFI, CELLULAR, ETHERNET, NONE).
     */
    fun getNetworkType(context: Context): NetworkType {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return NetworkType.NONE
        val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            else -> NetworkType.NONE
        }
    }

    /**
     * Verifica se è disponibile una qualsiasi connessione dati valida (Wi-Fi, cellulare o ethernet).
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return getNetworkType(context) != NetworkType.NONE
    }

    /**
     * Verifica se il dispositivo è connesso tramite Wi-Fi (o Ethernet).
     */
    fun isWifiConnected(context: Context): Boolean {
        val type = getNetworkType(context)
        return type == NetworkType.WIFI || type == NetworkType.ETHERNET
    }
}
