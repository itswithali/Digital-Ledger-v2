@file:OptIn(ExperimentalLayoutApi::class)

package com.dl.app

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ═════════════════════════ COMPUTE ═════════════════════════
class Mode(val id: String, val label: String, val hint: String)
val MODES = listOf(
    Mode("evaluate", "Evaluate", "Evaluate algebra, arithmetic, matrices, functions, and expressions."),
    Mode("derivative", "Derivative", "Differentiate with respect to x, e.g. 3*x^3 - 5*x + 7"),
    Mode("integrate", "Integral", "Numerical definite integral. Example sin(x), lower=0, upper=pi"),
    Mode("solve", "Solve for x", "Solve linear or quadratic equations."),
    Mode("simplify", "Simplify", "Simplify algebraic expressions."),
    Mode("graph", "Graph", "Plot an algebra/calculus expression in x.")
)
val CALC_KEYS = listOf("7", "8", "9", "/", "4", "5", "6", "*", "1", "2", "3", "-", "0", ".", "x", "+", "(", ")", "^", "=", "sqrt(", "sin(", "cos(", "tan(", "asin(", "acos(", "atan(", "pi", "e", "∫")

class HistItem(val input: String, val mode: String, val result: String)
class PlotData(val expr: String, val xmin: Double, val xmax: Double, val pts: List<Pair<Double, Double>>, val ymin: Double, val ymax: Double)

object CalcState {
    val history = mutableStateListOf<HistItem>()
    var mode by mutableStateOf("evaluate")
    var input by mutableStateOf("")
    var lower by mutableStateOf("0")
    var upper by mutableStateOf("pi")
    var result by mutableStateOf("AWAITING INPUT")
    var isError by mutableStateOf(false)
    var gxMin by mutableStateOf("-10")
    var gxMax by mutableStateOf("10")
    var plot by mutableStateOf<PlotData?>(null)
    var status by mutableStateOf("Enter an expression such as x^2 - 4 or sin(x) in x.")

    fun show(t: String, err: Boolean) { result = t; isError = err }
    fun push(input: String, res: String) { history.add(0, HistItem(input, mode, res)); while (history.size > 8) history.removeAt(history.size - 1) }

    fun run() {
        val raw = input.trim(); if (raw.isEmpty()) return
        try {
            when (mode) {
                "evaluate" -> { if (Calc.hasX(raw)) throw Calc.CalcError("x"); val o = Calc.fmt(Calc.eval(raw), 12); show(o, false); push(raw, o) }
                "simplify" -> { val s = Calc.simplify(raw); show(s, false); push(raw, s) }
                "derivative" -> { val d = "d/dx = " + Calc.derivative(raw); show(d, false); push(raw, d) }
                "solve" -> { val s = Calc.solve(raw); show(s, false); push(raw, s) }
                "integrate" -> {
                    val lo = Calc.eval(lower.ifEmpty { "0" }); val hi = Calc.eval(upper.ifEmpty { "pi" })
                    val iv = Calc.integral(raw, lo, hi); val o = "∫ = " + Calc.fmt(iv, 12)
                    show(o, false); push(raw + " [" + Calc.js(lo) + " → " + Calc.js(hi) + "]", o)
                }
                "graph" -> plotGraph()
            }
        } catch (e: Exception) { show("COULD NOT RESOLVE — check the expression.", true) }
    }

