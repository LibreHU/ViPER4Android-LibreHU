package com.llsl.viper4android.headunit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Raw client for the system service of Jancar ivi-services (AIDL `com.jancar.services.system.ISystem`,
 * exported without permission). Only the external amplifier switch is used.
 *
 * `setExternalPowerAmplifier` makes ivi-services send MCU command `44 [0/1]`, which drives the remote
 * (REM) output of an external amplifier. ivi-services re-applies the Settings.Global value
 * [KEY_EXTERNAL_AMP_SWITCH] at ACC on, on every MCU time frame and on screen on/off, so the setting must
 * be written too or the next MCU frame reverts the switch. Reference:
 * https://github.com/LibreHU/LibreHU-service/blob/main/docs/ivi-services/02-hardware.md
 */
class JancarSystemClient(
    private val context: Context,
) {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @Volatile
    private var binder: IBinder? = null
    private var bound = false

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                binder = service
                _connected.value = service != null
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                binder = null
                _connected.value = false
            }
        }

    fun bind(): Boolean {
        if (bound) return true
        bound =
            try {
                context.bindService(
                    Intent(ACTION).setPackage(JancarAudioClient.PACKAGE),
                    connection,
                    Context.BIND_AUTO_CREATE,
                )
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
        binder = null
        _connected.value = false
    }

    /** Saved switch, as read by ivi-services (stock setting "External Power Amplifier Enable"). */
    fun isExternalAmpEnabled(): Boolean = Settings.Global.getInt(context.contentResolver, KEY_EXTERNAL_AMP_SWITCH, 0) == 1

    /** Sends MCU command `44 [0/1]` through ivi-services (ISystem transaction 36). */
    fun setExternalAmpPower(on: Boolean): Boolean {
        val b = binder ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(if (on) 1 else 0)
            b.transact(TX_SET_EXTERNAL_POWER_AMPLIFIER, data, reply, 0)
            reply.readException()
            true
        } catch (_: RemoteException) {
            false
        } catch (_: RuntimeException) {
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * Saves the switch so ivi-services keeps it. Needs WRITE_SECURE_SETTINGS
     * (`adb shell pm grant <package> android.permission.WRITE_SECURE_SETTINGS`, or a privileged install).
     */
    fun saveExternalAmpEnabled(on: Boolean): Boolean =
        try {
            Settings.Global.putInt(context.contentResolver, KEY_EXTERNAL_AMP_SWITCH, if (on) 1 else 0)
        } catch (_: SecurityException) {
            false
        }

    companion object {
        const val ACTION = "com.jancar.services.action.system"
        const val KEY_EXTERNAL_AMP_SWITCH = "external_amp_switch"
        private const val DESCRIPTOR = "com.jancar.services.system.ISystem"
        private const val TX_SET_EXTERNAL_POWER_AMPLIFIER = 36
    }
}
