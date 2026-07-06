package com.tropicalstream.tapmetronome

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.tropicalstream.tapmetronome.input.TrackpadGestureEngine
import com.tropicalstream.tapmetronome.metro.Metronome
import com.tropicalstream.tapmetronome.metro.TapTempo
import com.tropicalstream.tapmetronome.render.MetronomeView
import com.tropicalstream.tapmetronome.sound.ClickEngine
import com.tropicalstream.tapmetronome.ui.BinocularSbsLayout

class MainActivity : Activity() {

    private val metro = Metronome()
    private val tapTempo = TapTempo()
    private val gestures = TrackpadGestureEngine()
    private val click = ClickEngine()
    private lateinit var view: MetronomeView

    private var running = false

    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply {
            densityDpi = DisplayMetrics.DENSITY_MEDIUM
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureImmersive()

        view = MetronomeView(this, metro, click)
        setContentView(BinocularSbsLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(view)
        })

        // Beat/subdivision → click (muted during the gap-trainer measure).
        metro.onBeat = { _, isDown, muted ->
            if (!muted) {
                if (isDown) click.downbeat() else click.beat()
            }
        }
        metro.onSub = { muted -> if (!muted) click.sub() }

        wireGestures()
    }

    private fun wireGestures() {
        gestures.onTap = {
            tapTempo.tap(SystemClock.uptimeMillis())?.let { metro.setBpmAligned(it) }
        }
        gestures.onDoubleTap = { metro.toggleRun() }
        gestures.onTripleTap = { view.cycleStyle() }
        gestures.onLongTap = { metro.toggleGapTrainer() }
        gestures.onSwipeVertical = { dir -> metro.adjustBpm(if (dir < 0) 5 else -5) } // up +5, down -5
        gestures.onSwipeHorizontal = { dir -> metro.cycleTimeSig(if (dir >= 0) 1 else -1) }
        gestures.onLeftTap = { click.silent = !click.silent }                          // Silent/Studio mode
    }

    private fun configureImmersive() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        window.decorView.setBackgroundColor(Color.BLACK)
    }

    // Runs every display frame (~60 Hz) for smooth motion + tight beat timing.
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(t: Long) {
            if (!running) return
            metro.update(System.nanoTime())
            view.invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onResume() {
        super.onResume()
        metro.resync(System.nanoTime())
        running = true
        Choreographer.getInstance().removeFrameCallback(frame)
        Choreographer.getInstance().postFrameCallback(frame)
    }

    override fun onPause() {
        super.onPause()
        running = false
    }

    override fun onDestroy() {
        super.onDestroy()
        gestures.release()
        click.release()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (gestures.onKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (gestures.onTouchEvent(ev)) return true
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (gestures.onGenericMotion(ev)) return true
        return super.dispatchGenericMotionEvent(ev)
    }
}