    fun plotGraph() {
        val expr = input.trim()
        if (expr.isEmpty()) { status = "Enter an expression in x first."; return }
        val xmin = gxMin.toDoubleOrNull() ?: Double.NaN; val xmax = gxMax.toDoubleOrNull() ?: Double.NaN
        if (!(xmax > xmin)) { status = "X max must be greater than X min."; return }
        val node = try { Calc.parse(expr) } catch (e: Exception) { show("COULD NOT RESOLVE — check the expression.", true); return }
        val pts = ArrayList<Pair<Double, Double>>(); var ymin = Double.POSITIVE_INFINITY; var ymax = Double.NEGATIVE_INFINITY
        for (k in 0 until 720) {
            val x = xmin + (xmax - xmin) * k / 719
            val y = try { Calc.eval(node, x) } catch (e: Exception) { Double.NaN }
            if (y.isFinite()) { pts.add(x to y); if (y < ymin) ymin = y; if (y > ymax) ymax = y }
        }
        if (!ymin.isFinite() || !ymax.isFinite()) { status = "No finite values in this range."; plot = null; return }
        val spread = max(1e-6, ymax - ymin); ymin -= spread * .08; ymax += spread * .08
        plot = PlotData(expr, xmin, xmax, pts, ymin, ymax)
        status = "Graphing $expr | y-range ${Calc.fmt(ymin, 5)} to ${Calc.fmt(ymax, 5)}"
    }
}

@Composable
fun ComputePage() {
    val p = pal(); val s = CalcState
    PageScaffold {
        val mode = MODES.first { it.id == s.mode }
        CardBox {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MODES.forEach { m -> ModeBtn(m.label, s.mode == m.id) { s.mode = m.id; if (m.id == "graph") s.plotGraph() } }
            }
            Spacer(Modifier.height(10.dp))
            T(mode.hint, 11f, p.faint)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(s.input, { s.input = it }, "Type here…", Modifier.weight(1f), onDone = { s.run() })
                Btn("=", primary = true) { s.run() }
            }
            if (s.mode == "integrate") {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { Lbl("Lower bound"); Field(s.lower, { s.lower = it }) }
                    Column(Modifier.weight(1f)) { Lbl("Upper bound"); Field(s.upper, { s.upper = it }) }
                }
            }
            Spacer(Modifier.height(12.dp))
            val shape = RoundedCornerShape(6.dp)
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 54.dp).clip(shape).background(p.panel2).border(1.dp, p.border, shape).padding(16.dp), contentAlignment = Alignment.CenterStart) {
                if (s.isError) T(s.result, 13f, p.warn, bold = true)
                else T(s.result, 19f, p.accent, display = true, glow = true)
            }
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CALC_KEYS.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { k ->
                            val ks = RoundedCornerShape(4.dp)
                            Box(
                                Modifier.weight(1f).clip(ks).background(p.panel).border(1.dp, p.border, ks)
                                    .clickable { Sfx.ui("tap"); when (k) { "=" -> s.run(); "∫" -> s.mode = "integrate"; else -> s.input += k } }.padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) { T(if (k == "acos(") "arccos(" else k, 12f, p.text) }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        CardBox {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(16.dp).height(1.dp).background(p.accent))
                    T("GRAPHING CALCULATOR", 11.5f, p.dim, display = true, spacing = .14f)
                }
                Btn("Plot current", small = true) { s.plotGraph() }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { Lbl("X min"); Field(s.gxMin, { s.gxMin = it }, kb = KeyboardType.Text) }
                Column(Modifier.weight(1f)) { Lbl("X max"); Field(s.gxMax, { s.gxMax = it }, kb = KeyboardType.Text) }
            }
            Spacer(Modifier.height(10.dp))
            GraphCanvas(s.plot)
            Spacer(Modifier.height(8.dp))
            T(s.status, 11f, p.faint)
        }
        SectionTitle("Recent")
        CardBox {
            if (s.history.isEmpty()) T("NOTHING CALCULATED YET", 12f, p.faint)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.history.forEach { h ->
                    val hs = RoundedCornerShape(4.dp)
                    Column(Modifier.fillMaxWidth().clip(hs).background(p.panel).border(1.dp, p.border, hs).clickable { s.mode = h.mode; s.input = h.input.substringBefore(" [") }.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        T(h.mode.uppercase(), 11.5f, p.text, bold = true)
                        T(h.input, 11.5f, p.dim); T(h.result, 11.5f, p.dim)
                    }
                }
            }
        }
    }
}

