@file:OptIn(ExperimentalLayoutApi::class)

package com.dl.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ═════════════ per-tab edit mode ═════════════
object EditState { val tabs = mutableStateListOf<String>() ; fun on(id: String) = id in tabs
    fun set(id: String, v: Boolean) { if (v && id !in tabs) tabs.add(id) else if (!v) tabs.remove(id) } }

// ═════════════ widget catalogue ═════════════
class FDef(val k: String, val label: String, val type: String, val def: Any, val options: List<Pair<String, String>> = emptyList())
class WDef(val type: String, val name: String, val desc: String, val icon: String, val w: Int, val h: Int, val fields: List<FDef> = emptyList())

val TZ_OPTS = listOf("" to "Local time", "Asia/Beirut" to "Beirut", "UTC" to "UTC", "America/New_York" to "New York", "America/Chicago" to "Chicago",
    "America/Los_Angeles" to "Los Angeles", "Europe/London" to "London", "Europe/Paris" to "Paris", "Europe/Moscow" to "Moscow", "Asia/Dubai" to "Dubai",
    "Asia/Riyadh" to "Riyadh", "Asia/Kolkata" to "Mumbai", "Asia/Shanghai" to "Beijing", "Asia/Tokyo" to "Tokyo", "Australia/Sydney" to "Sydney", "Africa/Cairo" to "Cairo")
private val FIT = listOf("cover" to "Fill (crop)", "contain" to "Whole picture")

val WIDGETS = listOf(
    WDef("calculator", "Calculator", "Quick arithmetic with keypad or keyboard", "calculator", 2, 3),
    WDef("picture", "Picture", "Show any photo from your device", "image", 2, 2, listOf(FDef("fit", "Fit", "select", "cover", FIT), FDef("caption", "Caption", "text", ""))),
    WDef("frame", "Picture frame", "A photo in a decorative frame", "palette", 2, 2, listOf(
        FDef("style", "Frame style", "select", "wood", listOf("wood" to "Classic wood", "gold" to "Gold", "black" to "Gallery black", "white" to "White mat", "polaroid" to "Polaroid", "neon" to "Neon")),
        FDef("fit", "Fit", "select", "cover", FIT), FDef("caption", "Caption", "text", ""))),
    WDef("clock", "Clock", "Digital or analog, any time zone", "clock", 2, 1, listOf(
        FDef("style", "Style", "select", "digital", listOf("digital" to "Digital", "analog" to "Analog")), FDef("tz", "Time zone", "select", "", TZ_OPTS),
        FDef("h12", "12-hour format", "checkbox", false), FDef("secs", "Show seconds", "checkbox", true), FDef("label", "Label", "text", ""))),
    WDef("timer", "Timer", "Countdown with a chime when it ends", "hourglass", 2, 2),
    WDef("stopwatch", "Stopwatch", "Start, stop and record laps", "stopwatch", 2, 2),
    WDef("notes", "Notes", "A pad that saves as you type", "notebook", 2, 2),
    WDef("todo", "Daily to-do", "Small checklist that can reset every day", "checklist", 2, 2, listOf(FDef("daily", "Uncheck everything each new day", "checkbox", true))),
    WDef("counter", "Counter", "Count reps, laps, cups… with +/−", "plus-circle", 1, 1, listOf(FDef("label", "What are you counting?", "text", "Reps"), FDef("step", "Step", "number", 1.0))),
    WDef("scoreboard", "Scoreboard", "Keep score for any game or match", "scoreboard", 2, 2, listOf(FDef("a", "Team / player A", "text", "Home"), FDef("b", "Team / player B", "text", "Away"))),
    WDef("countdown", "Countdown", "Days until a match, exam or trip", "flag", 2, 1, listOf(FDef("label", "Event name", "text", "Big day"), FDef("date", "Date", "date", ""), FDef("time", "Time (optional)", "time", ""))),
    WDef("minical", "Mini calendar", "Month view with your schedule marked", "calendar", 2, 3),
    WDef("goal", "Goal tracker", "Progress bar toward a target", "dart-target", 2, 1, listOf(FDef("label", "Goal", "text", "Weekly goal"), FDef("target", "Target", "number", 100.0), FDef("unit", "Unit", "text", ""), FDef("step", "Step", "number", 5.0))),
    WDef("habit", "Habit tracker", "Tick off the last 7 days and build a streak", "calories", 2, 1, listOf(FDef("label", "Habit", "text", "Workout"))),
    WDef("links", "Quick links", "Shortcuts to sites you use often", "link", 2, 2),
    WDef("converter", "Unit converter", "Length, weight, volume, speed, temperature", "ruler", 2, 2),
    WDef("text", "Heading / text", "A label, motto or section title", "news", 2, 1, listOf(
        FDef("text", "Text", "textarea", "Training plan"), FDef("size", "Size", "select", "l", listOf("s" to "Small", "m" to "Medium", "l" to "Large", "xl" to "Huge")),
        FDef("align", "Align", "select", "left", listOf("left" to "Left", "center" to "Center", "right" to "Right")))),
    WDef("youtube", "YouTube", "Watch a YouTube video or playlist", "video", 2, 2, listOf(FDef("url", "YouTube link", "text", ""))),
    WDef("video", "Video player", "Play a video or audio file from your device", "video", 3, 3)
)
fun wdef(type: String) = WIDGETS.firstOrNull { it.type == type }

