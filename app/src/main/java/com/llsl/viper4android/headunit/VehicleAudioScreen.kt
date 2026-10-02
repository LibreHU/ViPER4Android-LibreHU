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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

private const val TONE_MAX = 20
private const val FADER_MAX = 60
private const val FADER_CENTER = 30
private const val LOUDNESS_MAX = 15
private const val SUB_MAX = 12

/**
 * Settings of the head unit audio processor (after Android's mixer) through LibreHU-service: volume, fader,
 * balance, tone, loudness, subwoofer and the external amplifier remote output.
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
    val a = state.audio
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = UiDimens.Medium),
    ) {
        if (state.status.isNotEmpty() && !state.running) {
            item { StatusCard(stringResource(R.string.vehicle_not_running, state.status)) }
        } else if (state.running && !state.acc) {
            item { StatusCard(stringResource(R.string.vehicle_acc_off)) }
        }
        item { Spacer(modifier = Modifier.height(UiDimens.Medium)) }

        item {
            Section(R.string.section_vehicle_volume, R.string.desc_vehicle_volume, Icons.AutoMirrored.Filled.VolumeUp) {
                IntSlider(stringResource(R.string.label_vehicle_volume), a.volume, a.maxVolume) { viewModel.setVolume(it) }
                LabeledSwitch(
                    label = stringResource(R.string.label_vehicle_mute),
                    checked = a.muted,
                    onCheckedChange = viewModel::setMuted,
                )
            }
        }

        item { FaderBalanceSection(a, viewModel) }

        item {
            Section(R.string.section_vehicle_tone, R.string.desc_vehicle_tone, Icons.Default.GraphicEq) {
                IntSlider(stringResource(R.string.label_vehicle_bass), a.bass, TONE_MAX, centered = true) {
                    viewModel.setTone(it, a.middle, a.treble)
                }
                IntSlider(stringResource(R.string.label_vehicle_middle), a.middle, TONE_MAX, centered = true) {
                    viewModel.setTone(a.bass, it, a.treble)
                }
                IntSlider(stringResource(R.string.label_vehicle_treble), a.treble, TONE_MAX, centered = true) {
                    viewModel.setTone(a.bass, a.middle, it)
                }
            }
        }

        item {
            Section(R.string.section_vehicle_sub, R.string.desc_vehicle_sub, Icons.Default.SurroundSound) {
                IntSlider(stringResource(R.string.label_vehicle_loudness), a.loudness, LOUDNESS_MAX) { viewModel.setLoudness(it) }
                LabeledSwitch(
                    label = stringResource(R.string.label_vehicle_sub_switch),
                    checked = a.subwoofer,
                    onCheckedChange = { viewModel.setSubwoofer(it, a.subLevel) },
                )
                // Level 0..12 = -5..+7 dB on board A0_AN.
                IntSlider(stringResource(R.string.label_vehicle_subwoofer), a.subLevel, SUB_MAX, label = { signed(it - 5) + " dB" }) {
                    viewModel.setSubwoofer(a.subwoofer, it)
                }
            }
        }

        item {
            Section(R.string.section_vehicle_ext_amp, R.string.desc_vehicle_ext_amp, Icons.Default.PowerSettingsNew) {
                LabeledSwitch(
                    label = stringResource(R.string.label_vehicle_ext_amp),
                    checked = a.externalAmp,
                    onCheckedChange = viewModel::setExternalAmp,
                )
            }
        }
    }
}

@Composable
private fun Section(
    title: Int,
    description: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    EffectSection(
        title = stringResource(title),
        enabled = true,
        onEnabledChange = {},
        descriptionRes = description,
        icon = icon,
        hasEnableSwitch = false,
        initiallyExpanded = true,
    ) {
        content()
    }
}

@Composable
private fun FaderBalanceSection(
    a: HuAudio,
    viewModel: VehicleAudioViewModel,
) {
    Section(R.string.section_vehicle_fader, R.string.desc_vehicle_fader, Icons.Default.SpeakerGroup) {
        Column(verticalArrangement = Arrangement.spacedBy(UiDimens.Small)) {
            IntSlider(stringResource(R.string.label_vehicle_balance), a.balance, FADER_MAX, label = {
                sideLabel(it - FADER_CENTER, "L", "R")
            }) { viewModel.setBalanceFade(it, a.fade) }
            // Fade 0 = front, 60 = rear (ivi-services convention, kept by LibreHU-service).
            IntSlider(stringResource(R.string.label_vehicle_fade), a.fade, FADER_MAX, label = {
                sideLabel(it - FADER_CENTER, "F", "R")
            }) { viewModel.setBalanceFade(a.balance, it) }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { viewModel.setBalanceFade(FADER_CENTER, FADER_CENTER) }) {
                    Icon(Icons.Default.SettingsBackupRestore, contentDescription = stringResource(R.string.vehicle_center))
                }
            }
        }
    }
}

@Composable
private fun IntSlider(
    title: String,
    value: Int,
    max: Int,
    centered: Boolean = false,
    label: (Int) -> String = { if (centered) signed(it - max / 2) else it.toString() },
    onChange: (Int) -> Unit,
) {
    LabeledSlider(
        label = title,
        value = value.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = 0f..max.toFloat(),
        steps = (max - 1).coerceAtLeast(0),
        valueLabel = label(value),
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
