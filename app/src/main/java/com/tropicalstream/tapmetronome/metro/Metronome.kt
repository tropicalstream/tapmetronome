package com.tropicalstream.tapmetronome.metro

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor

/** Time signature preset: [beats] per measure, [subdiv] ticks per beat (6/8 = 2×3). */
data class TimeSig(val label: String, val beats: Int, val subdiv: Int)

/**
 * The metronome clock. Phase is an accumulator (beats elapsed) advanced by real
 * elapsed time each frame, so changing BPM never disrupts continuity and there's
 * no re-anchoring. The visual reads [pendulum]/[beatFraction] every frame for
 * smooth anticipatory motion; audio fires from the [onBeat]/[onSub] crossings.
 */
class Metronome {

    companion object {
        val TIME_SIGS = listOf(
            TimeSig("4/4", 4, 1),
            TimeSig("3/4", 3, 1),
            TimeSig("2/4", 2, 1),
            TimeSig("6/8", 2, 3),
            TimeSig("5/4", 5, 1)
        )
        const val MIN_BPM = 30
        const val MAX_BPM = 300
    }

    var bpm = 100
        set(value) {
            field = value.coerceIn(MIN_BPM, MAX_BPM)
        }
    var running = true
        private set
    var gapTrainer = false
        private set
    private var sigIndex = 0
    val sig: TimeSig get() = TIME_SIGS[sigIndex]

    private var phaseBeats = 0.0
    private var lastNanos = 0L
    private var lastBeatIndex = -1
    private var lastSubIndex = -1

    /** (beatInMeasure, isDownbeat, muted) on every beat crossing. */
    var onBeat: ((Int, Boolean, Boolean) -> Unit)? = null
    /** (muted) on every off-beat subdivision crossing. */
    var onSub: ((Boolean) -> Unit)? = null

    fun update(nowNanos: Long) {
        if (lastNanos == 0L) lastNanos = nowNanos
        if (!running) {
            lastNanos = nowNanos
            return
        }
        // Clamp so a resume/hitch can't fast-forward the beat.
        val dt = ((nowNanos - lastNanos) / 1e9).coerceIn(0.0, 0.1)
        lastNanos = nowNanos
        phaseBeats += dt * bpm / 60.0

        val bi = floor(phaseBeats).toInt()
        if (bi != lastBeatIndex) {
            lastBeatIndex = bi
            val bim = Math.floorMod(bi, sig.beats)
            onBeat?.invoke(bim, bim == 0, isGapBeat(bi))
        }
        if (sig.subdiv > 1) {
            val si = floor(phaseBeats * sig.subdiv).toInt()
            if (si != lastSubIndex) {
                lastSubIndex = si
                if (si % sig.subdiv != 0) onSub?.invoke(isGapBeat(si / sig.subdiv))
            }
        }
    }

    /** True while inside the muted/blanked 4th measure of a 4-measure gap cycle. */
    private fun isGapBeat(beatIndex: Int): Boolean {
        if (!gapTrainer) return false
        return Math.floorMod(Math.floorDiv(beatIndex, sig.beats), 4) == 3
    }

    fun isGapNow(): Boolean = isGapBeat(floor(phaseBeats).toInt())

    // ---- continuous readouts for rendering ----
    /** −1..+1 pendulum position; at an extreme exactly on each beat. */
    fun pendulum(): Float = cos(PI * phaseBeats).toFloat()
    fun beatFraction(): Float = (phaseBeats - floor(phaseBeats)).toFloat()
    fun beatInMeasure(): Int = Math.floorMod(floor(phaseBeats).toInt(), sig.beats)

    // ---- controls ----
    fun adjustBpm(delta: Int) {
        bpm += delta
    }

    /** Set tempo from tap-tempo and re-anchor so the next beat lands on the tap. */
    fun setBpmAligned(newBpm: Int) {
        bpm = newBpm
        phaseBeats = 0.0
        lastBeatIndex = -1
        lastSubIndex = -1
    }

    fun toggleRun() {
        running = !running
    }

    /** Re-anchor the clock after a resume so paused time isn't counted. */
    fun resync(nowNanos: Long) {
        lastNanos = nowNanos
    }

    fun cycleTimeSig(dir: Int) {
        val n = TIME_SIGS.size
        sigIndex = ((sigIndex + (if (dir >= 0) 1 else -1)) % n + n) % n
        phaseBeats = 0.0
        lastBeatIndex = -1
        lastSubIndex = -1
    }

    fun toggleGapTrainer() {
        gapTrainer = !gapTrainer
    }
}