@Composable
fun GraphCanvas(pd: PlotData?) {
    val p = pal(); val tm = rememberTextMeasurer()
    val bgc = Color(2, 10, 18).copy(alpha = .78f)
    val shape = RoundedCornerShape(5.dp)
    Canvas(Modifier.fillMaxWidth().height(320.dp).clip(shape).background(bgc).border(1.dp, p.border, shape)) {
        val w = size.width; val h = size.height
        val pad = 42.dp.toPx(); val pw = w - pad * 1.3f; val ph = h - pad * 1.35f; val x0 = pad; val y0 = h - pad
        val gridC = Color(90, 160, 190).copy(alpha = .2f)
        for (i in 0..10) { val gx = x0 + pw * i / 10; drawLine(gridC, Offset(gx, y0 - ph), Offset(gx, y0), 1.dp.toPx()) }
        for (j in 0..8) { val gy = y0 - ph * j / 8; drawLine(gridC, Offset(x0, gy), Offset(x0 + pw, gy), 1.dp.toPx()) }
        if (pd == null) return@Canvas
        fun px(x: Double) = (x0 + (x - pd.xmin) / (pd.xmax - pd.xmin) * pw).toFloat()
        fun py(y: Double) = (y0 - (y - pd.ymin) / (pd.ymax - pd.ymin) * ph).toFloat()
        val axC = Color(100, 190, 220).copy(alpha = .45f)
        if (pd.xmin <= 0 && 0 <= pd.xmax) drawLine(axC, Offset(px(0.0), y0 - ph), Offset(px(0.0), y0), 1.4.dp.toPx())
        if (pd.ymin <= 0 && 0 <= pd.ymax) drawLine(axC, Offset(x0, py(0.0)), Offset(x0 + pw, py(0.0)), 1.4.dp.toPx())
        val path = Path(); var pen = false; var prevY: Float? = null
        pd.pts.forEach { (x, y) ->
            val yy = py(y)
            if (prevY != null && abs(yy - prevY!!) > ph * 1.7f) pen = false
            if (!pen) { path.moveTo(px(x), yy); pen = true } else path.lineTo(px(x), yy)
            prevY = yy
        }
        drawPath(path, Color(0xFF38BDF8), style = Stroke(2.dp.toPx()))
        drawText(tm, "x: ${Calc.js(pd.xmin)} → ${Calc.js(pd.xmax)}", Offset(x0, 4.dp.toPx()), TextStyle(color = Color(0xFF9BDCFF), fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
    }
}

// ═════════════════════════ FOCUS ═════════════════════════
object FocusT {
    var total by mutableIntStateOf(1500)
    var remaining by mutableIntStateOf(1500)
    var running by mutableStateOf(false)
    var label by mutableStateOf("")
    var classId by mutableStateOf("")
    var endAt = 0L

    fun setMinutes(m: Int) { total = m * 60; remaining = total }

    fun start() {
        if (running) {
            running = false; remaining = max(1, ceil((endAt - System.currentTimeMillis()) / 1000.0).toInt())
            Notifier.cancelTimer(Store.app, "focus")
        } else {
            if (remaining <= 0) remaining = total
            endAt = System.currentTimeMillis() + remaining * 1000L; running = true
            Notifier.scheduleTimer(Store.app, "focus", endAt, "Focus session complete", (total / 60).toString() + " min" + if (label.isNotEmpty()) " · $label" else "")
        }
    }

    fun reset() { running = false; remaining = total; Notifier.cancelTimer(Store.app, "focus") }

    fun tick() {
        if (!running) return
        val rem = max(0, ceil((endAt - System.currentTimeMillis()) / 1000.0).toInt())
        if (rem != remaining) remaining = rem
        if (rem <= 0) complete()
    }

    private fun complete() {
        running = false
        Notifier.cancelTimer(Store.app, "focus")
        val mins = (total / 60.0).roundToInt()
        Store.mutate {
            focusLog.add(FocusEntry(uid(), System.currentTimeMillis(), mins, label, classId))
            if (focusLog.size > 500) focusLog = focusLog.takeLast(500).toMutableList()
        }
        remaining = total
        Sfx.done()
        toast("Focus session complete · $mins min")
    }
}

fun fmtClock(s: Int): String = "%02d:%02d".format(s / 60, s % 60)

@Composable
fun FocusPage() {
    val p = pal(); val d = Store.read(); val f = FocusT
    val log = d.focusLog
    val today = LocalDate.now()
    val byDay = HashMap<String, Int>(); var mins = 0; var count = 0
    log.forEach { e ->
        val k = java.time.Instant.ofEpochMilli(e.ts).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        byDay[k] = (byDay[k] ?: 0) + e.minutes
        if (k == today.toString()) { mins += e.minutes; count++ }
    }
    var streak = 0; var dd = today; if ((byDay[dd.toString()] ?: 0) == 0) dd = dd.minusDays(1)
    while ((byDay[dd.toString()] ?: 0) > 0) { streak++; dd = dd.minusDays(1) }
    val week = (6 downTo 0).map { i -> val x = today.minusDays(i.toLong()); DAY_NAMES[x.dayOfWeek.value - 1] to (byDay[x.toString()] ?: 0) }
    val mx = max(1, week.maxOf { it.second })
    val cur = (f.total / 60.0).roundToInt()
    val paused = !f.running && f.remaining < f.total && f.remaining > 0
    var custom by remember { mutableStateOf("") }

    PageScaffold {
        SectionTitle("Focus")
        CardBox {
            Box(Modifier.size(210.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 8f * size.width / 200f; val inset = sw / 2 + 10f * size.width / 200f
                    val tl = Offset(inset, inset); val sz = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(p.border, 0f, 360f, false, tl, sz, style = Stroke(sw))
                    val frac = if (f.total > 0) f.remaining.toFloat() / f.total else 0f
                    if (!p.lite && frac > 0f) drawArc(p.accent.copy(alpha = .3f), -90f, 360f * frac, false, tl, sz, style = Stroke(sw * 2f, cap = StrokeCap.Round))
                    drawArc(p.accent, -90f, 360f * frac, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T(fmtClock(f.remaining), 38f, p.text, display = true, spacing = .04f)
                    Spacer(Modifier.height(4.dp))
                    T(if (f.running) "FOCUSING" else if (paused) "PAUSED" else "READY", 10f, p.faint, spacing = .2f)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                listOf(15, 25, 45, 60).forEach { m -> DayChip("${m}m", m == cur, Modifier.width(52.dp)) { if (!f.running) { f.setMinutes(m); custom = "" } } }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.widthIn(max = 130.dp)) {
                    Lbl("Custom (min)")
                    Field(custom, { v -> custom = v.filter { it.isDigit() }.take(3); val n = custom.toIntOrNull(); if (!f.running && n != null && n in 1..240) f.setMinutes(n) }, "$cur", kb = KeyboardType.Number)
                }
                Column(Modifier.weight(1f)) {
                    Lbl("Class (optional)")
                    Pick("Class", listOf(PickOpt("No specific class", "")) + d.classes.map { PickOpt(it.name, it.id, color = tagColor(p, it)) }, f.classId, { f.classId = it })
                }
            }
            Spacer(Modifier.height(12.dp))
            Lbl("Working on"); Field(f.label, { f.label = it }, "Chapter 4 practice problems")
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn(if (f.running) "Pause" else if (paused) "Resume" else "Start", Modifier.weight(1f), primary = true) { f.start() }
                Btn("Reset", Modifier.weight(1f)) { f.reset() }
            }
        }
        Spacer(Modifier.height(16.dp))
        CardBox {
            T("Your focus", 13f, p.text, display = true, spacing = .06f, upper = true)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBlock(mins, "Min today", Modifier.weight(1f)); StatBlock(count, "Sessions", Modifier.weight(1f)); StatBlock(streak, "Day streak", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp).height(92.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                week.forEach { (lbl, mm) ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Bottom)) {
                        Box(Modifier.width(26.dp).height(max(4, (mm.toFloat() / mx * 64).roundToInt()).dp).graphicsLayer { alpha = .85f }.clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(p.accent))
                        T(lbl, 9f, p.faint, spacing = .06f)
                    }
                }
            }
        }
        SectionTitle("Recent sessions")
        val recent = log.takeLast(8).reversed()
        if (recent.isEmpty()) Empty("No sessions yet", "Finish a focus session and it will be logged here.")
        else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            recent.forEach { e ->
                val c = classById(e.classId); val shape = RoundedCornerShape(5.dp)
                Row(Modifier.fillMaxWidth().hud(p).clip(shape).background(p.panel).border(1.dp, p.border, shape).leftBar(tagColor(p, c)).padding(start = 17.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T("${e.minutes} min" + if (e.label.isNotEmpty()) " · ${e.label}" else "", 14.5f, p.text, bold = true)
                        val dt = java.time.Instant.ofEpochMilli(e.ts).atZone(java.time.ZoneId.systemDefault())
                        T((if (c != null) c.name + " · " else "") + dt.format(DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.getDefault())), 11f, p.faint)
                    }
                    Btn("Remove", ghost = true, small = true) { Store.mutate { focusLog.removeAll { it.id == e.id } } }
                }
            }
        }
    }
}

