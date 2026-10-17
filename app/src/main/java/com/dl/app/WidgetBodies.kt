@file:OptIn(ExperimentalLayoutApi::class)

package com.dl.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.MathContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

fun fmtNum(n: Double): String {
    if (n == 0.0 || !n.isFinite()) return if (n.isFinite()) "0" else n.toString()
    val bd = BigDecimal(n).round(MathContext(12)).stripTrailingZeros()
    val a = abs(n)
    return if (a >= 1e21 || a < 1e-6) bd.toString().replace("E+", "e+").replace("E-", "e-") else bd.toPlainString()
}
fun fmtHMS(sec0: Long): String { val sec = max(0, sec0); val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60; return (if (h > 0) "$h:%02d".format(m) else "%02d".format(m)) + ":%02d".format(s) }

/** Calculator-widget engine (no eval): + − × ÷ ^ % and parentheses. */
fun wCalcEval(src: String): Double {
    val s = src.replace("×", "*").replace("÷", "/").replace("−", "-").replace(Regex("\\s+"), "")
    var i = 0
    fun pk() = if (i < s.length) s[i] else '\u0000'
    lateinit var expr: () -> Double
    fun num(): Double { val m = Regex("^(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?").find(s.substring(i)) ?: throw Exception("n"); i += m.value.length; return m.value.toDouble() }
    fun prim(): Double { if (pk() == '(') { i++; val v = expr(); if (pk() != ')') throw Exception(")"); i++; return v }; return num() }
    fun post(): Double { var v = prim(); while (pk() == '%') { i++; v /= 100 }; return v }
    fun un(): Double { if (pk() == '-') { i++; return -un() }; if (pk() == '+') { i++; return un() }; return post() }
    fun pw(): Double { val b = un(); if (pk() == '^') { i++; return b.pow(pw()) }; return b }
    fun term(): Double { var v = pw(); while (true) { val c = pk(); if (c == '*') { i++; v *= pw() } else if (c == '/') { i++; v /= pw() } else return v } }
    expr = { var v = term(); while (true) { val c = pk(); if (c == '+') { i++; v += term() } else if (c == '-') { i++; v -= term() } else break }; v }
    val out = expr()
    if (i < s.length || !out.isFinite()) throw Exception("x")
    return out
}

@Composable
fun ColumnScope.WidgetBody(tab: CustomTab, w: Widget, editing: Boolean, configure: () -> Unit) {
    when (w.type) {
        "calculator" -> WCalc()
        "picture" -> WPicture(w, false, editing)
        "frame" -> WPicture(w, true, editing)
        "clock" -> WClock(w)
        "timer" -> WTimer(w)
        "stopwatch" -> WStopwatch(w)
        "notes" -> Field(w.str("text"), { w.data["text"] = it; Store.touch() }, "Write here…", Modifier.weight(1f), lines = 3, size = 14f, fill = true, dense = true)
        "todo" -> WTodo(w)
        "counter" -> WCounter(w)
        "scoreboard" -> WScore(w)
        "countdown" -> WCountdown(w, configure)
        "minical" -> WMiniCal()
        "goal" -> WGoal(w)
        "habit" -> WHabit(w)
        "links" -> WLinks(w)
        "converter" -> WConverter()
        "text" -> WText(w)
        "youtube" -> WYoutube(w)
        "video" -> WVideo(w, editing)
        else -> T("Unknown widget", 12f, pal().faint)
    }
}

// ───────── calculator ─────────
@Composable
private fun ColumnScope.WCalc() {
    val p = pal()
    var exp by remember { mutableStateOf("") }; var fresh by remember { mutableStateOf(false) }
    fun press(k: String) {
        if (exp == "Error") exp = ""
        when (k) {
            "C" -> exp = ""
            "⌫" -> exp = exp.dropLast(1)
            "=" -> try { if (exp.isNotEmpty()) exp = fmtNum(wCalcEval(exp)); fresh = true } catch (e: Exception) { exp = "Error" }
            "±" -> exp = if (exp.startsWith("-")) exp.drop(1) else if (exp.isNotEmpty()) "-$exp" else ""
            else -> { if (fresh && k.matches(Regex("[0-9.]"))) exp = ""; fresh = false; exp += k }
        }
    }
    Field(exp, { exp = it; fresh = false }, "0", Modifier.fillMaxWidth(), size = 20f, display = true, dense = true, textAlign = TextAlign.End, kb = KeyboardType.Text, onDone = { press("=") })
    val keys = listOf("C", "⌫", "%", "÷", "7", "8", "9", "×", "4", "5", "6", "−", "1", "2", "3", "+", "±", "0", ".", "=")
    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        keys.chunked(4).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { k ->
                    val primary = k == "="; val op = k in listOf("÷", "×", "−", "+", "%")
                    val sh = RoundedCornerShape(4.dp)
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(sh).background(if (primary) p.accent else p.panel2).border(1.dp, if (primary) p.accent else p.border, sh)
                            .clickable { Sfx.ui("tap"); press(k) }, contentAlignment = Alignment.Center
                    ) { T(k, 14f, if (primary) p.ink else if (op) p.accent else p.text, bold = true) }
                }
            }
        }
    }
}

