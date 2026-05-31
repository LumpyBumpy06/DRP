package com.drp33.quietsignal.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

private fun Context.vibrator(): Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

/** A single short buzz — used when recording starts. */
fun Context.vibrateTick(durationMs: Long = 35L) {
    val vibrator = vibrator()
    if (!vibrator.hasVibrator()) return
    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
}

/** A double tap — used when recording stops. */
fun Context.vibrateDoubleTap() {
    val vibrator = vibrator()
    if (!vibrator.hasVibrator()) return
    // wait, buzz, gap, buzz
    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 90, 30), -1))
}
