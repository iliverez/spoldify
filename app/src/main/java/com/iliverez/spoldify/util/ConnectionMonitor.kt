package com.iliverez.spoldify.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class ConnectionMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isConnected = MutableLiveData(isCurrentlyConnected())
    val isConnected: LiveData<Boolean> = _isConnected

    private val _isWifi = MutableLiveData(isCurrentlyWifi())
    val isWifi: LiveData<Boolean> = _isWifi

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isConnected.postValue(true)
            _isWifi.postValue(isCurrentlyWifi())
        }

        override fun onLost(network: Network) {
            _isConnected.postValue(false)
            _isWifi.postValue(false)
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _isWifi.postValue(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
    }

    fun isCurrentlyConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isCurrentlyWifi(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun unregister() {
        connectivityManager.unregisterNetworkCallback(callback)
    }
}