// ───────── picture / frame ─────────
@Composable
private fun ColumnScope.WPicture(w: Widget, framed: Boolean, editing: Boolean) {
    val p = pal(); val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { if (Media.saveImage(ctx, uri, w.id)) toast("Picture saved") else toast("That file couldn’t be used as a picture.") }
    }
    val bmp by produceState<ImageBitmap?>(null, w.id, Media.rev) {
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(Media.file(w.id).path)?.asImageBitmap() }.getOrNull() }
    }
    val fit = if (w.str("fit", "cover") == "contain") ContentScale.Fit else ContentScale.Crop
    val cap = w.str("caption")
    val b = bmp
    Box(Modifier.weight(1f).fillMaxWidth()) {
        if (b == null) {
            val sh = RoundedCornerShape(4.dp)
            Column(Modifier.align(Alignment.Center).clip(sh).dashed(p.border, 4.dp).clickable { Sfx.ui("tap"); picker.launch("image/*") }.padding(horizontal = 18.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Ico("image", 28.dp, p.text); T("ADD A PICTURE", 11.5f, p.text, bold = true, spacing = .06f)
            }
        } else if (!framed) {
            Column(Modifier.fillMaxSize()) {
                Image(b, null, Modifier.weight(1f).fillMaxWidth(), contentScale = fit)
                if (cap.isNotEmpty()) T(cap, 11f, p.dim, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 8.dp))
            }
        } else {
            val style = w.str("style", "wood")
            val (border, brush, mat, matPad) = when (style) {
                "gold" -> FrameSpec(9, Brush.linearGradient(listOf(Color(0xFFF6E27A), Color(0xFFB8860B), Color(0xFFF6E27A), Color(0xFF8A6508))), Color(0xFFF5EFDC), 5)
                "black" -> FrameSpec(8, SolidColor(Color(0xFF0C0C0C)), Color(0xFFF4F4F2), 12)
                "white" -> FrameSpec(7, SolidColor(Color(0xFFF2F2F2)), Color.White, 10)
                "polaroid" -> FrameSpec(0, SolidColor(Color.Transparent), Color(0xFFFAFAFA), 9)
                "neon" -> FrameSpec(3, SolidColor(p.accent), Color.Transparent, 4)
                else -> FrameSpec(10, Brush.linearGradient(listOf(Color(0xFF9B6A35), Color(0xFF5E3A17), Color(0xFF9B6A35))), Color(0xFFEFE6D2), 6)
            }
            val pol = style == "polaroid"
            Column(
                Modifier.fillMaxSize().padding(8.dp).then(if (pol) Modifier.rotate(-1.2f) else Modifier)
                    .then(if (style == "neon" && !p.lite) Modifier.glowBox(p.accent.copy(alpha = .6f), 6.dp, 12.dp) else Modifier)
                    .then(if (pol) Modifier.background(Color(0xFFFAFAFA)).padding(start = 9.dp, end = 9.dp, top = 9.dp, bottom = 6.dp) else Modifier.border(BorderStroke(border.dp, brush), if (style == "neon") RoundedCornerShape(6.dp) else RoundedCornerShape(0.dp)))
            ) {
                Box(Modifier.weight(1f).fillMaxWidth().background(mat).padding(if (pol) 0.dp else matPad.dp)) {
                    Image(b, null, Modifier.fillMaxSize(), contentScale = fit)
                }
                if (cap.isNotEmpty()) T(cap, if (pol) 13f else 11f, if (pol) Color(0xFF333333) else p.dim, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = if (pol) 8.dp else 4.dp, start = 6.dp, end = 6.dp, bottom = if (pol) 4.dp else 0.dp))
            }
        }
        if (editing && b != null) Row(Modifier.align(Alignment.BottomStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Btn("Change", small = true) { picker.launch("image/*") }
            Btn("Remove", small = true) { Media.delete(w.id) }
        }
    }
}
private data class FrameSpec(val border: Int, val brush: Brush, val mat: Color, val matPad: Int)

