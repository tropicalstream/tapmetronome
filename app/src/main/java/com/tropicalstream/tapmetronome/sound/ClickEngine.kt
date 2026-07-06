package com.tropicalstream.tapmetronome.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Synthesized metronome clicks (no asset files): very short enveloped tone bursts
 * (woodblock-ish). Downbeat is higher + louder. [silent] is the Zero-Bleed / Studio
 * mode — kills all audio while the visual pulse keeps time. All runCatching-wrapped.
 */
class ClickEngine {

    @Volatile var silent = false

    private val sr = 44100
    private val exec = Executors.newSingleThreadExecutor { r ->
        Thread(r, "tapmetro-audio").apply { isDaemon = true }
    }

    fun downbeat() = play(2050.0, 22, 0.65f)
    fun beat() = play(1500.0, 20, 0.45f)
    fun sub() = play(2600.0, 10, 0.18f)

    private fun play(freq: Double, ms: Int, vol: Float) {
        if (silent) return
        exec.execute {
            runCatching {
                val n = sr * ms / 1000
                val buf = ShortArray(n)
                val atk = (n * 0.02f).toInt().coerceAtLeast(1)
                for (i in 0 until n) {
                    val t = i.toDouble() / sr
                    // Fast exponential-ish decay for a click, quick attack.
                    val env = (if (i < atk) i.toFloat() / atk else 1f) *
                        (1f - i.toFloat() / n) * (1f - i.toFloat() / n)
                    buf[i] = (sin(2 * PI * freq * t) * env * vol * Short.MAX_VALUE).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sr)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buf.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(buf, 0, buf.size)
                track.play()
                Thread.sleep(ms + 40L)
                runCatching { track.stop() }
                track.release()
            }
        }
    }

    fun release() {
        exec.shutdownNow()
    }
}
