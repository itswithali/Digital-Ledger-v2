package com.dl.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import java.util.concurrent.Executors
import kotlin.math.*

data class Opt(val id: String, val label: String, val desc: String)

val BOOT_STYLES = listOf(
    Opt("tv", "CRT power-on", "Classic TV switch-on"), Opt("iris", "Iris open", "Circle opening from the centre"),
    Opt("scan", "System scan", "Logo, scan line and progress bar"), Opt("glitch", "Glitch in", "Flickering, split-colour title"),
    Opt("fade", "Soft fade", "Simple, quiet fade-in"), Opt("none", "None", "Open straight to the app")
)
val BOOT_SOUNDS = listOf(
    Opt("crt", "CRT power-on", "Static burst, sweep and rising chime"), Opt("chime", "Soft chime", "Gentle rising arpeggio"),
    Opt("surge", "Power surge", "Deep hum swelling into a ping"), Opt("arcade", "8-bit jingle", "Retro arcade arpeggio"),
    Opt("bell", "Glass bell", "Bright struck-glass tones"), Opt("none", "Silent", "No startup sound")
)
val UI_PACKS = listOf(
    Opt("scifi", "Sci-fi", "Rising glassy blips (default)"), Opt("soft", "Soft", "Quiet, rounded taps"),
    Opt("click", "Mechanical", "Crisp keyboard-style clicks"), Opt("arcade", "Arcade", "Chiptune beeps"),
    Opt("glass", "Glass", "Light, bell-like pings"), Opt("static", "Static", "CRT crackle and interference"),
    Opt("off", "Off", "No interface sounds")
)

/** A port of the HTML's Web-Audio synthesis (oscillators, exponential envelopes, biquad filters, noise) to PCM. */
object Sfx {
    private const val SR = 22050
    private val ex = Executors.newSingleThreadExecutor()
    private val rnd = java.util.Random()
    private var last = 0L
    private val uiCache = HashMap<String, ShortArray>()

    private class Biq(val type: String) {
        var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        fun set(f: Double, q: Double) {
            val fr = f.coerceIn(10.0, SR / 2.0 - 100)
            val w0 = 2 * PI * fr / SR; val c = cos(w0); val s = sin(w0)
            val alpha = if (type == "bandpass") s / (2 * q) else s / (2 * 10.0.pow(q / 20.0))
            val a0 = 1 + alpha
            when (type) {
                "lowpass" -> { b0 = (1 - c) / 2; b1 = 1 - c; b2 = (1 - c) / 2 }
                "highpass" -> { b0 = (1 + c) / 2; b1 = -(1 + c); b2 = (1 + c) / 2 }
                else -> { b0 = alpha; b1 = 0.0; b2 = -alpha }
            }
            a1 = -2 * c / a0; a2 = (1 - alpha) / a0; b0 /= a0; b1 /= a0; b2 /= a0
        }
        fun run(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }

    private class Syn(sec: Double) {
        val b = FloatArray((sec * SR).toInt())

        fun tone(t: Double, f: Double, d: Double, v: Double = .3, a0: Double = .02, type: String = "sine", f2: Double = 0.0) {
            val a = min(a0, d * .5)
            val s0 = (t * SR).toInt(); val n = (d * SR).toInt()
            var ph = 0.0
            for (i in 0 until n) {
                val idx = s0 + i; if (idx >= b.size) break
                val tt = i.toDouble() / SR
                val fr = if (f2 > 0) f * (f2 / f).pow(tt / d) else f
                ph += 2 * PI * fr / SR
                val w = when (type) {
                    "triangle" -> 2 / PI * asin(sin(ph))
                    "square" -> if (sin(ph) >= 0) 1.0 else -1.0
                    "sawtooth" -> { val u = ph / (2 * PI); 2 * (u - floor(u + .5)) }
                    else -> sin(ph)
                }
                val g = if (tt < a) .0001 * (v / .0001).pow(tt / a) else v * (.0001 / v).pow((tt - a) / (d - a))
                b[idx] += (w * g).toFloat()
            }
        }