// ───────── clock ─────────
@Composable
private fun ColumnScope.WClock(w: Widget) {
    val p = pal()
    val tz = w.str("tz"); val analog = w.str("style", "digital") == "analog"; val h12 = w.bool("h12"); val secs = w.bool("secs", true)
    val label = w.str("label").ifEmpty { TZ_OPTS.firstOrNull { it.first == tz }?.second ?: "Local time" }
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(500) } }
    val zone = runCatching { if (tz.isEmpty()) ZoneId.systemDefault() else ZoneId.of(tz) }.getOrDefault(ZoneId.systemDefault())
    val t = Instant.ofEpochMilli(now).atZone(zone)
    Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (analog) {
            Canvas(Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true)) {
                val k = size.width / 100f; val c = Offset(50f * k, 50f * k)
                drawCircle(p.border, 46f * k, c, style = Stroke(2f * k))
                for (i in 0 until 12) {
                    val a = Math.toRadians(i * 30.0 - 90.0); val len = if (i % 3 == 0) 14f else 11f
                    drawLine(p.dim, Offset((50 + 42 * cos(a)).toFloat() * k, (50 + 42 * sin(a)).toFloat() * k), Offset((50 + (50 - len) * cos(a)).toFloat() * k, (50 + (50 - len) * sin(a)).toFloat() * k), 1.6f * k)
                }
                fun hand(deg: Double, from: Float, to: Float, sw: Float, col: Color) {
                    val a = Math.toRadians(deg - 90.0)
                    drawLine(col, Offset(c.x + (from * cos(a)).toFloat() * k, c.y + (from * sin(a)).toFloat() * k), Offset(c.x + (to * cos(a)).toFloat() * k, c.y + (to * sin(a)).toFloat() * k), sw * k, StrokeCap.Round)
                }
                hand((t.hour % 12) * 30.0 + t.minute * .5, 0f, 22f, 3.2f, p.text)
                hand(t.minute * 6.0 + t.second * .1, 0f, 32f, 2.4f, p.text)
                if (secs) hand(t.second * 6.0, -6f, 36f, 1.2f, p.accent)
                drawCircle(p.accent, 2.4f * k, c)
            }
        } else {
            var hh = t.hour; var ap = ""
            if (h12) { ap = if (hh >= 12) " PM" else " AM"; hh = if (hh % 12 == 0) 12 else hh % 12 }
            T("%02d:%02d".format(hh, t.minute) + (if (secs) ":%02d".format(t.second) else "") + ap, 22f, p.accent, display = true, glow = true)
        }
        T(t.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())), 11f, p.dim, spacing = .05f)
        T(label.uppercase(), 9.5f, p.faint, spacing = .14f)
    }
}