// ═════════════ media (pictures / videos saved per widget) ═════════════
object Media {
    var rev by mutableIntStateOf(0)
    fun file(id: String) = File(Store.app.filesDir, "media/$id")
    fun delete(id: String) { file(id).delete(); rev++ }
    fun saveImage(ctx: android.content.Context, uri: android.net.Uri, id: String): Boolean = runCatching {
        val cr = ctx.contentResolver
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1; while (o.outWidth / s > 1600 || o.outHeight / s > 1600) s *= 2
        val bm = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return false
        val f = file(id); f.parentFile?.mkdirs()
        f.outputStream().use { bm.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        rev++; true
    }.getOrDefault(false)
    fun saveRaw(ctx: android.content.Context, uri: android.net.Uri, id: String): Boolean = runCatching {
        val f = file(id); f.parentFile?.mkdirs()
        ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        rev++; true
    }.getOrDefault(false)
}

// ═════════════ background ticking (timers keep running on hidden tabs; to-do daily reset) ═════════════
object WidgetTicker {
    fun all(): List<Pair<CustomTab, Widget>> = Store.d.customTabs.flatMap { t -> t.widgets.map { t to it } }
    fun tick() {
        val now = System.currentTimeMillis()
        all().forEach { (_, w) ->
            if (w.type == "timer" && w.bool("running") && now >= w.num("endAt")) {
                w.data["running"] = false; w.data["remaining"] = w.num("total", 300.0)
                Notifier.cancelTimer(Store.app, w.id)
                Store.touch(); Sfx.done(); toast((w.title.ifEmpty { "Timer" }) + " finished")
            }
            if (w.type == "todo") todoReset(w)
        }
    }
    fun todoReset(w: Widget) {
        val k = java.time.LocalDate.now().toString()
        if (w.str("lastReset").isEmpty()) { w.data["lastReset"] = k; return }
        if (w.bool("daily", true) && w.str("lastReset") != k) {
            w.list("items").forEach { (it as? MutableMap<String, Any?>)?.put("done", false) }
            w.data["lastReset"] = k; Store.touch()
        }
    }
}

// ═════════════ grid solver (ported from the HTML: ctSolve / ctFirstFit / ctTidy) ═════════════
class GRect(val x: Int, val y: Int, val w: Int, val h: Int)
class Placed(val w: Widget, val cw: Int, var x: Int, var y: Int)
fun gOverlap(a: GRect, b: GRect) = a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h
fun gFits(r: GRect, placed: List<GRect>) = placed.none { gOverlap(r, it) }
fun gFirstFit(w: Int, h: Int, cols: Int, placed: List<GRect>): GRect {
    var y = 0
    while (y < 5000) { for (x in 0..(cols - w)) { val r = GRect(x, y, w, h); if (gFits(r, placed)) return r }; y++ }
    return GRect(0, y, w, h)
}
fun gridCols(widthDp: Int) = if (widthDp <= 760) 2 else if (widthDp <= 1100) 3 else 4

fun ctSolve(t: CustomTab, cols: Int, anchor: Widget?, save: Boolean): List<Placed> {
    class It(val w: Widget, val cw: Int, var x: Int?, var y: Int?)
    val items = t.widgets.map { w ->
        val cw = min(w.w, cols); val p = w.pos[cols.toString()]
        It(w, cw, p?.let { max(0, min(cols - cw, it.x)) }, p?.let { max(0, it.y) })
    }
    val placed = ArrayList<GRect>(); val out = ArrayList<Placed>()
    fun commit(it: It, x: Int, y: Int) { it.x = x; it.y = y; placed.add(GRect(x, y, it.cw, it.w.h)); out.add(Placed(it.w, it.cw, x, y)) }
    fun dropDown(it: It) { var y = it.y!!; while (!gFits(GRect(it.x!!, y, it.cw, it.w.h), placed)) y++; commit(it, it.x!!, y) }
    val fixed = items.filter { it.x != null }
    val first = if (anchor != null) fixed.filter { it.w === anchor } else emptyList()
    first.forEach { dropDown(it) }
    fixed.filter { it !in first }.sortedWith(compareBy<It>({ it.y }, { it.x })).forEach { dropDown(it) }
    items.filter { it.x == null }.forEach { val r = gFirstFit(it.cw, it.w.h, cols, placed); commit(it, r.x, r.y) }
    if (save) out.forEach { it.w.pos[cols.toString()] = Pos(it.x, it.y) }
    return out
}

fun ctTidy(t: CustomTab, cols: Int) {
    val items = ctSolve(t, cols, null, false).sortedWith(compareBy<Placed>({ it.y }, { it.x }))
    t.widgets = items.map { it.w }.toMutableList()
    t.widgets.forEach { it.pos.remove(cols.toString()) }
}

fun newWidget(type: String): Widget { val d = wdef(type)!!; return Widget(uid(), type, "", d.w, d.h, mutableMapOf(), mutableMapOf()) }

// ═════════════ custom tab page ═════════════
@Composable
fun CustomTabPage(tabId: String) {
    val p = pal(); val d = Store.read(); val cfg = LocalConfiguration.current
    val t = d.customTabs.firstOrNull { it.id == tabId } ?: return
    val editing = EditState.on(t.id)
    val scroll = rememberScrollState()
    var showAdd by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    val cols = gridCols(cfg.screenWidthDp)
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 90.dp)) {
        DeskHeader()
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Ico(t.icon, 30.dp, p.accent)
            T(t.name, 20f, p.text, display = true, spacing = .06f, upper = true, maxLines = 1, modifier = Modifier.weight(1f))
        }
        FlowRow(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (editing) {
                Btn("+ Add widget") { showAdd = true }
                Btn("Tidy up") { Store.mutate { customTabs.firstOrNull { it.id == t.id }?.let { ctTidy(it, cols) } } }
                Btn("Rename / logo") { showEdit = true }
            }
            Btn(if (editing) "✓ Done" else "✎ Edit", primary = editing) { EditState.set(t.id, !editing) }
        }
        if (editing) T("Edit mode — drag a widget by its header to place it anywhere on the grid, drag the corner triangle to resize, ⚙ to configure, ✕ to remove. “Tidy up” packs everything neatly.", 11f, p.dim, lineH = 1.5f, modifier = Modifier.padding(bottom = 14.dp))
        if (t.widgets.isEmpty()) {
            Empty("This tab is empty", "Press Edit, then + Add widget to build your own dashboard here — calculator, pictures, clock, timer, notes, YouTube, video and more.") {
                Spacer(Modifier.height(12.dp))
                Btn("+ Add your first widget", primary = true) { EditState.set(t.id, true); showAdd = true }
            }
        } else Board(t, editing, scroll, cols)
    }
    if (showAdd) AddWidgetSheet({ showAdd = false }) { type ->
        Store.mutate { customTabs.firstOrNull { it.id == t.id }?.widgets?.add(newWidget(type)) }
        showAdd = false
    }
    if (showEdit) TabDialog(t, { showEdit = false })
}

