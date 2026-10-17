package com.dl.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.max

/** Startup animations: timings and keyframes are taken from the HTML's CSS. */
object Boot {
    var style by mutableStateOf<String?>(null)
    var ms by mutableFloatStateOf(0f)

    fun play(s: String) { if (s != "none") { ms = 0f; style = s } }
}

private fun ez(t: Float, a: Float, b: Float, c: Float, d: Float) = CubicBezierEasing(a, b, c, d).transform(t.coerceIn(0f, 1f))
private fun seg(ms: Float, start: Float, dur: Float) = ((ms - start) / dur).coerceIn(0f, 1f)

/** CRT "switch-on" brightness flash applied to the whole app (brightness 2.6 → contrast 1.4 → saturate .4, easing out). */
fun Modifier.tvFlash(): Modifier {
    if (Boot.style != "tv" || Boot.ms < 1100f || Boot.ms > 2000f) return this
    val k = 1f - ez(seg(Boot.ms, 1100f, 900f), 0f, 0f, .58f, 1f)   // 1 → 0
    val b = 1f + 1.6f * k; val c = 1f + .4f * k; val s = 1f - .6f * k
    return this.drawWithContent {
        fun mul(a: Array<DoubleArray>, z: Array<DoubleArray>) = Array(5) { r -> DoubleArray(5) { col -> (0..4).sumOf { a[r][it] * z[it][col] } } }
        val B = arrayOf(doubleArrayOf(b.toDouble(), 0.0, 0.0, 0.0, 0.0), doubleArrayOf(0.0, b.toDouble(), 0.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, b.toDouble(), 0.0, 0.0), doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, 0.0, 0.0, 1.0))
        val off = (.5 - .5 * c) * 255
        val C = arrayOf(doubleArrayOf(c.toDouble(), 0.0, 0.0, 0.0, off), doubleArrayOf(0.0, c.toDouble(), 0.0, 0.0, off), doubleArrayOf(0.0, 0.0, c.toDouble(), 0.0, off), doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, 0.0, 0.0, 1.0))
        val sd = s.toDouble()
        val S = arrayOf(doubleArrayOf(.213 + .787 * sd, .715 - .715 * sd, .072 - .072 * sd, 0.0, 0.0), doubleArrayOf(.213 - .213 * sd, .715 + .285 * sd, .072 - .072 * sd, 0.0, 0.0), doubleArrayOf(.213 - .213 * sd, .715 - .715 * sd, .072 + .928 * sd, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, 0.0, 0.0, 1.0))
        val m = mul(S, mul(C, B))
        val cm = ColorMatrix(FloatArray(20) { i -> m[i / 5][i % 5].toFloat() })
        val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(cm) }
        drawIntoCanvas { cv ->
            cv.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
            drawContent()
            cv.restore()
        }
    }
}