// ───────── timer ─────────
@Composable
private fun ColumnScope.WTimer(w: Widget) {
    val p = pal(); val ctx = LocalContext.current
    if (w.data["total"] == null) { w.data["total"] = 300.0; w.data["remaining"] = 300.0; w.data["running"] = false }
    val total = w.num("total", 300.0).toLong(); val running = w.bool("running"); val endAt = w.num("endAt").toLong()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) { while (running) { now = System.currentTimeMillis(); delay(250) } }
    val remaining = if (running) max(0, ceil((endAt - now) / 1000.0).toLong()) else w.num("remaining", total.toDouble()).toLong()
    var custom by remember { mutableStateOf("") }
    fun set(secs: Long) { w.data["total"] = secs.toDouble(); w.data["remaining"] = secs.toDouble(); Store.touch() }
    Box(Modifier.weight(1f).fillMaxWidth().defaultMinSize(minHeight = 60.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
            val k = size.width / 100f; val sw = 6f * k; val inset = 6f * k + sw / 2
            val tl = Offset(inset, inset); val sz = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(p.border, 0f, 360f, false, tl, sz, style = Stroke(sw))
            val frac = if (total > 0) remaining.toFloat() / total else 0f
            drawArc(p.accent, -90f, 360f * frac, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        T(fmtHMS(remaining), 16f, p.text, display = true)
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(1, 5, 10, 15, 25, 45).forEach { m -> ChipB("${m}m", m * 60L == total) { if (!running) set(m * 60L) } }
        Box(Modifier.width(64.dp)) {
            Field(custom, { v -> custom = v.filter { c -> c.isDigit() }.take(3); val n = custom.toIntOrNull(); if (!running && n != null && n in 1..600) { set(n * 60L) } }, "min", kb = KeyboardType.Number, size = 13f, dense = true)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Btn(if (running) "Pause" else if (remaining < total) "Resume" else "Start", Modifier.weight(1f), primary = true, small = true) {
            if (running) {
                w.data["remaining"] = max(1, ceil((endAt - System.currentTimeMillis()) / 1000.0)).toDouble(); w.data["running"] = false
                Notifier.cancelTimer(ctx, w.id)
            } else {
                var rem = w.num("remaining", total.toDouble()); if (rem <= 0) rem = total.toDouble(); w.data["remaining"] = rem
                val end = System.currentTimeMillis() + (rem * 1000).toLong(); w.data["endAt"] = end.toDouble(); w.data["running"] = true
                Notifier.scheduleTimer(ctx, w.id, end, (w.title.ifEmpty { "Timer" }) + " finished", "Your timer has ended.")
            }
            Store.touch()
        }
        Btn("Reset", Modifier.weight(1f), small = true) { w.data["running"] = false; w.data["remaining"] = total.toDouble(); Notifier.cancelTimer(ctx, w.id); Store.touch() }
    }
}

// ───────── stopwatch ─────────
@Composable
private fun ColumnScope.WStopwatch(w: Widget) {
    val p = pal()
    if (w.data["elapsed"] == null) { w.data["elapsed"] = 0.0; w.data["running"] = false }
    val running = w.bool("running")
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) { while (running) { now = System.currentTimeMillis(); delay(100) } }
    val elapsed = w.num("elapsed").toLong()
    val cur = if (running) elapsed + (now - w.num("startAt").toLong()) else elapsed
    fun fmt(ms: Long): String { val t = ms / 100; return "%02d:%02d.%d".format(t / 600, t / 10 % 60, t % 10) }
    val laps = w.list("laps").map { (it as? Double)?.toLong() ?: 0L }
    T(fmt(cur), 22f, p.accent, display = true, glow = true, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Btn(if (running) "Stop" else if (elapsed > 0) "Resume" else "Start", Modifier.weight(1f), primary = true, small = true) {
            if (running) { w.data["elapsed"] = cur.toDouble(); w.data["running"] = false } else { w.data["startAt"] = System.currentTimeMillis().toDouble(); w.data["running"] = true }
            Store.touch()
        }
        Btn("Lap", Modifier.weight(1f), small = true) { if (running) { val l = w.list("laps"); l.add(0, cur.toDouble()); while (l.size > 30) l.removeAt(l.size - 1); Store.touch() } }
        Btn("Reset", Modifier.weight(1f), small = true) { w.data["running"] = false; w.data["elapsed"] = 0.0; w.data["laps"] = mutableListOf<Any?>(); Store.touch() }
    }
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
        laps.forEachIndexed { i, l ->
            val prev = laps.getOrElse(i + 1) { 0L }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 2.dp).dashedBottom(p), horizontalArrangement = Arrangement.SpaceBetween) {
                T("Lap ${laps.size - i}", 11f, p.dim); T(fmt(l), 11f, p.text, bold = true); T("+" + fmt(l - prev), 11f, p.accent2)
            }
        }
    }
}

fun Modifier.dashedBottom(p: Pal): Modifier = this.drawBehind {
    drawLine(p.border, Offset(0f, size.height), Offset(size.width, size.height), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
}

// ───────── to-do ─────────
@Composable
@Suppress("UNCHECKED_CAST")
private fun ColumnScope.WTodo(w: Widget) {
    val p = pal()
    LaunchedEffect(w.id) { WidgetTicker.todoReset(w) }
    val items = w.list("items") as MutableList<MutableMap<String, Any?>>
    var text by remember { mutableStateOf("") }
    val n = items.count { it["done"] == true }
    Column {
        T("$n / ${items.size} done", 10.5f, p.dim)
        Box(Modifier.padding(top = 4.dp).fillMaxWidth(if (items.isEmpty()) 0f else n.toFloat() / items.size).height(3.dp).clip(RoundedCornerShape(2.dp)).background(p.accent))
    }
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { it ->
            Row(Modifier.fillMaxWidth().dashedBottom(p).padding(vertical = 4.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Check(it["done"] == true) { it["done"] = it["done"] != true; Store.touch() }
                T(it["text"]?.toString() ?: "", 12.5f, if (it["done"] == true) p.faint else p.text, strike = it["done"] == true, modifier = Modifier.weight(1f))
                T("✕", 11f, p.faint, modifier = Modifier.clickable { items.remove(it); Store.touch() }.padding(4.dp))
            }
        }
        if (items.isEmpty()) T("No tasks yet.", 11f, p.faint)
    }
    Field(text, { text = it.take(80) }, "Add a task and press Enter", size = 14f, dense = true, onDone = {
        if (text.isNotBlank()) { items.add(mutableMapOf("id" to uid(), "text" to text.trim(), "done" to false)); text = ""; Store.touch() }
    })
}

// ───────── counter / scoreboard ─────────
@Composable
private fun ColumnScope.WCounter(w: Widget) {
    val p = pal()
    if (w.data["n"] == null) w.data["n"] = 0.0
    val step = w.num("step", 1.0).let { if (it == 0.0) 1.0 else it }
    val n = w.num("n")
    fun set(v: Double) { w.data["n"] = Math.round(v * 1000) / 1000.0; Store.touch() }
    T(w.str("label", "Reps").uppercase(), 10.5f, p.dim, spacing = .12f)
    T(Calc.js(n), 32f, p.accent, display = true, glow = true)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Btn("−", Modifier.weight(1f), small = true) { set(n - step) }
        Btn("+", Modifier.weight(1f), small = true, primary = true) { set(n + step) }
        Btn("0", Modifier.weight(1f), small = true) { set(0.0) }
    }
}

