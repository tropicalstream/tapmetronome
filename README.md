# TapMetronome

A visual-first metronome for the RayNeo X3 Pro AR glasses. Built for musicians: a
smooth **anticipatory pendulum** you can feel the beat approaching from, kept tiny
and in the upper periphery so it never blocks your sheet music, frets, or bandmates.

Same self-contained stack as X3 Snake / X3 Timer: custom Canvas rendering inside a
dual-draw `BinocularSbsLayout`, synthesized clicks, **no external dependencies**.

## Controls (right temple pad)

| Gesture | Action |
|---|---|
| **Tap … tap … tap** | **Tap-tempo** — tap the beat 2–4× and the BPM matches your speed (and re-anchors so the next beat lands on your tap) |
| **Swipe ↑ / ↓** | BPM **+5 / −5** |
| **Swipe ← / →** | Cycle time signature (4/4 · 3/4 · 2/4 · 6/8 · 5/4) |
| **Double-tap** | Start / stop |
| **Triple-tap** | Cycle visual style (Pendulum → Arc → Border) |
| **Long-press** | Toggle **Gap-Method** trainer |
| **Left-pad tap** | Toggle **Silent / Studio mode** (kills audio, visual keeps time) |

## Features (the musician spec)

1. **Anticipatory motion** — the dot follows a cosine pendulum, so it *decelerates into* each beat and sits at an extreme exactly on the beat. Your eye reads the acceleration and predicts the hit, instead of reacting to a sudden flash (which makes you drag).
2. **Micro-glanceability & transparency** — the tracker lives in the upper periphery and is low-opacity. Three layout styles via triple-tap:
   - **Pendulum** — a dot swings across a thin top bar.
   - **Arc** — the dot sweeps a shallow arc (conductor's baton).
   - **Border** — *only* the screen-edge frame pulses; maximum passthrough for reading music.
3. **Downbeat & subdivision coding** — beat 1 is a **big green** flash + lit downbeat dot; beats 2-3-4 are smaller **cyan** pulses; compound meters (6/8) add faint subdivision ticks. A row of dots shows where you are in the measure.
4. **Tap-tempo** — a burst of taps sets BPM from the average interval, clamped 30–300.
5. **Zero-Bleed / Studio mode** — a silent toggle kills the audio engine entirely so clicks can't bleed into live mics; the visual pulse alone guides tracking.
6. **Gap-Method trainer** — plays 3 measures, then **blanks and mutes the 4th** ("— keep time —") so you hold tempo on your own, reappearing on the next downbeat. Trains your internal clock.

## Build & install

```bash
cd /Users/me/Projects/tapmetronome
./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.tropicalstream.tapmetronome.debug/com.tropicalstream.tapmetronome.MainActivity
```

## Notes

- Beat timing runs off `System.nanoTime()` and the display frame callback (~60 Hz), so visual jitter is ≤ ~16 ms. For live tracking, Studio (silent) mode leans on the visual, which is the tightest signal.
- Architecture: `metro/Metronome.kt` (phase-accumulator clock, beats/subdivisions, gap trainer), `metro/TapTempo.kt`, `sound/ClickEngine.kt` (synthesized), `render/MetronomeView.kt` (3 styles), `MainActivity.kt`, plus the reused `ui/BinocularSbsLayout.kt` and `input/TrackpadGestureEngine.kt`.