@Composable
fun AddWidgetSheet(onClose: () -> Unit, onPick: (String) -> Unit) {
    val p = pal()
    Sheet("Add a widget", onClose) {
        WIDGETS.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { w ->
                    val sh = RoundedCornerShape(6.dp)
                    Column(Modifier.weight(1f).fillMaxHeight().clip(sh).background(p.panel).border(1.dp, p.border, sh).clickable { Sfx.ui("tap"); onPick(w.type) }.padding(12.dp)) {
                        Ico(w.icon, 20.dp, p.accent)
                        Spacer(Modifier.height(6.dp))
                        T(w.name, 12.5f, p.text, bold = true)
                        Spacer(Modifier.height(3.dp))
                        T(w.desc, 10f, p.dim)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ═════════════ new / edit tab dialog + icon picker ═════════════
@Composable
fun TabDialog(t: CustomTab?, onClose: () -> Unit, onCreated: ((CustomTab) -> Unit)? = null) {
    val p = pal()
    var name by remember { mutableStateOf(t?.name ?: "") }
    var sel by remember { mutableStateOf(t?.icon ?: "trophy") }
    val isNew = t == null
    Sheet(if (isNew) "New tab" else "Edit tab", onClose, listOf(SheetBtn("Cancel"), SheetBtn(if (isNew) "Create tab" else "Save", primary = true) {
        val nm = name.trim().ifEmpty { "New tab" }
        if (isNew) {
            val nt = CustomTab(uid(), nm, sel, mutableListOf())
            Store.mutate {
                customTabs.add(nt); val o = preferences.tabOrder; val i = o.indexOf("customize"); o.add(if (i > -1) i else o.size, "ct-" + nt.id)
            }
            EditState.set(nt.id, true); onCreated?.invoke(nt)
        } else Store.mutate { customTabs.firstOrNull { it.id == t!!.id }?.let { it.name = nm; it.icon = sel } }
        true
    })) {
        Lbl("Tab name")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val sh = RoundedCornerShape(4.dp)
            Box(Modifier.size(44.dp).clip(sh).border(1.dp, p.accent, sh), contentAlignment = Alignment.Center) { Ico(sel, 22.dp, p.accent) }
            Field(name, { name = it.take(22) }, "e.g. Football, Gym, Finance", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Lbl("Logo — ${Icons.list.count { it.cat != "tab" }} icons + ${Emojis.list.size} emoji, or type your own")
        IconPicker(sel) { sel = it }
    }
}

object Emojis {
    var list: List<String> = emptyList()
    fun init(ctx: android.content.Context) { if (list.isEmpty()) list = JsonParser.parseString(ctx.assets.open("emojis.json").bufferedReader().readText()).asJsonArray.map { it.asString } }
}

@Composable
fun IconPicker(selected: String, onPick: (String) -> Unit) {
    val p = pal(); var q by remember { mutableStateOf("") }; var custom by remember { mutableStateOf("") }
    Field(q, { q = it }, "Search logos… (football, gym, money, music)")
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, p.border, RoundedCornerShape(4.dp)).verticalScroll(rememberScrollState()).padding(10.dp)) {
        val term = q.trim().lowercase(); var any = false
        Icons.cats.forEach { cat ->
            val rows = Icons.list.filter { it.cat == cat && (term.isEmpty() || (it.name.replace("-", " ") + " " + it.tags + " " + cat).lowercase().contains(term)) }
            if (rows.isNotEmpty()) {
                any = true
                T(cat.uppercase(), 10f, p.faint, spacing = .1f, modifier = Modifier.padding(vertical = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { rows.forEach { IconCell(it.name, selected, onPick) } }
            }
        }
        if (!any) T("No logo matches — try an emoji below.", 11f, p.faint)
    }
    Spacer(Modifier.height(8.dp))
    T("EMOJI (${Emojis.list.size})", 10f, p.faint, spacing = .1f, modifier = Modifier.padding(vertical = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Emojis.list.forEach { IconCell("emoji:$it", selected, onPick) } }
    Spacer(Modifier.height(8.dp))
    Field(custom, { custom = it.take(8); if (custom.trim().isNotEmpty()) onPick("emoji:" + custom.trim()) }, "…or type/paste any emoji")
}

@Composable
private fun IconCell(name: String, selected: String, onPick: (String) -> Unit) {
    val p = pal(); val sh = RoundedCornerShape(5.dp); val on = name == selected
    Box(Modifier.size(42.dp).clip(sh).background(if (on) p.accent.copy(alpha = .14f) else p.panel2).border(1.dp, if (on) p.accent else p.border, sh).clickable { Sfx.ui("tap"); onPick(name) }, contentAlignment = Alignment.Center) {
        Ico(name, 22.dp, if (on) p.accent else p.text)
    }
}

// ═════════════ widget settings dialog ═════════════
@Composable
fun WidgetSettings(tab: CustomTab, w: Widget, onClose: () -> Unit) {
    val def = wdef(w.type) ?: return
    val vals = remember { mutableStateMapOf<String, Any>() }
    LaunchedEffect(Unit) {
        vals["__title"] = w.title
        def.fields.forEach { f -> vals[f.k] = when (f.def) { is Boolean -> w.bool(f.k, f.def as Boolean); is Double -> w.num(f.k, f.def as Double); else -> w.str(f.k, f.def.toString()) } }
    }
    val p = pal()
    Sheet(def.name + " settings", onClose, listOf(SheetBtn("Cancel"), SheetBtn("Save", primary = true) {
        Store.mutate {
            val ww = customTabs.firstOrNull { it.id == tab.id }?.widgets?.firstOrNull { it.id == w.id }
            if (ww != null) {
                ww.title = (vals["__title"] as? String ?: "").trim().take(40)
                def.fields.forEach { f ->
                    val v = vals[f.k]
                    ww.data[f.k] = if (f.type == "number") ((v as? String)?.toDoubleOrNull() ?: (v as? Double) ?: f.def) else v
                }
            }
        }
        true
    })) {
        Lbl("Title (optional)"); Field((vals["__title"] as? String) ?: "", { vals["__title"] = it }, "")
        def.fields.forEach { f ->
            Spacer(Modifier.height(12.dp))
            when (f.type) {
                "select" -> { Lbl(f.label); Pick(f.label, f.options.map { PickOpt(it.second, it.first) }, (vals[f.k] as? String) ?: f.def.toString(), { vals[f.k] = it }) }
                "checkbox" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Check((vals[f.k] as? Boolean) ?: (f.def as Boolean)) { vals[f.k] = !((vals[f.k] as? Boolean) ?: (f.def as Boolean)) }
                    T(f.label, 12.5f, p.text)
                }
                "textarea" -> { Lbl(f.label); Field((vals[f.k] as? String) ?: "", { vals[f.k] = it }, "", lines = 3) }
                "date" -> { Lbl(f.label); DateField((vals[f.k] as? String) ?: "", { vals[f.k] = it }) }
                "time" -> { Lbl(f.label); TimeField((vals[f.k] as? String) ?: "", { vals[f.k] = it }) }
                "number" -> { Lbl(f.label); Field(fmtVal(vals[f.k]), { vals[f.k] = it }, "") }
                else -> { Lbl(f.label); Field((vals[f.k] as? String) ?: "", { vals[f.k] = it.take(300) }, "") }
            }
        }
    }
}
private fun fmtVal(v: Any?): String = when (v) { is Double -> Calc.js(v); null -> ""; else -> v.toString() }

// ═════════════ the board ═════════════
private class DragS {
    var id by mutableStateOf<String?>(null)
    var left by mutableFloatStateOf(0f); var top by mutableFloatStateOf(0f)
    var tx by mutableIntStateOf(0); var ty by mutableIntStateOf(0)
    var finger = Offset.Zero; var grab = Offset.Zero
}
private class ResizeS { var id by mutableStateOf<String?>(null); var nw by mutableIntStateOf(1); var nh by mutableIntStateOf(1) }

@Composable
fun Board(tab: CustomTab, editing: Boolean, scroll: ScrollState, cols: Int) {
    val p = pal(); val d = Store.read(); val density = LocalDensity.current; val cfg = LocalConfiguration.current
    val view = LocalView.current; val scope = rememberCoroutineScope()
    val phone = cfg.screenWidthDp <= 760
    val gapPx = with(density) { (if (phone) 10.dp else 12.dp).toPx() }
    val rowPx = with(density) { (if (phone) 132.dp else 150.dp).toPx() }
    val drag = remember { DragS() }; val rs = remember { ResizeS() }
    var boardCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val settle = remember { Animatable(0f) }
    var relId by remember { mutableStateOf<String?>(null) }
    var relFrom by remember { mutableStateOf(Offset.Zero) }
    var settlingPos by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val colPx = (constraints.maxWidth - gapPx * (cols - 1)) / cols
        val stepX = colPx + gapPx; val stepY = rowPx + gapPx
        val items = ctSolve(tab, cols, null, false)
        var rows = max(1, items.maxOfOrNull { it.y + it.w.h } ?: 1)
        if (editing) rows = max(rows + 2, 6)
        val boardH = with(density) { (rows * rowPx + (rows - 1) * gapPx).toDp() }
        val cellShape = RoundedCornerShape(p.radius)

        Box(
            Modifier.fillMaxWidth().height(boardH).onGloballyPositioned { boardCoords = it }.drawBehind {
                if (editing) for (r in 0 until rows) for (c in 0 until cols) {
                    drawRoundRect(p.accent.copy(alpha = .04f), Offset(c * stepX, r * stepY), Size(colPx, rowPx), androidx.compose.ui.geometry.CornerRadius(p.radius.toPx()))
                    drawRoundRect(p.border.copy(alpha = .5f), Offset(c * stepX, r * stepY), Size(colPx, rowPx), androidx.compose.ui.geometry.CornerRadius(p.radius.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }
            }
        ) {
            // ghost cell showing where the dragged widget will land
            if (drag.id != null) {
                val it = items.firstOrNull { it.w.id == drag.id }
                if (it != null) Box(
                    Modifier.offset { IntOffset((drag.tx * stepX).roundToInt(), (drag.ty * stepY).roundToInt()) }
                        .size(with(density) { (it.cw * colPx + (it.cw - 1) * gapPx).toDp() }, with(density) { (it.w.h * rowPx + (it.w.h - 1) * gapPx).toDp() })
                        .clip(cellShape).background(p.accent.copy(alpha = .14f)).dashed(p.accent, p.radius, 2.dp)
                )
            }
            items.forEach { it ->
                val w = it.w
                key(w.id) {
                    val isDrag = drag.id == w.id; val isRes = rs.id == w.id; val isRel = relId == w.id
                    val finalPos = Offset(it.x * stepX, it.y * stepY)
                    val target = IntOffset(finalPos.x.roundToInt(), finalPos.y.roundToInt())
                    val anim by animateIntOffsetAsState(target, if (isDrag || isRel) snap<IntOffset>() else spring<IntOffset>(stiffness = Spring.StiffnessMediumLow), label = "w")
                    val shownCw = if (isRes) rs.nw else it.cw; val shownH = if (isRes) rs.nh else w.h
                    val pos = when {
                        isDrag -> IntOffset(drag.left.roundToInt(), drag.top.roundToInt())
                        isRel -> { val o = finalPos + (relFrom - finalPos) * settle.value; IntOffset(o.x.roundToInt(), o.y.roundToInt()) }
                        else -> anim
                    }
                    WidgetFrame(
                        tab, w, editing, isDrag,
                        Modifier.offset { pos }.zIndex(if (isDrag || isRel) 10f else 0f)
                            .size(with(density) { (shownCw * colPx + (shownCw - 1) * gapPx).toDp() }, with(density) { (shownH * rowPx + (shownH - 1) * gapPx).toDp() }),
                        shownCw, shownH,
                        onDragStart = { finger, grab ->
                            drag.id = w.id; drag.finger = finger; drag.grab = grab; relId = null
                            drag.tx = it.x; drag.ty = it.y
                            scope.launch {
                                val margin = with(density) { 70.dp.toPx() }
                                while (drag.id == w.id) {
                                    val screenH = view.rootView.height.toFloat(); val py = drag.finger.y
                                    if (py < margin) scroll.scrollBy(-ceil((margin - py) / 5f)) else if (py > screenH - margin) scroll.scrollBy(ceil((py - (screenH - margin)) / 5f))
                                    val bw = boardCoords?.positionInWindow() ?: Offset.Zero
                                    drag.left = drag.finger.x - drag.grab.x - bw.x; drag.top = drag.finger.y - drag.grab.y - bw.y
                                    drag.tx = (drag.left / stepX).roundToInt().coerceIn(0, cols - it.cw)
                                    drag.ty = (drag.top / stepY).roundToInt().coerceIn(0, max(0, rows - w.h))
                                    androidx.compose.runtime.withFrameNanos { }
                                }
                            }
                        },
                        onDragMove = { drag.finger = it },
                        onDragEnd = { commit ->
                            val id = drag.id; val from = Offset(drag.left, drag.top); val tx = drag.tx; val ty = drag.ty
                            if (commit && id != null) {
                                relFrom = from; relId = id
                                Store.mutate {
                                    val tt = customTabs.firstOrNull { x -> x.id == tab.id }
                                    val ww = tt?.widgets?.firstOrNull { x -> x.id == id }
                                    if (tt != null && ww != null) { ww.pos[cols.toString()] = Pos(tx, ty); ctSolve(tt, cols, ww, true) }
                                }
                                drag.id = null
                                scope.launch { settle.snapTo(1f); settle.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)); relId = null }
                            } else drag.id = null
                        },
                        onResizeStart = { rs.id = w.id; rs.nw = it.cw; rs.nh = w.h },
                        onResize = { dx, dy ->
                            rs.nw = max(1, min(cols - it.x, it.cw + (dx / stepX).roundToInt()))
                            rs.nh = max(1, min(6, w.h + (dy / stepY).roundToInt()))
                        },
                        onResizeEnd = {
                            val nw = rs.nw; val nh = rs.nh; val id = rs.id
                            rs.id = null
                            Store.mutate {
                                val tt = customTabs.firstOrNull { x -> x.id == tab.id }
                                val ww = tt?.widgets?.firstOrNull { x -> x.id == id }
                                if (tt != null && ww != null) { ww.w = nw; ww.h = nh; ww.pos[cols.toString()] = Pos(it.x, it.y); ctSolve(tt, cols, ww, true) }
                            }
                        }
                    )
                }
            }
        }
    }
}

// ═════════════ one widget: header + body + resize handle ═════════════
@Composable
private fun WidgetFrame(
    tab: CustomTab, w: Widget, editing: Boolean, lifted: Boolean, modifier: Modifier, cw: Int, h: Int,
    onDragStart: (Offset, Offset) -> Unit, onDragMove: (Offset) -> Unit, onDragEnd: (Boolean) -> Unit,
    onResizeStart: () -> Unit, onResize: (Float, Float) -> Unit, onResizeEnd: () -> Unit
) {
    val p = pal(); val def = wdef(w.type)
    val shape = RoundedCornerShape(p.radius)
    var boxC by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var headC by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    val curW by rememberUpdatedState(w)
    Box(modifier.onGloballyPositioned { boxC = it }.then(if (lifted && !p.lite) Modifier.glowBox(p.accent.copy(alpha = .35f), p.radius, 18.dp) else Modifier)) {
        Column(
            Modifier.fillMaxSize()
                .then(if (editing) Modifier.drawBehind {
                    translate(-3.dp.toPx(), -3.dp.toPx()) {
                        drawRoundRect(p.accent, cornerRadius = androidx.compose.ui.geometry.CornerRadius(p.radius.toPx() + 3.dp.toPx()), size = Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                    }
                } else Modifier)
                .clip(shape).background(p.panel).border(1.dp, p.border, shape)
        ) {
            if (editing || w.title.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 30.dp).drawBehind {
                        drawLine(p.border, Offset(0f, size.height), Offset(size.width, size.height), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                    }.onGloballyPositioned { headC = it }
                        .then(if (editing) Modifier.pointerInput(w.id) {
                            detectDragGestures(
                                onDragStart = { off ->
                                    val hc = headC; val bc = boxC
                                    if (hc != null && bc != null) { val fw = hc.localToWindow(off); onDragStart(fw, fw - bc.positionInWindow()) }
                                },
                                onDrag = { change, _ -> change.consume(); headC?.let { onDragMove(it.localToWindow(change.position)) } },
                                onDragEnd = { onDragEnd(true) }, onDragCancel = { onDragEnd(false) }
                            )
                        } else Modifier)
                        .padding(start = 10.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    T(if (w.title.isNotEmpty()) w.title else def?.name ?: "Widget", 10.5f, p.dim, display = true, spacing = .12f, upper = true, maxLines = 1, modifier = Modifier.weight(1f))
                    if (editing) {
                        T("$cw×$h", 9.5f, p.accent, spacing = .04f)
                        WTool("⚙") { showSettings = true }
                        WTool("✕", danger = true) { confirmDel = true }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetBody(tab, w, editing) { showSettings = true }
            }
        }
        if (editing) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(34.dp).pointerInput(w.id) {
                    var ax = 0f; var ay = 0f
                    detectDragGestures(
                        onDragStart = { ax = 0f; ay = 0f; onResizeStart() },
                        onDrag = { change, delta -> change.consume(); ax += delta.x; ay += delta.y; onResize(ax, ay) },
                        onDragEnd = { onResizeEnd() }, onDragCancel = { onResizeEnd() }
                    )
                }
            ) {
                androidx.compose.foundation.Canvas(Modifier.align(Alignment.BottomEnd).size(24.dp)) {
                    val path = Path().apply { moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                    drawPath(path, p.accent, style = Fill)
                }
            }
        }
    }
    if (showSettings) WidgetSettings(tab, w, { showSettings = false })
    if (confirmDel) ConfirmSheet("Remove widget", "This widget and anything stored in it will be removed.", "Remove", true) { ok ->
        confirmDel = false
        if (ok) { Media.delete(curW.id); Notifier.cancelTimer(Store.app, curW.id); Store.mutate { customTabs.firstOrNull { it.id == tab.id }?.widgets?.removeAll { it.id == curW.id } } }
    }
}

@Composable
private fun WTool(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val p = pal(); val sh = RoundedCornerShape(4.dp)
    Box(Modifier.defaultMinSize(minWidth = 24.dp, minHeight = 22.dp).clip(sh).background(p.panel2).border(1.dp, if (danger) p.warn.copy(alpha = .6f) else p.border, sh).clickable { Sfx.ui("tap"); onClick() }.padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
        T(label, 11f, if (danger) p.warn else p.text)
    }
}
