package com.danitube.app.data.suggest

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the phone is on a network that is not metered (normally Wi-Fi). Streaming and
 * auto-download only happen then, never on mobile data or a metered hotspot.
 */
@Singleton
class UnmeteredNetwork @Inject constructor(@ApplicationContext context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    private val _available = MutableStateFlow(check())
    val available: StateFlow<Boolean> = _available.asStateFlow()

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _available.value = caps.isUnmetered()
            }

            override fun onLost(network: Network) {
                _available.value = false
            }
        })
    }

    /** Fresh answer for background work, which should not trust a cached flow value. */
    fun isAvailable(): Boolean = check().also { _available.value = it }

    private fun check(): Boolean {
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.isUnmetered() == true
    }

    private fun NetworkCapabilities.isUnmetered() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}