// ═════════════════════════ SCHEDULE ═════════════════════════
class SchedEv(val title: String, val time: String, val type: String)
object SchedState { var view by mutableStateOf("week"); var cursor by mutableStateOf(LocalDate.now()) }

fun eventsFor(d: AppData, key: String): List<SchedEv> {
    val out = ArrayList<SchedEv>()
    d.events.filter { it.date == key }.forEach { out.add(SchedEv(it.title, it.time, it.type)) }
    val dn = DAY_NAMES[LocalDate.parse(key).dayOfWeek.value - 1]
    d.classes.filter { dn in it.days }.forEach { out.add(SchedEv(it.name, it.time, "class")) }
    d.homework.filter { it.due == key }.forEach { out.add(SchedEv(it.title, "", "task")) }
    return out.sortedBy { it.time.ifEmpty { "99:99" } }
}

@Composable
fun CalEvent(e: SchedEv, showType: Boolean = false) {
    val p = pal()
    val c = when (e.type) { "class" -> p.tags[2]; "task" -> p.good; else -> p.accent2 }
    Column(Modifier.fillMaxWidth().padding(bottom = 5.dp).clip(RoundedCornerShape(2.dp)).background(p.accent.copy(alpha = .06f)).leftBar(c).padding(start = 9.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)) {
        T(e.title, 10f, p.text, bold = true)
        T(if (showType) e.type.uppercase() else if (e.time.isNotEmpty()) fmtTime(e.time) else e.type.uppercase(), 10f, p.faint)
    }
}

