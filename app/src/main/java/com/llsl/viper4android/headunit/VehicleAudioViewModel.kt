package com.llsl.viper4android.headunit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** One processor setting as reported by ivi-services. */
data class HuParam(
    val id: Int,
    val min: Int,
    val max: Int,
    val default: Int,
    val value: Int,
)

data class VehicleAudioState(
    val supported: Boolean = false,
    val connected: Boolean = false,
    val loading: Boolean = false,
    val params: Map<Int, HuParam> = emptyMap(),
    val eqBands: List<Int> = emptyList(),
    val eqCenterFreqs: Map<Int, Int> = emptyMap(),
)

@HiltViewModel
class VehicleAudioViewModel
    @Inject
    constructor(
        application: Application,
    ) : AndroidViewModel(application) {
        private val client = JancarAudioClient(application)

        private val _state = MutableStateFlow(VehicleAudioState(supported = JancarAudioClient.isSupported(application)))
        val state: StateFlow<VehicleAudioState> = _state.asStateFlow()

        // Slider moves are coalesced: only the latest value per id is sent to the chip.
        private val pending = ConcurrentHashMap<Int, Int>()
        private val wakeUp = Channel<Unit>(Channel.CONFLATED)

        init {
            if (_state.value.supported) {
                viewModelScope.launch {
                    client.connected.collect { connected ->
                        _state.update { it.copy(connected = connected) }
                        if (connected) reload()
                    }
                }
                viewModelScope.launch(Dispatchers.IO) {
                    for (signal in wakeUp) {
                        val ids = pending.keys.toList()
                        for (id in ids) {
                            val v = pending.remove(id) ?: continue
                            client.setParam(id, v)
                        }
                    }
                }
                client.bind()
            }
        }

        fun reload() {
            viewModelScope.launch(Dispatchers.IO) {
                _state.update { it.copy(loading = true) }
                val params = mutableMapOf<Int, HuParam>()
                for (id in SCALAR_IDS) readParam(id)?.let { params[id] = it }

                val reportedEqCount = client.getParam(HeadUnitParam.EQ_COUNT)?.coerceIn(0, MAX_EQ_BANDS) ?: 0
                // ROHM BD37534 (EQ_COUNT = 6) has no real 6-band EQ: in libJanCarIVI.so, bands 0-2 write the
                // same treble/middle/bass gain registers as the Tone section, and bands 3-5 select the
                // frequency/Q of those filters. Showing them as EQ bands would duplicate Tone with wrong labels.
                val eqCount = if (reportedEqCount == BD37534_EQ_COUNT) 0 else reportedEqCount
                val bands = mutableListOf<Int>()
                val freqs = mutableMapOf<Int, Int>()
                for (band in 0 until eqCount) {
                    val p = readParam(HeadUnitParam.eqGain(band)) ?: continue
                    params[p.id] = p
                    bands += band
                    client.getParam(HeadUnitParam.eqCenterFreq(band))?.let { freqs[band] = it }
                }
                _state.update { it.copy(loading = false, params = params, eqBands = bands, eqCenterFreqs = freqs) }
            }
        }

        fun set(
            id: Int,
            value: Int,
        ) {
            val current = _state.value.params[id] ?: return
            val v = if (id == HeadUnitParam.BALANCE_FADE) value else value.coerceIn(current.min, current.max)
            _state.update { s -> s.copy(params = s.params + (id to current.copy(value = v))) }
            pending[id] = v
            wakeUp.trySend(Unit)
        }

        fun setBalanceFade(
            balance: Int,
            fade: Int,
        ) = set(HeadUnitParam.BALANCE_FADE, HeadUnitParam.packBalanceFade(balance, fade))

        fun resetToDefaults() {
            for (p in _state.value.params.values) set(p.id, p.default)
        }

        private fun readParam(id: Int): HuParam? {
            if (!client.isParamAvailable(id)) return null
            val min = client.getParamMin(id) ?: return null
            val max = client.getParamMax(id) ?: return null
            val def = client.getParamDefault(id) ?: min
            val value = client.getParam(id) ?: def
            return HuParam(id, min, max, def, value)
        }

        override fun onCleared() {
            client.unbind()
            wakeUp.close()
            super.onCleared()
        }

        private companion object {
            const val MAX_EQ_BANDS = 31
            const val BD37534_EQ_COUNT = 6
            val SCALAR_IDS =
                intArrayOf(
                    HeadUnitParam.BALANCE_FADE,
                    HeadUnitParam.BASS,
                    HeadUnitParam.MIDDLE,
                    HeadUnitParam.TREBLE,
                    HeadUnitParam.LOUDNESS,
                    HeadUnitParam.POSITION,
                    HeadUnitParam.SUBWOOFER,
                    HeadUnitParam.SUBWOOFER_SWITCH,
                    HeadUnitParam.SUB_LPF,
                    HeadUnitParam.SUB_HPF,
                    HeadUnitParam.DELAY_FL,
                    HeadUnitParam.DELAY_FR,
                    HeadUnitParam.DELAY_RL,
                    HeadUnitParam.DELAY_RR,
                )
        }
    }