@Composable
private fun ColumnScope.WScore(w: Widget) {
    val p = pal()
    if (w.data["sa"] == null) { w.data["sa"] = 0.0; w.data["sb"] = 0.0 }
    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        listOf("sa" to w.str("a", "Home"), "sb" to w.str("b", "Away")).forEachIndexed { idx, (key, name) ->
            if (idx == 1) T("VS", 10f, p.faint, spacing = .2f)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T(name.uppercase(), 11f, p.dim, spacing = .1f, maxLines = 1)
                T(w.num(key).toInt().toString(), 34f, p.accent, display = true, glow = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(-1 to "−1", 1 to "+1", 2 to "+2", 3 to "+3").forEach { (v, lbl) ->
                        Btn(lbl, small = true, primary = v == 1) { w.data[key] = max(0.0, w.num(key) + v); Store.touch() }
                    }
                }
            }
        }
    }
    Btn("New game", Modifier.fillMaxWidth(), small = true) { w.data["sa"] = 0.0; w.data["sb"] = 0.0; Store.touch() }
}

// ───────── countdown ─────────
@Composable
private fun ColumnScope.WCountdown(w: Widget, configure: () -> Unit) {
    val p = pal()
    val date = w.str("date")
    if (date.isEmpty()) { T("Pick a date to start counting down.", 11f, p.faint); Btn("Set date", small = true) { configure() }; return }
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(500) } }
    val tgt = runCatching { LocalDate.parse(date).atTime(runCatching { LocalTime.parse(w.str("time")) }.getOrDefault(LocalTime.MIDNIGHT)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrDefault(0L)
    val ms = tgt - now
    T(w.str("label", "Event").uppercase(), 10.5f, p.dim, spacing = .12f)
    if (ms <= 0) {
        T(if (ms > -86400000 && w.str("time").isEmpty()) "Today" else "Passed", 24f, p.accent, display = true, glow = true)
        T("${floor(-ms / 86400000.0).toLong()} day(s) ago", 12f, p.dim)
    } else {
        val sec = ms / 1000; val dd = sec / 86400
        T("$dd " + (if (dd == 1L) "day" else "days"), 24f, p.accent, display = true, glow = true)
        T("%02d:%02d:%02d".format(sec % 86400 / 3600, sec % 3600 / 60, sec % 60), 12f, p.dim)
    }
}

// ───────── mini calendar ─────────
@Composable
private fun ColumnScope.WMiniCal() {
    val p = pal(); val d = Store.read()
    var off by remember { mutableIntStateOf(0) }
    val cur = LocalDate.now().withDayOfMonth(1).plusMonths(off.toLong())
    val st = cur.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val tk = LocalDate.now()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        T("‹", 16f, p.text, modifier = Modifier.clickable { Sfx.ui("tap"); off-- }.padding(horizontal = 10.dp, vertical = 2.dp))
        T(cur.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())), 12f, p.text, bold = true, align = TextAlign.Center, modifier = Modifier.weight(1f))
        T("›", 16f, p.text, modifier = Modifier.clickable { Sfx.ui("tap"); off++ }.padding(horizontal = 10.dp, vertical = 2.dp))
    }
    Row(Modifier.fillMaxWidth()) { DAY_NAMES.forEach { T(it.take(1), 9.5f, p.faint, align = TextAlign.Center, modifier = Modifier.weight(1f)) } }
    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (r in 0 until 6) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (c in 0 until 7) {
                val x = st.plusDays((r * 7 + c).toLong()); val other = x.month != cur.month; val today = x == tk
                val has = eventsFor(d, dateKey(x)).isNotEmpty()
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(if (today) p.accent else Color.Transparent).graphicsLayer { alpha = if (other) .55f else 1f }, contentAlignment = Alignment.Center) {
                    T("${x.dayOfMonth}", 11f, if (today) p.ink else if (other) p.faint else p.text, bold = today)
                    if (has) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).size(4.dp).clip(androidx.compose.foundation.shape.CircleShape).background(p.tags[5]))
                }
            }
        }
    }
}