        fun noise(t: Double, d: Double, v: Double = .3, type: String = "highpass", f: Double = 1800.0, q: Double = -1.0) {
            val s0 = (t * SR).toInt(); val n = max(1, (d * SR).toInt())
            val bq = Biq(type); bq.set(f, if (q > 0) q else 1.0)
            for (i in 0 until n) {
                val idx = s0 + i; if (idx >= b.size) break
                val x = (Math.random() * 2 - 1) * (1 - i.toDouble() / n).pow(2)
                b[idx] += (bq.run(x) * v).toFloat()
            }
        }

        /** sawtooth through a sweeping low-pass, with a 3-point exponential gain envelope */
        fun saw(t: Double, f0: Double, f1: Double, dur: Double, lp0: Double, lp1: Double, peak: Double, tPeak: Double, tEnd: Double, stop: Double) {
            val s0 = (t * SR).toInt(); val n = (stop * SR).toInt()
            val bq = Biq("lowpass"); var ph = 0.0
            for (i in 0 until n) {
                val idx = s0 + i; if (idx >= b.size) break
                val tt = i.toDouble() / SR
                val k = (tt / dur).coerceIn(0.0, 1.0)
                if (i % 16 == 0) bq.set(lp0 * (lp1 / lp0).pow(k), 1.0)
                val fr = f0 * (f1 / f0).pow(k)
                ph += 2 * PI * fr / SR
                val u = ph / (2 * PI); val w = 2 * (u - floor(u + .5))
                val g = when {
                    tt < tPeak -> .0001 * (peak / .0001).pow(tt / tPeak)
                    tt < tEnd -> peak * (.0001 / peak).pow((tt - tPeak) / (tEnd - tPeak))
                    else -> .0001
                }
                b[idx] += (bq.run(w) * g).toFloat()
            }
        }

        fun highpass(f: Double) { val bq = Biq("highpass"); bq.set(f, 1.0); for (i in b.indices) b[i] = bq.run(b[i].toDouble()).toFloat() }

        fun pcm(master: Double): ShortArray {
            val out = ShortArray(b.size) { (b[it] * master).coerceIn(-1.0, 1.0).times(32767).toInt().toShort() }
            val fade = min(200, out.size)
            for (i in 0 until fade) out[out.size - 1 - i] = (out[out.size - 1 - i] * (i / fade.toFloat())).toInt().toShort()
            return out
        }
    }

    private fun bootSamples(id: String, vol: Double): ShortArray {
        val t = .05
        val s = Syn(3.2)
        when (id) {
            "crt" -> {
                s.noise(t + .22, .4, .35, "highpass", 1800.0)
                s.saw(t, 70.0, 600.0, 1.1, 200.0, 2600.0, .5, .8, 1.25, 1.3)
                listOf(659.25, 987.77, 1318.51, 1975.53).forEachIndexed { i, f ->
                    val st = t + 1.05 + i * .11
                    s.tone(st, f, .6, .45, .02); s.tone(st, f * 2, .25, .12, .01, "triangle")
                }
            }
            "chime" -> listOf(523.25, 659.25, 783.99, 1046.5, 1318.51).forEachIndexed { i, f ->
                val st = t + i * .13
                s.tone(st, f, .9, .4, .015); s.tone(st, f * 2, .35, .08, .01, "triangle")
            }
            "surge" -> {
                s.tone(t, 45.0, 1.1, .55, .5, "sine", 110.0)
                s.saw(t, 90.0, 1400.0, 1.0, 300.0, 3000.0, .3, .9, 1.15, 1.2)
                s.tone(t + .95, 1318.51, .8, .4, .01); s.tone(t + .95, 1975.53, .5, .15, .01)
            }
            "arcade" -> {
                listOf(392.0, 523.25, 659.25, 784.0, 1046.5, 1318.51).forEachIndexed { i, f -> s.tone(t + i * .075, f, .13, .16, .005, "square") }
                s.tone(t + .5, 1568.0, .4, .16, .005, "square")
            }
            "bell" -> listOf(880.0 to 0.0, 1318.51 to .12, 1760.0 to .24).forEach { n ->
                listOf(1.0 to .35, 2.76 to .12, 5.4 to .05).forEach { p -> s.tone(t + n.second, n.first * p.first, 1.6 / (1 + p.first * .4), p.second, .005) }
            }
            else -> return ShortArray(0)
        }
        return s.pcm(.22 * vol)
    }

