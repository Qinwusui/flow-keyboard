package com.flowkeyboard.android.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.flowkeyboard.android.R
import com.flowkeyboard.android.model.KeyboardSettings

class FeedbackEngine(context: Context) : AutoCloseable {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Vibrator::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    ).build()
    private val loaded = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    private val press: Int
    private val turn: Int
    private var lastTick = 0L
    private var closed = false

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        press = pool.load(context, R.raw.key_press, 1)
        turn = pool.load(context, R.raw.belt_tick, 1)
    }

    fun keyPress(settings: KeyboardSettings) {
        if (closed) return
        if (settings.soundEnabled && audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL && press in loaded) {
            pool.play(press, 0.35f, 0.35f, 0, 0, 1f)
        }
        if (settings.hapticsEnabled && settings.vibrationLevel > 0f) {
            performCrispHaptic(settings.vibrationLevel)
        }
    }

    fun beltTick(settings: KeyboardSettings) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTick < 150) return
        lastTick = now
        if (closed) return
        if (settings.soundEnabled && audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL && turn in loaded) {
            pool.play(turn, 0.08f, 0.08f, 0, 0, 1f)
        }
    }

    fun previewVibration(level: Float) {
        if (closed || level <= 0f) return
        performCrispHaptic(level)
    }

    private fun performCrispHaptic(level: Float) {
        if (closed || vibrator?.hasVibrator() != true) return
        val clamped = level.coerceIn(0.01f, 1f)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                vibrator.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, clamped)
                        .compose()
                )
                return
            }
        } catch (_: Throwable) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                return
            }
        } catch (_: Throwable) {}

        val amp = (255 * clamped).toInt().coerceIn(1, 255)
        vibrator.vibrate(VibrationEffect.createOneShot(5, amp))
    }

    override fun close() { if (!closed) { closed = true; pool.release(); vibrator?.cancel() } }
}
