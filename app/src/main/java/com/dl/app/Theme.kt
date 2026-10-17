package com.dl.app

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow

class Pal(
    val bg: Color, val text: Color, val dim: Color, val faint: Color, val accent: Color, val accent2: Color, val ink: Color,
    val panel: Color, val panel2: Color, val border: Color, val grid: Color, val warn: Color, val warnBg: Color,
    val good: Color, val goodBg: Color, val tags: List<Color>, val radius: Dp, val pad: Dp, val lite: Boolean, val scan: Float
)

val LocalPal = staticCompositionLocalOf<Pal> { error("no palette") }

fun rgbOf(h: String): IntArray { val x = h.removePrefix("#"); return intArrayOf(x.substring(0, 2).toInt(16), x.substring(2, 4).toInt(16), x.substring(4, 6).toInt(16)) }
fun colorOf(h: String): Color { val c = rgbOf(h); return Color(c[0], c[1], c[2]) }
fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r / 255f, g / 255f, b / 255f, a)
fun hexOf(r: DoubleArray): String = "#" + r.joinToString("") { v -> val i = Math.max(0, Math.min(255, Math.round(v).toInt())); (if (i < 16) "0" else "") + Integer.toHexString(i) }.uppercase()
fun mixHex(a: String, b: String, t: Double): String { val x = rgbOf(a); val y = rgbOf(b); return hexOf(doubleArrayOf(x[0] + (y[0] - x[0]) * t, x[1] + (y[1] - x[1]) * t, x[2] + (y[2] - x[2]) * t)) }
fun lumOf(h: String): Double { val c = rgbOf(h).map { var v = it / 255.0; if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }; return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2] }
fun contrastOf(a: String, b: String): Double { val x = lumOf(a); val y = lumOf(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05) }
fun normHex(h0: String): String? {
    var h = h0.trim(); if (h.isNotEmpty() && h[0] != '#') h = "#$h"
    if (Regex("^#[0-9a-fA-F]{3}$").matches(h)) h = "#" + h.substring(1).map { "$it$it" }.joinToString("")
    return if (Regex("^#[0-9a-fA-F]{6}$").matches(h)) h.uppercase() else null
}
fun hexOfColor(c: Color): String = "#%02X%02X%02X".format((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

/** Mirrors the HTML's CSS variables: the built-in "midnight" theme, or colours derived from the 3 custom picks. */
fun makePal(p: Prefs, phone: Boolean): Pal {
    val tag = listOf("#38BDF8", "#22D3EE", "#A78BFA", "#F472B6", "#34D399", "#FBBF24").map { colorOf(it) }
    val lite = when (p.perf) { "on" -> true; "off" -> false; else -> phone }
    val tc = p.themeColors
    val radius = p.radius.dp
    val pad = p.cardPad.dp
    val warn = colorOf("#FF5470"); val good = colorOf("#34D399")
    val warnBg = rgba(255, 84, 112, .12f); val goodBg = rgba(52, 211, 153, .12f)
    if (tc == null) {
        return Pal(colorOf("#0B1220"), colorOf("#E6EEF8"), colorOf("#93A8C4"), colorOf("#667E9E"), colorOf("#38BDF8"), colorOf("#7DD3FC"),
            colorOf("#050505"), rgba(16, 26, 44, .94f), rgba(16, 26, 44, .86f), rgba(96, 165, 250, .28f), rgba(96, 165, 250, .06f),
            warn, warnBg, good, goodBg, tag, radius, pad, lite, .04f)
    }
    val text = normHex(tc.text) ?: "#E6EEF8"; val bg = normHex(tc.bg) ?: "#0B1220"; val acc = normHex(tc.accent) ?: "#38BDF8"
    val ar = rgbOf(acc)
    val tr = rgbOf(mixHex(text, acc, .35))
    val pn = rgbOf(mixHex(bg, text, .07)); val pn2 = rgbOf(mixHex(bg, text, .045))
    return Pal(colorOf(bg), colorOf(text), colorOf(mixHex(text, bg, .38)), colorOf(mixHex(text, bg, .62)), colorOf(acc),
        colorOf(mixHex(acc, "#FFFFFF", .35)), if (lumOf(acc) > 0.35) colorOf("#050505") else Color.White,
        rgba(pn[0], pn[1], pn[2], .94f), rgba(pn2[0], pn2[1], pn2[2], .86f), rgba(tr[0], tr[1], tr[2], .30f), rgba(ar[0], ar[1], ar[2], .07f),
        warn, warnBg, good, goodBg, tag, radius, pad, lite, .04f)
}

/** Orbitron (display) + Share Tech Mono (body), loaded from assets/fonts. Falls back gracefully if missing. */
object Fonts {
    var display: (FontWeight) -> FontFamily = { FontFamily.SansSerif }
    var mono: FontFamily = FontFamily.Monospace
    private val cache = HashMap<Int, FontFamily>()

    fun init(ctx: Context) {
        val am = ctx.assets
        val have = am.list("fonts")?.toSet() ?: emptySet()
        if ("ShareTechMono.ttf" in have) mono = runCatching { FontFamily(Font("fonts/ShareTechMono.ttf", am)) }.getOrDefault(FontFamily.Monospace)
        if ("Orbitron.ttf" in have) {
            display = { w ->
                cache.getOrPut(w.weight) {
                    runCatching {
                        FontFamily(Font("fonts/Orbitron.ttf", am, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))))
                    }.getOrDefault(FontFamily.SansSerif)
                }
            }
        }
    }
}
