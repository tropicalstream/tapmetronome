package com.tropicalstream.tapmetronome.metro

import kotlin.math.roundToInt

/**
 * Tap-tempo detector: averages the intervals of a recent burst of taps and
 * returns the implied BPM. A gap longer than [RESET_MS] starts a fresh burst.
 */
class TapTempo {
    private val taps = ArrayDeque<Long>()

    /** Feed a tap time (ms); returns a new BPM once there are ≥2 taps, else null. */
    fun tap(nowMs: Long): Int? {
        if (taps.isNotEmpty() && nowMs - taps.last() > RESET_MS) taps.clear()
        taps.addLast(nowMs)
        while (taps.size > MAX_TAPS) taps.removeFirst()
        if (taps.size < 2) return null

        var sum = 0L
        for (i in 1 until taps.size) sum += taps[i] - taps[i - 1]
        val avg = sum.toDouble() / (taps.size - 1)
        if (avg <= 0) return null
        return (60000.0 / avg).roundToInt().coerceIn(Metronome.MIN_BPM, Metronome.MAX_BPM)
    }

    companion object {
        private const val RESET_MS = 2000L
        private const val MAX_TAPS = 5
    }
}