@Composable
fun Modifier.dashedCell(): Modifier { val p = pal(); return this.drawBehind {
    val pe = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
    drawLine(p.border, Offset(size.width, 0f), Offset(size.width, size.height), 1f, pathEffect = pe)
    drawLine(p.border, Offset(0f, size.height), Offset(size.width, size.height), 1f, pathEffect = pe)
} }

@Composable
fun SchedulePage() {
    val p = pal(); val d = Store.read(); val s = SchedState
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("event") }
    var date by remember(s.cursor) { mutableStateOf(dateKey(s.cursor)) }
    var time by remember { mutableStateOf("") }
    fun weekStart(x: LocalDate) = x.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val titleText = when (s.view) {
        "month" -> s.cursor.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
        "week" -> "Week of " + weekStart(s.cursor).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
        else -> s.cursor.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()))
    }
    val todayKey = dateKey(LocalDate.now())
    fun open(day: String) { s.cursor = LocalDate.parse(day); s.view = "day" }

    PageScaffold {
        SectionTitle("Schedule")
        CardBox {
            T(titleText, 16f, p.accent, display = true)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Btn("‹", small = true) { s.cursor = when (s.view) { "month" -> s.cursor.minusMonths(1); "week" -> s.cursor.minusDays(7); else -> s.cursor.minusDays(1) } }
                Btn("Today", small = true) { s.cursor = LocalDate.now() }
                Btn("›", small = true) { s.cursor = when (s.view) { "month" -> s.cursor.plusMonths(1); "week" -> s.cursor.plusDays(7); else -> s.cursor.plusDays(1) } }
                Btn("Week", small = true, primary = s.view == "week") { s.view = "week" }
                Btn("Month", small = true, primary = s.view == "month") { s.view = "month" }
                Btn("Day", small = true, primary = s.view == "day") { s.view = "day" }
            }
        }
        Spacer(Modifier.height(16.dp))
        CardBox {
            T("Add task / event / class", 15f, p.text, display = true, spacing = .06f, upper = true)
            Spacer(Modifier.height(12.dp))
            Lbl("Title"); Field(title, { title = it }, "Project review")
            Spacer(Modifier.height(12.dp))
            Lbl("Type"); Pick("Type", listOf(PickOpt("Event", "event"), PickOpt("Task", "task"), PickOpt("Class", "class")), type, { type = it }, Modifier.widthIn(max = 200.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) { Lbl("Date"); DateField(date, { date = it }) }
                Column(Modifier.weight(1f)) { Lbl("Time"); TimeField(time, { time = it }) }
            }
            Spacer(Modifier.height(14.dp))
            Btn("Add to schedule", primary = true) {
                if (title.isNotBlank() && date.isNotEmpty()) { Store.mutate { events.add(EventItem(uid(), title.trim(), date, time, type)) }; title = ""; time = "" }
            }
        }
        Spacer(Modifier.height(16.dp))
        when (s.view) {
            "month" -> {
                val first = LocalDate.of(s.cursor.year, s.cursor.month, 1); val start = weekStart(first)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(p.panel).border(1.dp, p.border, RoundedCornerShape(6.dp)).horizontalScroll(rememberScrollState())) {
                    Column(Modifier.width(805.dp)) {
                        Row { DAY_NAMES.forEach { dn -> T(dn.uppercase(), 10f, p.faint, spacing = .08f, modifier = Modifier.weight(1f).dashedCell().padding(horizontal = 10.dp, vertical = 9.dp)) } }
                        for (r in 0 until 6) Row(Modifier.height(IntrinsicSize.Min)) {
                            for (c in 0 until 7) {
                                val day = start.plusDays((r * 7 + c).toLong()); val key = dateKey(day)
                                val evs = eventsFor(d, key); val isToday = key == todayKey
                                Column(
                                    Modifier.weight(1f).defaultMinSize(minHeight = 112.dp).fillMaxHeight().graphicsLayer { alpha = if (day.month != s.cursor.month) .4f else 1f }
                                        .then(if (isToday) Modifier.border(1.dp, p.accent) else Modifier).dashedCell()
                                        .pointerInput(key) { detectTapGestures(onDoubleTap = { open(key) }) }.padding(8.dp)
                                ) {
                                    T("${day.dayOfMonth}", 12f, p.text, display = true)
                                    Spacer(Modifier.height(7.dp))
                                    evs.take(5).forEach { CalEvent(it) }
                                    if (evs.size > 5) T("+${evs.size - 5} more", 9f, p.faint)
                                }
                            }
                        }
                    }
                }
            }
            "week" -> {
                val start = weekStart(s.cursor); val days = (0..6).map { start.plusDays(it.toLong()) }
                days.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { day ->
                            val key = dateKey(day); val evs = eventsFor(d, key); val sh = RoundedCornerShape(5.dp); val isToday = key == todayKey
                            Column(
                                Modifier.weight(1f).clip(sh).background(p.panel).border(if (isToday) 2.dp else 1.dp, if (isToday) p.accent else p.border, sh)
                                    .pointerInput(key) { detectTapGestures(onDoubleTap = { open(key) }) }.padding(10.dp)
                            ) {
                                T(DAY_NAMES[day.dayOfWeek.value - 1], 11f, p.accent, display = true)
                                T(day.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())), 10f, p.faint)
                                if (evs.isEmpty()) WeekItem("No entries", "", true) else evs.take(4).forEach { WeekItem(it.title, if (it.time.isNotEmpty()) fmtTime(it.time) else it.type.uppercase(), false) }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(p.panel).border(1.dp, p.border, RoundedCornerShape(6.dp)).horizontalScroll(rememberScrollState())) {
                    Column(Modifier.width(980.dp)) {
                        Row {
                            T("TIME", 10f, p.faint, spacing = .08f, modifier = Modifier.width(78.dp).dashedCell().padding(7.dp))
                            days.forEach { day ->
                                Column(Modifier.weight(1f).dashedCell().padding(horizontal = 10.dp, vertical = 9.dp)) {
                                    T(DAY_NAMES[day.dayOfWeek.value - 1].uppercase(), 10f, p.faint, spacing = .08f)
                                    T("${day.dayOfMonth}", 10f, p.accent, spacing = .08f)
                                }
                            }
                        }
                        for (hour in 6..23) Row(Modifier.height(IntrinsicSize.Min)) {
                            T("%02d:00".format(hour), 9f, p.faint, modifier = Modifier.width(78.dp).fillMaxHeight().background(Color.White.copy(alpha = .015f)).dashedCell().padding(7.dp).defaultMinSize(minHeight = 58.dp))
                            days.forEach { day ->
                                val key = dateKey(day)
                                val evs = eventsFor(d, key).filter { it.time.isNotEmpty() && it.time.substringBefore(":").toIntOrNull() == hour }
                                Column(Modifier.weight(1f).fillMaxHeight().defaultMinSize(minHeight = 72.dp).dashedCell().pointerInput(key) { detectTapGestures(onDoubleTap = { open(key) }) }.padding(7.dp)) {
                                    evs.forEach { CalEvent(it) }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                val key = dateKey(s.cursor); val evs = eventsFor(d, key)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(p.panel).border(1.dp, p.border, RoundedCornerShape(6.dp))) {
                    for (hour in 7..22) Row(Modifier.height(IntrinsicSize.Min)) {
                        T("%02d:00".format(hour), 9f, p.faint, modifier = Modifier.width(110.dp).fillMaxHeight().defaultMinSize(minHeight = 54.dp).dashedCell().padding(10.dp))
                        Column(Modifier.weight(1f).fillMaxHeight().defaultMinSize(minHeight = 54.dp).dashedCell().padding(10.dp)) {
                            evs.filter { it.time.isNotEmpty() && it.time.substringBefore(":").toIntOrNull() == hour }.forEach { CalEvent(it, showType = true) }
                        }
                    }
                }
            }
        }
        SectionTitle("Added schedule items")
        if (d.events.isEmpty()) Empty("No added events", "Events, tasks, and classes you add here will appear in the calendar and this list.")
        else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            d.events.sortedBy { it.date + it.time }.forEach { e ->
                val sh = RoundedCornerShape(5.dp)
                Row(Modifier.fillMaxWidth().hud(p).clip(sh).background(p.panel).border(1.dp, p.border, sh).leftBar(p.tags[0]).padding(start = 17.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(e.title, 13.5f, p.text, bold = true)
                        Spacer(Modifier.height(4.dp))
                        T(fmtDate(e.date) + "   " + (if (e.time.isNotEmpty()) fmtTime(e.time) else "ALL DAY") + "   " + e.type.uppercase(), 11f, p.dim)
                    }
                    Btn("Remove", ghost = true, small = true) { Store.mutate { events.removeAll { it.id == e.id } } }
                }
            }
        }
    }
}

@Composable
private fun WeekItem(title: String, sub: String, faint: Boolean) {
    val p = pal()
    Column(Modifier.fillMaxWidth().padding(top = 5.dp).drawBehind {
        drawLine(p.border, Offset.Zero, Offset(size.width, 0f), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
    }.padding(top = 5.dp)) {
        T(title, 10f, if (faint) p.faint else p.dim, bold = !faint)
        if (sub.isNotEmpty()) T(sub, 10f, p.faint)
    }
}
