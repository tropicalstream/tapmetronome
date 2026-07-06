package com.tropicalstream.tapmetronome.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import com.tropicalstream.tapmetronome.metro.Metronome
import com.tropicalstream.tapmetronome.sound.ClickEngine
import kotlin.math.min
import kotlin.math.sin

/**
 * Visual metronome, upper-periphery, low footprint so the wearer can read sheet
 * music / see their instrument. Three layout styles (cycle with triple-tap):
 *   0 PENDULUM — a dot swings across a top bar, decelerating into each beat.
 *   1 ARC      — the dot sweeps along a shallow arc (conductor's baton).
 *   2 BORDER   — minimal: only the screen-edge frame pulses (max passthrough).
 *
 * Anticipatory motion: the dot is at an extreme exactly ON each beat (cosine
 * pendulum), so the eye reads the deceleration and predicts the hit. Downbeat is
 * a big green flash; other beats a smaller cyan pulse; subdivisions faint ticks.
 */
class MetronomeView(
    context: Context,
    private val metro: Metronome,
    private val click: ClickEngine
) : View(context) {

    var style = 0
        private set

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val digits = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val arcPath = Path()

    private var vw = 640
    private var vh = 480

    fun cycleStyle(): Int {
        style = (style + 1) % 3
        return style
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        vw = w; vh = h
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)

        val downbeat = metro.beatInMeasure() == 0
        val frac = metro.beatFraction()
        val pulse = if (metro.running) ((1f - frac) * (1f - frac)) else 0f
        val beatColor = if (downbeat) GREEN else CYAN
        val gapNow = metro.isGapNow()

        // Gap-Method: the 4th measure is blanked — the musician holds time alone.
        if (gapNow) {
            text.textSize = vh * 0.06f
            text.color = DIM
            text.alpha = 120
            text.clearShadowLayer()
            canvas.drawText("— keep time —", vw / 2f, vh * 0.16f, text)
            drawReadout(canvas, faint = true)
            drawStatus(canvas)
            return
        }

        when (style) {
            0 -> drawPendulum(canvas, pulse, beatColor)
            1 -> drawArc(canvas, pulse, beatColor)
            2 -> drawBorder(canvas, pulse, beatColor)
        }
        drawBeatDots(canvas, pulse)
        drawReadout(canvas, faint = false)
        drawStatus(canvas)
        drawHint(canvas)
    }

    // ---- style 0: pendulum bar ----
    private fun drawPendulum(canvas: Canvas, pulse: Float, beatColor: Int) {
        val y = vh * 0.13f
        val barL = vw * 0.12f
        val barR = vw * 0.88f
        val center = (barL + barR) / 2f
        val dotR = vh * 0.028f
        val amp = (barR - barL) / 2f - dotR

        stroke.color = DIM
        stroke.alpha = 90
        stroke.strokeWidth = 2f
        canvas.drawLine(barL, y, barR, y, stroke)
        // end anchors
        p.style = Paint.Style.FILL
        p.color = DIM
        p.alpha = 120
        canvas.drawCircle(barL, y, dotR * 0.4f, p)
        canvas.drawCircle(barR, y, dotR * 0.4f, p)

        val x = center + amp * metro.pendulum()
        // flash ring grows on the beat
        if (metro.running) {
            stroke.color = beatColor
            stroke.strokeWidth = 3f
            stroke.alpha = (pulse * 220).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, dotR + pulse * vh * 0.05f, stroke)
        }
        glowDot(canvas, x, y, dotR * (1f + pulse * 0.5f), beatColor)
    }

    // ---- style 1: sweeping arc ----
    private fun drawArc(canvas: Canvas, pulse: Float, beatColor: Int) {
        val baseY = vh * 0.10f
        val sag = vh * 0.10f
        val xL = vw * 0.14f
        val xR = vw * 0.86f
        arcPath.reset()
        var t = 0f
        while (t <= 1f) {
            val px = xL + (xR - xL) * t
            val py = baseY + sag * sin(Math.PI * t).toFloat()
            if (t == 0f) arcPath.moveTo(px, py) else arcPath.lineTo(px, py)
            t += 0.05f
        }
        stroke.color = DIM
        stroke.alpha = 90
        stroke.strokeWidth = 2f
        canvas.drawPath(arcPath, stroke)

        val tt = ((metro.pendulum() + 1f) / 2f).coerceIn(0f, 1f)
        val dx = xL + (xR - xL) * tt
        val dy = baseY + sag * sin(Math.PI * tt).toFloat()
        val dotR = vh * 0.028f
        if (metro.running) {
            stroke.color = beatColor
            stroke.strokeWidth = 3f
            stroke.alpha = (pulse * 220).toInt().coerceIn(0, 255)
            canvas.drawCircle(dx, dy, dotR + pulse * vh * 0.05f, stroke)
        }
        glowDot(canvas, dx, dy, dotR * (1f + pulse * 0.5f), beatColor)
    }

    // ---- style 2: border frame (max transparency) ----
    private fun drawBorder(canvas: Canvas, pulse: Float, beatColor: Int) {
        if (!metro.running) return
        stroke.color = beatColor
        stroke.alpha = (pulse * 230).toInt().coerceIn(0, 255)
        stroke.strokeWidth = vh * (0.010f + pulse * 0.02f)
        val inset = stroke.strokeWidth
        canvas.drawRect(inset, inset, vw - inset, vh - inset, stroke)
    }

    // ---- shared: beat-position dots ----
    private fun drawBeatDots(canvas: Canvas, pulse: Float) {
        val beats = metro.sig.beats
        val cur = metro.beatInMeasure()
        val r = vh * 0.016f
        val gap = vw * 0.045f
        val totalW = (beats - 1) * gap
        var x = vw / 2f - totalW / 2f
        val y = vh * 0.44f
        p.style = Paint.Style.FILL
        for (b in 0 until beats) {
            val on = metro.running && b == cur
            val down = b == 0
            p.color = if (down) GREEN else CYAN
            p.alpha = when {
                on -> (120 + pulse * 135).toInt().coerceIn(0, 255)
                else -> 55
            }
            canvas.drawCircle(x, y, if (on) r * (1f + pulse * 0.6f) else r * 0.8f, p)
            x += gap
        }
    }

    // ---- shared: BPM + time signature ----
    private fun drawReadout(canvas: Canvas, faint: Boolean) {
        val a = if (faint) 90 else 255
        digits.textSize = vh * 0.13f
        digits.color = Color.WHITE
        digits.alpha = a
        digits.setShadowLayer(digits.textSize * 0.18f, 0f, 0f, CYAN)
        canvas.drawText(metro.bpm.toString(), vw / 2f, vh * 0.32f, digits)
        digits.clearShadowLayer()

        text.textSize = vh * 0.045f
        text.color = CYAN
        text.alpha = a
        text.clearShadowLayer()
        canvas.drawText("BPM   ·   ${metro.sig.label}", vw / 2f, vh * 0.38f, text)
    }

    private fun drawStatus(canvas: Canvas) {
        val badges = ArrayList<Pair<String, Int>>()
        if (!metro.running) badges.add("STOPPED" to WHITE)
        if (click.silent) badges.add("SILENT" to AMBER)
        if (metro.gapTrainer) badges.add("GAP" to GREEN)
        if (badges.isEmpty()) return
        text.textAlign = Paint.Align.LEFT
        text.textSize = vh * 0.04f
        text.clearShadowLayer()
        var x = vw * 0.04f
        for ((label, c) in badges) {
            text.color = c
            text.alpha = 220
            canvas.drawText(label, x, vh * 0.075f, text)
            x += text.measureText(label) + vw * 0.03f
        }
        text.textAlign = Paint.Align.CENTER
    }

    private fun drawHint(canvas: Canvas) {
        text.textAlign = Paint.Align.CENTER
        text.textSize = vh * 0.036f
        text.color = DIM
        text.alpha = 120
        text.clearShadowLayer()
        canvas.drawText(
            "TAP tempo   ·   ↑↓ ±5   ·   ←→ time-sig   ·   2×tap start/stop",
            vw / 2f, vh * 0.95f, text
        )
    }

    private fun glowDot(canvas: Canvas, x: Float, y: Float, r: Float, color: Int) {
        p.style = Paint.Style.FILL
        for (i in 4 downTo 1) {
            p.color = color
            p.alpha = 22 + (4 - i) * 8
            canvas.drawCircle(x, y, r * (1f + i * 0.5f), p)
        }
        p.color = color
        p.alpha = 255
        canvas.drawCircle(x, y, r, p)
        p.color = Color.WHITE
        p.alpha = 210
        canvas.drawCircle(x, y, r * 0.45f, p)
    }

    companion object {
        private val GREEN = 0xFF2BFF88.toInt()
        private val CYAN = 0xFF00E5FF.toInt()
        private val AMBER = 0xFFFFB300.toInt()
        private val DIM = 0xFF7A8699.toInt()
        private val WHITE = 0xFFFFFFFF.toInt()
    }
}