@Composable
fun BootOverlay() {
    val style = Boot.style ?: return
    val total = when (style) { "tv" -> 2300f; "iris" -> 1800f; "scan" -> 2650f; "glitch" -> 2100f; else -> 1500f }
    LaunchedEffect(style) {
        val a = Animatable(0f)
        a.animateTo(total, tween(total.toInt(), easing = LinearEasing)) { Boot.ms = value }
        Boot.style = null; Boot.ms = 0f
    }
    val ms = Boot.ms
    val p = pal()
    Box(Modifier.fillMaxSize()) {
        when (style) {
            "tv" -> Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                val open = ez(seg(ms, 1100f, 750f), .75f, 0f, .25f, 1f)
                val topH = h * .504f
                val ty = -open * (topH * 1.01f)
                if (open < 1f) {
                    drawRect(Color.Black, Offset(0f, ty), Size(w, topH))
                    drawRect(Color.Black, Offset(0f, h * .496f - ty), Size(w, h * .504f))
                }
                // dot
                val dp0 = seg(ms, 80f, 300f)
                if (dp0 > 0f && dp0 < 1f) {
                    val o = if (dp0 < .4f) dp0 / .4f else 1f - (dp0 - .4f) / .6f
                    val sc = if (dp0 < .4f) .2f + 1.2f * (dp0 / .4f) else 1.4f - .4f * ((dp0 - .4f) / .6f)
                    drawCircle(Color.White.copy(alpha = o), 3.dp.toPx() * sc, Offset(w / 2, h / 2))
                }
                // line
                val lp = seg(ms, 250f, 850f)
                if (lp > 0f && ms < 1650f) {
                    val flick = when { lp < .65f -> 1f; lp < .75f -> .5f; lp < .85f -> 1f; else -> .75f }
                    val out = seg(ms, 1100f, 550f)
                    val oo = (1f - ez(out, .42f, 0f, 1f, 1f)) * flick
                    val sy = 1f + 8f * ez(out, .42f, 0f, 1f, 1f)
                    val lw = w * lp; val lh = 2.dp.toPx() * sy
                    if (oo > 0f) {
                        drawRect(Color.White.copy(alpha = .25f * oo), Offset((w - lw) / 2, h / 2 - lh * 2), Size(lw, lh * 4))
                        drawRect(Color.White.copy(alpha = oo), Offset((w - lw) / 2, h / 2 - lh / 2), Size(lw, lh))
                    }
                }
            }
            "iris" -> Canvas(Modifier.fillMaxSize()) {
                val irisT = ez(seg(ms, 300f, 1100f), .65f, 0f, .35f, 1f)
                val iris = irisT * 1.05f
                val fade = 1f - seg(ms, 1450f, 250f)
                val r = hypot(size.width, size.height) / 2f
                val c = Offset(size.width / 2, size.height / 2)
                if (fade > 0f) drawRect(
                    Brush.radialGradient(
                        0f to Color.Transparent, iris.coerceIn(0f, .999f) to Color.Transparent,
                        (iris + .015f).coerceIn(0f, 1f) to Color.Black.copy(alpha = fade), 1f to Color.Black.copy(alpha = fade),
                        center = c, radius = r
                    )
                )
            }
            "scan" -> {
                val fadeOut = 1f - seg(ms, 2050f, 500f)
                Box(Modifier.fillMaxSize().alpha(fadeOut).background(p.bg)) {
                    val pop = seg(ms, 0f, 500f)
                    Column(
                        Modifier.align(Alignment.Center).alpha(pop).graphicsLayer { val s = .94f + .06f * pop; scaleX = s; scaleY = s },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        HexMark(64.dp, 22f)
                        T("DIGITAL LEDGER", 22f, p.text, display = true, weight = 700, spacing = .34f, glow = true)
                        Box(Modifier.width(240.dp).height(3.dp).background(p.accent.copy(alpha = .18f))) {
                            val f = ez(seg(ms, 350f, 1500f), .5f, 0f, .2f, 1f)
                            Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(p.accent))
                        }
                        T("INITIALIZING SYSTEMS", 10f, p.faint, spacing = .3f)
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        val sp = seg(ms, 200f, 1700f)
                        if (sp > 0f && sp < 1f) {
                            val y = size.height * sp
                            drawRect(p.accent.copy(alpha = .18f), Offset(0f, y - 10.dp.toPx()), Size(size.width, 20.dp.toPx()))
                            drawRect(p.accent.copy(alpha = .9f), Offset(0f, y), Size(size.width, 2.dp.toPx()))
                        }
                    }
                }
            }
            "glitch" -> {
                val out = (1f - seg(ms, 1700f, 350f)).let { if (it > 0f) (Math.floor((it * 3f).toDouble()) / 3.0 + .34).toFloat().coerceAtMost(1f) else 0f }
                Box(Modifier.fillMaxSize().alpha(out).background(Color.Black), contentAlignment = Alignment.Center) {
                    val flick = when ((ms / 100f).toInt() % 7) { 0 -> .35f; 1 -> 1f; 2 -> .6f; 3 -> 1f; 4 -> .85f; else -> 1f }
                    val bandsA = listOf(0f to .2f, .55f to .75f, .3f to .5f, .75f to .95f, .1f to .3f, .45f to .6f)
                    val bandsB = listOf(.6f to .8f, .05f to .25f, .4f to .6f, .8f to 1f, .2f to .4f)
                    val ia = ((ms - 100f) / 220f).toInt().coerceAtLeast(0) % bandsA.size
                    val ib = (ms / 180f).toInt() % bandsB.size
                    Box(Modifier.alpha(flick)) {
                        GlitchText(Color.White, 0, null)
                        GlitchText(Color(0xFFFF2A6D), -3, bandsA[ia])
                        GlitchText(Color(0xFF05D9E8), 3, bandsB[ib])
                    }
                }
            }
            else -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - seg(ms, 350f, 1000f))))
        }
    }
}

@Composable
private fun GlitchText(c: Color, dx: Int, band: Pair<Float, Float>?) {
    val m = Modifier.offset { IntOffset(dx.dp.roundToPx(), 0) }.let { base ->
        if (band == null) base else base.drawWithContent {
            clipRect(0f, size.height * band.first, size.width, size.height * band.second) { this@drawWithContent.drawContent() }
        }
    }
    T("DIGITAL LEDGER", 26f, c, display = true, weight = 800, spacing = .2f, modifier = m)
}