// ───────── goal / habit ─────────
@Composable
private fun ColumnScope.WGoal(w: Widget) {
    val p = pal()
    if (w.data["v"] == null) w.data["v"] = 0.0
    val tgt = w.num("target", 100.0).let { if (it == 0.0) 100.0 else it }; val step = w.num("step", 5.0).let { if (it == 0.0) 1.0 else it }; val unit = w.str("unit")
    val v = w.num("v"); val pct = max(0.0, min(100.0, v / tgt * 100))
    T(w.str("label", "Goal").uppercase(), 10.5f, p.dim, spacing = .12f)
    val sh = RoundedCornerShape(6.dp)
    Box(Modifier.fillMaxWidth().height(10.dp).clip(sh).background(p.panel2).border(1.dp, p.border, sh)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth((pct / 100).toFloat()).background(p.accent))
    }
    Row { T(fmtNum(v), 12f, p.text, bold = true); T(" / ${fmtNum(tgt)} $unit · ${pct.roundToInt()}%", 12f, p.dim) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Btn("−", Modifier.weight(1f), small = true) { w.data["v"] = max(0.0, v - step); Store.touch() }
        Btn("+" + fmtNum(step), Modifier.weight(1f), small = true, primary = true) { w.data["v"] = v + step; Store.touch() }
        Btn("Reset", Modifier.weight(1f), small = true) { w.data["v"] = 0.0; Store.touch() }
    }
}

@Composable
private fun ColumnScope.WHabit(w: Widget) {
    val p = pal()
    val days = w.map("days")
    var streak = 0; var x = LocalDate.now(); if (days[x.toString()] == null) x = x.minusDays(1)
    while (days[x.toString()] != null) { streak++; x = x.minusDays(1) }
    T((w.str("label", "Habit") + " · streak $streak").uppercase(), 10.5f, p.dim, spacing = .12f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        for (i in 6 downTo 0) {
            val dx = LocalDate.now().minusDays(i.toLong()); val k = dx.toString(); val on = days[k] != null; val sh = RoundedCornerShape(6.dp)
            Column(
                Modifier.width(38.dp).clip(sh).background(if (on) p.accent else p.panel2).border(1.dp, if (on || i == 0) p.accent else p.border, sh)
                    .clickable { Sfx.ui("tap"); if (on) days.remove(k) else days[k] = 1.0; Store.touch() }.padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                T(DAY_NAMES[dx.dayOfWeek.value - 1].take(1), 9f, if (on) p.ink else p.dim, spacing = .06f)
                Box(Modifier.height(14.dp), contentAlignment = Alignment.Center) { if (on) T("✓", 12f, p.ink) }
            }
        }
    }
}

// ───────── quick links ─────────
@Composable
@Suppress("UNCHECKED_CAST")
private fun ColumnScope.WLinks(w: Widget) {
    val p = pal(); val ctx = LocalContext.current
    val links = w.list("links") as MutableList<MutableMap<String, Any?>>
    var t by remember { mutableStateOf("") }; var u by remember { mutableStateOf("") }
    fun add() {
        var url = u.trim(); if (url.isEmpty()) return
        if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(url)) url = "https://$url"
        links.add(mutableMapOf("t" to (t.trim().ifEmpty { url.replace(Regex("^https?://", RegexOption.IGNORE_CASE), "").substringBefore("/") }), "u" to url)); t = ""; u = ""; Store.touch()
    }
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
        links.forEach { l ->
            Row(Modifier.fillMaxWidth().dashedBottom(p).padding(vertical = 5.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                T(l["t"]?.toString() ?: "", 12.5f, p.accent, maxLines = 1, modifier = Modifier.weight(1f).clickable { openUrl(ctx, l["u"]?.toString() ?: "") })
                T("✕", 11f, p.faint, modifier = Modifier.clickable { links.remove(l); Store.touch() }.padding(4.dp))
            }
        }
        if (links.isEmpty()) T("Add your first link below.", 11f, p.faint)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Field(t, { t = it.take(30) }, "Name", Modifier.weight(1f), size = 13f, dense = true)
        Field(u, { u = it }, "https://…", Modifier.weight(1f), size = 13f, dense = true, onDone = { add() })
        Btn("Add", primary = true, small = true) { add() }
    }
}

