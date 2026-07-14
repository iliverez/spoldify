package com.iliverez.spoldify.zeroconf

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.iliverez.spoldify.SpoldifyApp
import com.spotify.connectstate.Connect
import xyz.gianlu.librespot.ZeroconfServer
import java.io.IOException

class ZeroconfManager(private val app: SpoldifyApp) : ZeroconfServer.SessionListener {

    private var zeroconfServer: ZeroconfServer? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = ZeroconfStateLiveData()
    val state: ZeroconfStateLiveData get() = _state

    fun start() {
        if (zeroconfServer != null) return

        _state.postValue(ZeroconfState.STARTING)

        Thread {
            try {
                acquireMulticastLock()

                val conf = app.authRepository.getConfiguration()
                val server = tryCreateServer(conf, "Spoldify", ZEROCONF_PORT)
                    ?: tryCreateServer(conf, "Spoldify", -1)
                    ?: tryCreateServer(conf, "Spoldify-" + (1000..9999).random(), -1)
                    ?: throw IOException("Failed to start Zeroconf server")

                server.addSessionListener(this)
                zeroconfServer = server

                _state.postValue(ZeroconfState.WAITING)
                Log.i(TAG, "Zeroconf server started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Zeroconf server", e)
                _state.postValue(ZeroconfState.ERROR)
            }
        }.start()
    }

    private fun tryCreateServer(conf: xyz.gianlu.librespot.core.Session.Configuration, name: String, port: Int): ZeroconfServer? {
        return try {
            ZeroconfServer.Builder(conf)
                .setDeviceName(name)
                .setDeviceType(Connect.DeviceType.SMARTPHONE)
                .setPreferredLocale("en")
                .setListenAll(true)
                .setListenPort(port)
                .create()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create server (name=$name, port=$port): ${e.message}")
            null
        }
    }

    fun stop() {
        try {
            zeroconfServer?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing Zeroconf server", e)
        }
        zeroconfServer = null
        releaseMulticastLock()
        _state.postValue(ZeroconfState.IDLE)
    }

    override fun sessionChanged(session: xyz.gianlu.librespot.core.Session) {
        Log.i(TAG, "Zeroconf session established for user: ${session.username()}")
        _state.postValue(ZeroconfState.CONNECTED)
        app.authRepository.onZeroconfSession(session)
    }

    override fun sessionClosing(session: xyz.gianlu.librespot.core.Session) {
        Log.i(TAG, "Zeroconf session closing")
    }

    private fun acquireMulticastLock() {
        try {
            val wifiManager = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("spoldify_zeroconf").apply {
                setReferenceCounted(true)
                acquire()
            }
            Log.d(TAG, "Multicast lock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire multicast lock", e)
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
            multicastLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release multicast lock", e)
        }
    }

    enum class ZeroconfState {
        IDLE,
        STARTING,
        WAITING,
        CONNECTED,
        ERROR
    }

    companion object {
        private const val TAG = "ZeroconfManager"
        const val ZEROCONF_PORT = 38475
    }
}
