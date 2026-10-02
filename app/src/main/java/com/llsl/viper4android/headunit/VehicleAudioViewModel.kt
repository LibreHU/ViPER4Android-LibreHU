package com.llsl.viper4android.headunit

import android.app.Application
import android.os.RemoteException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.librehu.service.ILibreHuCallback
import org.librehu.service.ILibreHuService
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** Settings of the audio processor as reported by LibreHU-service. */
data class HuAudio(
    val volume: Int = 0,
    val maxVolume: Int = 40,
    val muted: Boolean = false,
    val bass: Int = 10,
    val middle: Int = 10,
    val treble: Int = 10,
    val balance: Int = 30,
    val fade: Int = 30,
    val loudness: Int = 0,
    val subwoofer: Boolean = false,
    val subLevel: Int = 6,
    val externalAmp: Boolean = false,
)

data class VehicleAudioState(
    val supported: Boolean = false,
    val connected: Boolean = false,
    val status: String = "",
    val running: Boolean = false,
    val acc: Boolean = false,
    val audio: HuAudio = HuAudio(),
)

@HiltViewModel
class VehicleAudioViewModel
    @Inject
    constructor(
        application: Application,
    ) : AndroidViewModel(application) {
        private val client = LibreHuClient(application)

        private val _state = MutableStateFlow(VehicleAudioState(supported = LibreHuClient.isSupported(application)))
        val state: StateFlow<VehicleAudioState> = _state.asStateFlow()

        // Slider moves are coalesced: only the latest call per setting is sent to the service.
        private val pending = ConcurrentHashMap<String, (ILibreHuService) -> Unit>()
        private val wakeUp = Channel<Unit>(Channel.CONFLATED)

        @Volatile
        private var lastLocalChange = 0L

        private val callback =
            object : ILibreHuCallback.Stub() {
                override fun onVehicleFlags(flags: Int) = reload()

                // Ignore the echo of our own changes while the user drags a slider.
                override fun onAudioChanged() {
                    if (pending.isEmpty() && System.currentTimeMillis() - lastLocalChange > ECHO_WINDOW_MS) reload()
                }

                override fun onMcuFrame(
                    cmd: Int,
                    data: ByteArray?,
                    fromMcu: Boolean,
                ) {}

                override fun onKey(
                    channel: Int,
                    values: IntArray?,
                    released: Boolean,
                    learning: Boolean,
                ) {}

                override fun onCanData(data: ByteArray?) {}
            }

        init {
            if (_state.value.supported) {
                viewModelScope.launch {
                    client.service.collect { s ->
                        _state.update { it.copy(connected = s != null) }
                        if (s != null) {
                            remote { it.registerCallback(callback) }
                            reload()
                        }
                    }
                }
                viewModelScope.launch(Dispatchers.IO) {
                    for (signal in wakeUp) {
                        val s = client.service.value ?: continue
                        for (key in pending.keys.toList()) {
                            val op = pending.remove(key) ?: continue
                            try {
                                op(s)
                            } catch (_: RemoteException) {
                            }
                        }
                        delay(SEND_INTERVAL_MS)
                    }
                }
                client.bind()
            }
        }

        fun reload() {
            viewModelScope.launch(Dispatchers.IO) {
                val s = client.service.value ?: return@launch
                try {
                    val tone = s.tone
                    val bf = s.balanceFade
                    val flags = s.vehicleFlags
                    val status = s.status
                    val audio =
                        HuAudio(
                            volume = s.volume,
                            maxVolume = s.maxVolume,
                            muted = s.isMuted,
                            bass = tone[0],
                            middle = tone[1],
                            treble = tone[2],
                            balance = bf[0],
                            fade = bf[1],
                            loudness = s.loudness,
                            subwoofer = s.isSubwooferOn,
                            subLevel = s.subwooferLevel,
                            externalAmp = s.isExternalAmpEnabled,
                        )
                    _state.update {
                        it.copy(
                            status = status,
                            running = status.startsWith("RUNNING"),
                            acc = flags and LibreHuClient.FLAG_ACC != 0,
                            audio = audio,
                        )
                    }
                } catch (_: RemoteException) {
                }
            }
        }

        /** Applies [change] to the displayed state now and sends [op] (coalesced under [key]) to the service. */
        private fun edit(
            key: String,
            change: (HuAudio) -> HuAudio,
            op: (ILibreHuService) -> Unit,
        ) {
            lastLocalChange = System.currentTimeMillis()
            _state.update { it.copy(audio = change(it.audio)) }
            pending[key] = op
            wakeUp.trySend(Unit)
        }

        fun setVolume(v: Int) = edit("volume", { it.copy(volume = v) }) { it.setVolume(v) }

        fun setMuted(m: Boolean) = edit("mute", { it.copy(muted = m) }) { it.setMuted(m) }

        fun setTone(
            bass: Int,
            middle: Int,
            treble: Int,
        ) = edit("tone", { it.copy(bass = bass, middle = middle, treble = treble) }) { it.setTone(bass, middle, treble) }

        fun setBalanceFade(
            balance: Int,
            fade: Int,
        ) = edit("bf", { it.copy(balance = balance, fade = fade) }) { it.setBalanceFade(balance, fade) }

        fun setLoudness(v: Int) = edit("loudness", { it.copy(loudness = v) }) { it.setLoudness(v) }

        fun setSubwoofer(
            on: Boolean,
            level: Int,
        ) = edit("sub", { it.copy(subwoofer = on, subLevel = level) }) { it.setSubwoofer(on, level) }

        fun setExternalAmp(on: Boolean) = edit("amp", { it.copy(externalAmp = on) }) { it.setExternalAmpEnabled(on) }

        fun resetToDefaults() {
            val d = HuAudio()
            setTone(d.bass, d.middle, d.treble)
            setBalanceFade(d.balance, d.fade)
            setLoudness(d.loudness)
        }

        private inline fun remote(block: (ILibreHuService) -> Unit) {
            val s = client.service.value ?: return
            try {
                block(s)
            } catch (_: RemoteException) {
            }
        }

        override fun onCleared() {
            remote { it.unregisterCallback(callback) }
            client.unbind()
            wakeUp.close()
            super.onCleared()
        }

        private companion object {
            const val ECHO_WINDOW_MS = 1000L
            const val SEND_INTERVAL_MS = 30L
        }
    }