// ───────── unit converter ─────────
val UNITS: List<Pair<String, List<Pair<String, Double>>>> = listOf(
    "Length" to listOf("m" to 1.0, "km" to 1000.0, "cm" to .01, "mm" to .001, "mi" to 1609.344, "yd" to .9144, "ft" to .3048, "in" to .0254),
    "Weight" to listOf("kg" to 1.0, "g" to .001, "lb" to .45359237, "oz" to .028349523),
    "Volume" to listOf("L" to 1.0, "mL" to .001, "gal" to 3.785411784, "cup" to .2365882365, "fl oz" to .0295735296),
    "Speed" to listOf("km/h" to 1.0, "mph" to 1.609344, "m/s" to 3.6, "knot" to 1.852),
    "Temperature" to listOf("°C" to 1.0, "°F" to 1.0, "K" to 1.0)
)
fun convertUnit(cat: String, v: Double, from: String, to: String): Double {
    if (cat == "Temperature") {
        val c = if (from == "°C") v else if (from == "°F") (v - 32) * 5 / 9 else v - 273.15
        return if (to == "°C") c else if (to == "°F") c * 9 / 5 + 32 else c + 273.15
    }
    val u = UNITS.first { it.first == cat }.second.toMap()
    return v * u[from]!! / u[to]!!
}

@Composable
private fun ColumnScope.WConverter() {
    val p = pal()
    var cat by remember { mutableStateOf("Length") }
    val units = UNITS.first { it.first == cat }.second.map { it.first }
    var from by remember { mutableStateOf("m") }; var to by remember { mutableStateOf("km") }
    var a by remember { mutableStateOf("1") }
    val fromU = if (from in units) from else units[0]; val toU = if (to in units) to else units[min(1, units.size - 1)]
    Pick("Category", UNITS.map { PickOpt(it.first, it.first) }, cat, { cat = it; val us = UNITS.first { x -> x.first == it }.second.map { x -> x.first }; from = us[0]; to = us[min(1, us.size - 1)] }, dense = true)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(a, { a = it }, "", Modifier.weight(1f), kb = KeyboardType.Decimal, size = 14f, dense = true)
        Pick("From", units.map { PickOpt(it, it) }, fromU, { from = it }, Modifier.weight(1f), dense = true)
    }
    T("=", 12f, p.faint, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Pick("To", units.map { PickOpt(it, it) }, toU, { to = it }, dense = true)
    val v = a.toDoubleOrNull()
    T(if (v != null && v.isFinite()) fmtNum(convertUnit(cat, v, fromU, toU)) + " " + toU else "—", 16f, p.accent, display = true, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

// ───────── heading / text ─────────
@Composable
private fun ColumnScope.WText(w: Widget) {
    val p = pal()
    val size = w.str("size", "l")
    val (fs, col, gl) = when (size) { "s" -> Triple(13f, p.text, false); "m" -> Triple(18f, p.text, false); "xl" -> Triple(38f, p.accent, true); else -> Triple(26f, p.accent, false) }
    T(w.str("text", "Training plan"), fs, col, display = true, glow = gl, lineH = 1.2f,
        align = when (w.str("align", "left")) { "center" -> TextAlign.Center; "right" -> TextAlign.Right; else -> TextAlign.Left }, modifier = Modifier.fillMaxWidth())
}

// ───────── YouTube ─────────
class YtP(val vid: String?, val list: String?)
fun ytParse(raw: String): YtP? {
    val s = raw.trim(); if (s.isEmpty()) return null
    var vid: String? = null; var list: String? = null
    Regex("[?&]list=([A-Za-z0-9_-]+)").find(s)?.let { list = it.groupValues[1] }
    val m = Regex("(?:youtu\\.be/|[?&]v=|/embed/|/shorts/|/live/|/v/)([A-Za-z0-9_-]{11})").find(s)
    if (m != null) vid = m.groupValues[1]
    else if (Regex("^[A-Za-z0-9_-]{11}$").matches(s)) vid = s
    else if (Regex("^(PL|UU|OL|FL|RD)[A-Za-z0-9_-]{10,}$").matches(s)) list = s
    return if (vid != null || list != null) YtP(vid, list) else null
}
fun ytEmbed(p: YtP): String { val q = "?rel=0&modestbranding=1&playsinline=1"; val b = "https://www.youtube-nocookie.com/embed/"
    return if (p.vid != null) b + p.vid + q + (if (p.list != null) "&list=" + p.list else "") else b + "videoseries" + q + "&list=" + p.list }
fun ytWatch(p: YtP): String = if (p.vid != null) "https://www.youtube.com/watch?v=" + p.vid + (if (p.list != null) "&list=" + p.list else "") else "https://www.youtube.com/playlist?list=" + p.list

@Composable
private fun ColumnScope.WYoutube(w: Widget) {
    val p = pal(); val ctx = LocalContext.current
    val yt = ytParse(w.str("url"))
    if (yt == null) {
        var inp by remember { mutableStateOf("") }; var msg by remember { mutableStateOf("") }
        fun load() { if (ytParse(inp) == null) msg = "That doesn’t look like a YouTube link." else { w.data["url"] = inp.trim(); Store.touch() } }
        T("YOUTUBE", 10.5f, p.dim, spacing = .12f)
        Field(inp, { inp = it }, "Paste a YouTube link…", size = 14f, dense = true, onDone = { load() })
        Btn("Load video", Modifier.fillMaxWidth(), primary = true, small = true) { load() }
        if (msg.isNotEmpty()) T(msg, 11f, p.warn)
        return
    }
    val url = ytEmbed(yt)
    AndroidView(
        factory = { c -> WebView(c).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT; webViewClient = WebViewClient(); setBackgroundColor(android.graphics.Color.BLACK); loadUrl(url)
        } },
        modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(4.dp))
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        T("Open on YouTube ↗", 11f, p.accent, modifier = Modifier.weight(1f).clickable { openUrl(ctx, ytWatch(yt)) })
        ChipB("Change", false) { w.data.remove("url"); Store.touch() }
    }
}

