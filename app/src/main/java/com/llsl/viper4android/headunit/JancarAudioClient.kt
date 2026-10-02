package com.llsl.viper4android.headunit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Raw client for the head unit audio processor service exposed by Jancar ivi-services
 * (AIDL `com.jancar.services.audio.IAudio`, exported without permission).
 *
 * The audio processor (ROHM BD37534 / BU32107, AKM AK7604 depending on the board) sits after
 * Android's mixer: fader, balance, per-speaker delay, hardware EQ and subwoofer live there, not in
 * the Android audio stream processed by ViPER.
 *
 * Calls are raw binder transactions so no Jancar classes are needed. Reference:
 * https://github.com/LibreHU/MCU-tools-app/blob/main/docs/ivi_audio.md
 */
class JancarAudioClient(
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
                    Intent(ACTION).setPackage(PACKAGE),
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

    fun isParamAvailable(id: Int): Boolean = (callInt(TX_IS_PARAM_AVAILABLE, id) ?: 0) != 0

    fun getParamMin(id: Int): Int? = callInt(TX_GET_PARAM_MIN, id)

    fun getParamMax(id: Int): Int? = callInt(TX_GET_PARAM_MAX, id)

    fun getParamDefault(id: Int): Int? = callInt(TX_GET_PARAM_DEFAULT, id)

    fun getParam(id: Int): Int? = callInt(TX_GET_PARAM, id)

    fun setParam(
        id: Int,
        value: Int,
    ): Boolean {
        val b = binder ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(id)
            data.writeInt(value)
            b.transact(TX_SET_PARAM, data, reply, 0)
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

    private fun callInt(
        code: Int,
        arg: Int,
    ): Int? {
        val b = binder ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(arg)
            b.transact(code, data, reply, 0)
            reply.readException()
            reply.readInt()
        } catch (_: RemoteException) {
            null
        } catch (_: RuntimeException) {
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    companion object {
        const val PACKAGE = "com.jancar.services"
        const val ACTION = "com.jancar.services.action.audio"
        private const val DESCRIPTOR = "com.jancar.services.audio.IAudio"

        private const val TX_IS_PARAM_AVAILABLE = 4
        private const val TX_GET_PARAM_MIN = 5
        private const val TX_GET_PARAM_MAX = 6
        private const val TX_GET_PARAM_DEFAULT = 7
        private const val TX_GET_PARAM = 8
        private const val TX_SET_PARAM = 9

        /** True when the Jancar ivi-services package is installed (head unit firmware). */
        fun isSupported(context: Context): Boolean =
            try {
                context.packageManager.getPackageInfo(PACKAGE, 0)
                true
            } catch (_: Exception) {
                false
            }
    }
}

/** `AudioParam.Id` values used by ivi-services (subset relevant to the vehicle screen). */
object HeadUnitParam {
    const val BASS = 21
    const val MIDDLE = 22
    const val TREBLE = 23
    const val BALANCE_FADE = 24
    const val POSITION = 25
    const val LOUDNESS = 27
    const val EQ_COUNT = 31
    const val DELAY_FL = 33
    const val DELAY_FR = 34
    const val DELAY_RL = 35
    const val DELAY_RR = 36
    const val SUBWOOFER = 42
    const val SUBWOOFER_SWITCH = 52
    const val SUB_LPF = 119
    const val SUB_HPF = 120

    fun eqGain(band: Int): Int = 1000 + band

    fun eqCenterFreq(band: Int): Int = 1040 + band

    /** BALANCE_FADE packs both values: ((balance + 100) shl 16) or (fade + 100). */
    fun packBalanceFade(
        balance: Int,
        fade: Int,
    ): Int = ((balance + 100) shl 16) or (fade + 100)

    fun balanceOf(packed: Int): Int = ((packed ushr 16) and 0xFFFF) - 100

    fun fadeOf(packed: Int): Int = (packed and 0xFFFF) - 100
}
