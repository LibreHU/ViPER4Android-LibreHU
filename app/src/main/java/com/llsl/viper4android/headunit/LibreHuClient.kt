package com.llsl.viper4android.headunit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.librehu.service.ILibreHuService

/**
 * Connection to LibreHU-service (https://github.com/LibreHU/LibreHU-service), the open replacement of Jancar
 * ivi-services. It drives the head unit audio processor (ROHM BD37534), which sits after Android's mixer: volume,
 * fader, balance, tone, loudness and subwoofer apply to every source, unlike ViPER effects.
 */
class LibreHuClient(
    private val context: Context,
) {
    private val _service = MutableStateFlow<ILibreHuService?>(null)
    val service: StateFlow<ILibreHuService?> = _service.asStateFlow()
    private var bound = false

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?,
            ) {
                _service.value = binder?.let { ILibreHuService.Stub.asInterface(it) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                _service.value = null
            }
        }

    fun bind(): Boolean {
        if (bound) return true
        bound =
            try {
                context.bindService(Intent(ACTION_BIND).setPackage(PACKAGE), connection, Context.BIND_AUTO_CREATE)
            } catch (_: SecurityException) {
                false
            }
        return bound
    }

    fun unbind() {
        if (bound) {
            try {
                context.unbindService(connection)
            } catch (_: IllegalArgumentException) {
            }
        }
        bound = false
        _service.value = null
    }

    companion object {
        const val PACKAGE = "org.librehu.service"
        const val ACTION_BIND = "org.librehu.service.BIND"

        // Vehicle flags of the API (org.librehu.service.LibreHu).
        const val FLAG_MCU_ONLINE = 1 shl 0
        const val FLAG_ACC = 1 shl 1

        /** True when LibreHU-service is installed. */
        fun isSupported(context: Context): Boolean =
            try {
                context.packageManager.getPackageInfo(PACKAGE, 0)
                true
            } catch (_: Exception) {
                false
            }
    }
}