    private fun uiSamples(pack: String, kind: String): ShortArray {
        val s = Syn(.6); val t = 0.0
        when (pack) {
            "scifi" -> {
                val p = when (kind) { "tab" -> doubleArrayOf(560.0, 1320.0, .1, .09); "ok" -> doubleArrayOf(760.0, 1900.0, .16, .1); else -> doubleArrayOf(1500.0, 2300.0, .05, .06) }
                s.tone(t, p[0], p[2] + .07, p[3], .008, "sine", p[1]); s.tone(t, p[0] * 1.5, p[2] + .07, p[3], .008, "triangle", p[1] * 1.5)
                s.highpass(380.0)
            }
            "soft" -> {
                val f = when (kind) { "tab" -> 392.0; "ok" -> 660.0; else -> 520.0 }
                s.tone(t, f, .09, .07, .008); if (kind == "ok") s.tone(t + .07, f * 1.5, .12, .07, .008)
            }
            "click" -> {
                val tab = kind == "tab"
                s.noise(t, .018, if (tab) .5 else .4, "bandpass", if (tab) 1800.0 else 3200.0, 1.2)
                s.tone(t, if (tab) 140.0 else 200.0, .04, .12, .003, "sine", 60.0)
                if (kind == "ok") s.noise(t + .07, .018, .4, "bandpass", 3800.0, 1.2)
            }
            "arcade" -> {
                if (kind == "ok") { s.tone(t, 784.0, .06, .06, .004, "square"); s.tone(t + .06, 1175.0, .1, .06, .004, "square") }
                else s.tone(t, if (kind == "tab") 660.0 else 880.0, if (kind == "tab") .08 else .05, .06, .004, "square", if (kind == "tab") 990.0 else 880.0)
            }
            "glass" -> {
                fun ping(f: Double, t0: Double) { s.tone(t0, f, .22, .09, .004); s.tone(t0, f * 2.76, .1, .03, .004) }
                if (kind == "ok") { ping(2093.0, t); ping(2637.0, t + .08) } else ping(if (kind == "tab") 1397.0 else 1760.0, t)
            }
            "static" -> {
                s.noise(t, if (kind == "tab") .09 else .05, .22, "highpass", if (kind == "tab") 2200.0 else 3000.0)
                if (kind == "ok") s.tone(t + .04, 1500.0, .1, .05, .006)
            }
            else -> return ShortArray(0)
        }
        return s.pcm(1.0)
    }

    private fun play(data: ShortArray, vol: Float) {
        if (data.isEmpty()) return
        try {
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(data.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
            t.write(data, 0, data.size); t.setVolume(vol.coerceIn(0f, 1f)); t.play()
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { t.release() } }, data.size * 1000L / SR + 600)
        } catch (_: Throwable) {
        }
    }

    private fun master() = Store.p.volume.coerceIn(0.0, 1.0)

    /** Startup sound. force=true ignores the on/off toggle (used by previews). */
    fun startup(force: Boolean = false, id: String? = null) {
        if (!force && !Store.p.sound) return
        val sid = id ?: Store.p.bootSound
        val vol = master()
        ex.execute { play(bootSamples(sid, vol), 1f) }
    }

    /** Interface feedback: kind = "tap" | "tab" | "ok" */
    fun ui(kind: String) {
        if (!Store.p.uiSound) return
        val now = System.currentTimeMillis(); if (now - last < 50) return; last = now
        val pack = Store.p.uiPack
        if (pack == "off") return
        val vol = master().toFloat()
        ex.execute { play(uiCache.getOrPut("$pack/$kind") { uiSamples(pack, kind) }, vol) }
    }

    fun previewUi(pack: String) {
        val vol = master().toFloat()
        ex.execute {
            play(uiSamples(pack, "tab"), vol)
            Thread.sleep(300)
            play(uiSamples(pack, "ok"), vol)
        }
    }

    /** Timer / focus-complete chime */
    fun done() {
        vibrate(longArrayOf(0, 200, 100, 200))
        if (!Store.p.sound) return
        ex.execute {
            val s = Syn(1.2)
            listOf(880.0, 1174.66, 1567.98).forEachIndexed { i, f -> s.tone(.02 + i * .16, f, .5, .25, .02) }
            play(s.pcm(1.0), 1f)
        }
    }

    fun vibrate(pattern: LongArray) {
        runCatching {
            val v = Store.app.getSystemService(Vibrator::class.java)
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }
}
