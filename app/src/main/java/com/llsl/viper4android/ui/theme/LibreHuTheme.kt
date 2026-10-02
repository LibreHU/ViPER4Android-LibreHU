package com.llsl.viper4android.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Effective dark theme of the app, provided by [ViperTheme]. */
val LocalDarkTheme = compositionLocalOf { true }

private const val ACTION_THEME_CHANGED = "org.librehu.action.THEME_CHANGED"
private val LAUNCHER_THEME_URI: Uri = Uri.parse("content://org.librehu.launcher.theme/theme")

/** Light / dark theme of LibreHU Launcher (null when it is not installed). */
private fun readLauncherDark(context: Context): Boolean? =
    try {
        context.contentResolver.query(LAUNCHER_THEME_URI, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getInt(0) != 0 else null
        }
    } catch (_: Exception) {
        null
    }

/**
 * LibreHU: follows LibreHU Launcher's theme (light, dark, or automatic with the headlights / time of day), live;
 * without the launcher, Android's dark theme as before.
 */
@Composable
fun rememberLibreHuDarkTheme(): Boolean {
    val system = isSystemInDarkTheme()
    val context = LocalContext.current
    var launcher by remember { mutableStateOf(readLauncherDark(context)) }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    intent: Intent,
                ) {
                    launcher = intent.getBooleanExtra("dark", true)
                }
            }
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    launcher = readLauncherDark(context)
                }
            }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_THEME_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        try {
            context.contentResolver.registerContentObserver(LAUNCHER_THEME_URI, false, observer)
        } catch (_: SecurityException) {
        }
        onDispose {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return launcher ?: system
}