// ───────── video player ─────────
@Composable
private fun ColumnScope.WVideo(w: Widget, editing: Boolean) {
    val p = pal(); val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { toast("Saving video…"); if (Media.saveRaw(ctx, uri, w.id)) { w.data["name"] = "Video"; w.data["pos"] = 0.0; Store.touch(); toast("Video saved") } else toast("That file couldn’t be saved.") }
    }
    val has = remember(w.id, Media.rev) { Media.file(w.id).exists() }
    if (!has) {
        val sh = RoundedCornerShape(4.dp)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(Modifier.clip(sh).dashed(p.border, 4.dp).clickable { Sfx.ui("tap"); picker.launch("*/*") }.padding(horizontal = 18.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Ico("video", 28.dp, p.text); T("CHOOSE A VIDEO", 11.5f, p.text, bold = true, spacing = .06f)
            }
        }
        return
    }
    var vv by remember { mutableStateOf<VideoView?>(null) }
    var mp by remember { mutableStateOf<MediaPlayer?>(null) }
    var err by remember { mutableStateOf(false) }
    val rate = w.num("rate", 1.0).toFloat()
    DisposableEffect(w.id) { onDispose { vv?.let { w.data["pos"] = it.currentPosition / 1000.0; Store.touch() } } }
    LaunchedEffect(w.id) { while (true) { delay(2000); vv?.let { if (it.isPlaying) { w.data["pos"] = it.currentPosition / 1000.0 } } } }
    Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color.Black)) {
        AndroidView(
            factory = { c -> VideoView(c).apply {
                val mc = MediaController(c); mc.setAnchorView(this); setMediaController(mc)
                setVideoPath(Media.file(w.id).path)
                setOnPreparedListener { m -> mp = m; runCatching { m.playbackParams = PlaybackParams().setSpeed(max(.25f, rate)) ; m.pause() }
                    val at = w.num("pos"); if (at > 2 && at < duration / 1000.0 - 3) seekTo((at * 1000).toInt()) }
                setOnErrorListener { _, _, _ -> err = true; true }
                vv = this
            } },
            modifier = Modifier.fillMaxSize()
        )
        if (err) T("This device can’t play that video format. An MP4 (H.264) usually works everywhere.", 11f, p.warn, modifier = Modifier.align(Alignment.Center).padding(12.dp))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        T(w.str("name"), 10.5f, p.dim, maxLines = 1, modifier = Modifier.weight(1f))
        Pick("Playback speed", listOf(.5, .75, 1.0, 1.25, 1.5, 2.0).map { PickOpt(Calc.js(it) + "×", it.toString()) }, rate.toDouble().toString(), { v ->
            w.data["rate"] = v.toDouble(); Store.touch(); runCatching { mp?.let { m -> val pl = m.isPlaying; m.playbackParams = PlaybackParams().setSpeed(v.toFloat()); if (!pl) m.pause() } }
        }, Modifier.width(96.dp), dense = true)
        ChipB("Replace", false) { picker.launch("*/*") }
        ChipB("Remove", false) { Media.delete(w.id); w.data.clear(); Store.touch() }
    }
}
