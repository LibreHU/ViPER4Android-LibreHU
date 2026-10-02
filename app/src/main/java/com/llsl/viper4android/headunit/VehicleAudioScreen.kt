package com.llsl.viper4android.headunit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llsl.viper4android.R
import com.llsl.viper4android.ui.components.LabeledSlider
import com.llsl.viper4android.ui.components.LabeledSwitch
import com.llsl.viper4android.ui.components.UiDimens
import com.llsl.viper4android.ui.screens.main.EffectSection
import kotlin.math.roundToInt

/**
 * Settings of the head unit audio processor (after Android's mixer): fader, balance, tone,
 * hardware EQ, loudness, subwoofer and per-speaker delay. Only what the chip reports as available
 * is shown, with the ranges ivi-services gives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleAudioScreen(
    onBack: () -> Unit,
    viewModel: VehicleAudioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vehicle_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.vehicle_back))
                    }
                },
                actions = {
                    if (state.connected) {
                        IconButton(onClick = viewModel::reload) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.vehicle_reload))
                        }
                        IconButton(onClick = viewModel::resetToDefaults) {
                            Icon(Icons.Default.SettingsBackupRestore, contentDescription = stringResource(R.string.vehicle_reset))
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        },
    ) { padding ->
        when {
            !state.supported -> StatusCard(stringResource(R.string.vehicle_not_supported), Modifier.padding(padding))
            !state.connected -> StatusCard(stringResource(R.string.vehicle_connecting), Modifier.padding(padding))
            else -> VehicleAudioContent(state, viewModel, Modifier.padding(padding))
        }
    }
}

@Composable
private fun StatusCard(
    text: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().padding(UiDimens.Medium)) {
        Text(text = text, modifier = Modifier.padding(UiDimens.Medium), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun VehicleAudioContent(
    state: VehicleAudioState,
    viewModel: VehicleAudioViewModel,
    modifier: Modifier = Modifier,
) {
    val p = state.params
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = UiDimens.Medium),
    ) {
        if (state.loading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        item { Spacer(modifier = Modifier.height(UiDimens.Medium)) }

        p[HeadUnitParam.BALANCE_FADE]?.let { bf ->
            item { FaderBalanceSection(bf, viewModel) }
        }

        val tone = listOf(HeadUnitParam.BASS, HeadUnitParam.MIDDLE, HeadUnitParam.TREBLE).mapNotNull { p[it] }
        if (tone.isNotEmpty()) {
            item {
                EffectSection(
                    title = stringResource(R.string.section_vehicle_tone),
                    enabled = true,
                    onEnabledChange = {},
                    descriptionRes = R.string.desc_vehicle_tone,
                    icon = Icons.Default.GraphicEq,
                    hasEnableSwitch = false,
                    initiallyExpanded = true,
                ) {
                    for (param in tone) {
                        val label =
                            when (param.id) {
                                HeadUnitParam.BASS -> R.string.label_vehicle_bass
                                HeadUnitParam.MIDDLE -> R.string.label_vehicle_middle
                                else -> R.string.label_vehicle_treble
                            }
                        ParamSlider(stringResource(label), param, viewModel, centered = true)
                    }
                }
            }
        }

        if (state.eqBands.isNotEmpty()) {
            item {
                EffectSection(
                    title = stringResource(R.string.section_vehicle_eq),
                    enabled = true,
                    onEnabledChange = {},
                    descriptionRes = R.string.desc_vehicle_eq,
                    icon = Icons.Default.Equalizer,
                    hasEnableSwitch = false,
                    initiallyExpanded = true,
                ) {
                    for (band in state.eqBands) {
                        val param = p[HeadUnitParam.eqGain(band)] ?: continue
                        val freq = state.eqCenterFreqs[band]
                        val label = if (freq != null && freq > 0) formatHz(freq) else "#${band + 1}"
                        ParamSlider(label, param, viewModel, centered = true)
                    }
                }
            }
        }

        val sub =
            listOf(
                HeadUnitParam.LOUDNESS,
                HeadUnitParam.SUBWOOFER,
                HeadUnitParam.SUB_LPF,
                HeadUnitParam.SUB_HPF,
                HeadUnitParam.POSITION,
            ).mapNotNull { p[it] }
        val subSwitch = p[HeadUnitParam.SUBWOOFER_SWITCH]
        if (sub.isNotEmpty() || subSwitch != null) {
            item {
                EffectSection(
                    title = stringResource(R.string.section_vehicle_sub),
                    enabled = true,
                    onEnabledChange = {},
                    descriptionRes = R.string.desc_vehicle_sub,
                    icon = Icons.Default.SurroundSound,
                    hasEnableSwitch = false,
                    initiallyExpanded = true,
                ) {
                    subSwitch?.let { sw ->
                        LabeledSwitch(
                            label = stringResource(R.string.label_vehicle_sub_switch),
                            checked = sw.value != sw.min,
                            onCheckedChange = { on -> viewModel.set(sw.id, if (on) sw.max else sw.min) },
                        )
                    }
                    for (param in sub) {
                        val label =
                            when (param.id) {
                                HeadUnitParam.LOUDNESS -> R.string.label_vehicle_loudness
                                HeadUnitParam.SUBWOOFER -> R.string.label_vehicle_subwoofer
                                HeadUnitParam.SUB_LPF -> R.string.label_vehicle_sub_lpf
                                HeadUnitParam.SUB_HPF -> R.string.label_vehicle_sub_hpf
                                else -> R.string.label_vehicle_position
                            }
                        ParamSlider(stringResource(label), param, viewModel)
                    }
                }
            }
        }

        if (state.extAmpAvailable) {
            item {
                EffectSection(
                    title = stringResource(R.string.section_vehicle_ext_amp),
                    enabled = true,
                    onEnabledChange = {},
                    descriptionRes = R.string.desc_vehicle_ext_amp,
                    icon = Icons.Default.PowerSettingsNew,
                    hasEnableSwitch = false,
                    initiallyExpanded = true,
                ) {
                    LabeledSwitch(
                        label = stringResource(R.string.label_vehicle_ext_amp),
                        checked = state.extAmpOn,
                        onCheckedChange = viewModel::setExternalAmp,
                    )
                    if (!state.extAmpSaved) {
                        Text(
                            text = stringResource(R.string.vehicle_ext_amp_not_saved),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        val delays =
            listOf(
                HeadUnitParam.DELAY_FL to R.string.label_vehicle_delay_fl,
                HeadUnitParam.DELAY_FR to R.string.label_vehicle_delay_fr,
                HeadUnitParam.DELAY_RL to R.string.label_vehicle_delay_rl,
                HeadUnitParam.DELAY_RR to R.string.label_vehicle_delay_rr,
            ).mapNotNull { (id, label) -> p[id]?.let { it to label } }
        if (delays.isNotEmpty()) {
            item {
                EffectSection(
                    title = stringResource(R.string.section_vehicle_delay),
                    enabled = true,
                    onEnabledChange = {},
                    descriptionRes = R.string.desc_vehicle_delay,
                    icon = Icons.Default.Timer,
                    hasEnableSwitch = false,
                    initiallyExpanded = true,
                ) {
                    for ((param, label) in delays) ParamSlider(stringResource(label), param, viewModel)
                }
            }
        }
    }
}

@Composable
private fun FaderBalanceSection(
    bf: HuParam,
    viewModel: VehicleAudioViewModel,
) {
    val balance = HeadUnitParam.balanceOf(bf.value)
    val fade = HeadUnitParam.fadeOf(bf.value)
    val center = (bf.min + bf.max) / 2
    val range = bf.min.toFloat()..bf.max.toFloat()
    val steps = (bf.max - bf.min - 1).coerceAtLeast(0)
    EffectSection(
        title = stringResource(R.string.section_vehicle_fader),
        enabled = true,
        onEnabledChange = {},
        descriptionRes = R.string.desc_vehicle_fader,
        icon = Icons.Default.SpeakerGroup,
        hasEnableSwitch = false,
        initiallyExpanded = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiDimens.Small)) {
            LabeledSlider(
                label = stringResource(R.string.label_vehicle_balance),
                value = balance.toFloat(),
                onValueChange = { viewModel.setBalanceFade(it.roundToInt(), fade) },
                valueRange = range,
                steps = steps,
                valueLabel = sideLabel(balance - center, "L", "R"),
            )
            LabeledSlider(
                label = stringResource(R.string.label_vehicle_fade),
                value = fade.toFloat(),
                onValueChange = { viewModel.setBalanceFade(balance, it.roundToInt()) },
                valueRange = range,
                steps = steps,
                // ivi-services: fade 0 = front, max = rear (stock app: driver seat = (0, 0)).
                valueLabel = sideLabel(fade - center, "F", "R"),
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { viewModel.setBalanceFade(center, center) }) {
                    Icon(Icons.Default.SettingsBackupRestore, contentDescription = stringResource(R.string.vehicle_center))
                }
            }
        }
    }
}

@Composable
private fun ParamSlider(
    label: String,
    param: HuParam,
    viewModel: VehicleAudioViewModel,
    centered: Boolean = false,
) {
    if (param.max <= param.min) return
    val center = (param.min + param.max) / 2
    LabeledSlider(
        label = label,
        value = param.value.toFloat(),
        onValueChange = { viewModel.set(param.id, it.roundToInt()) },
        valueRange = param.min.toFloat()..param.max.toFloat(),
        steps = (param.max - param.min - 1).coerceAtLeast(0),
        valueLabel = if (centered) signed(param.value - center) else param.value.toString(),
    )
}

private fun signed(v: Int): String = if (v > 0) "+$v" else v.toString()

/** Offset from center as "L3" / "C" / "R5" (or F/R for the fader). */
private fun sideLabel(
    offset: Int,
    negative: String,
    positive: String,
): String =
    when {
        offset < 0 -> "$negative${-offset}"
        offset > 0 -> "$positive$offset"
        else -> "C"
    }

private fun formatHz(hz: Int): String = if (hz >= 1000 && hz % 100 == 0) "${hz / 1000.0} kHz".replace(".0 ", " ") else "$hz Hz"
