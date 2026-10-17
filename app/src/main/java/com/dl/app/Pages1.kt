@file:OptIn(ExperimentalLayoutApi::class)

package com.dl.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun dateKey(d: LocalDate): String = d.toString()
fun daysUntil(s: String): Long = if (s.isEmpty()) 9999 else runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(s)) }.getOrDefault(9999)
fun fmtDate(s: String): String = if (s.isEmpty()) "NO DATE" else runCatching { LocalDate.parse(s).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())).uppercase() }.getOrDefault(s)
fun classById(id: String): ClassItem? = Store.d.classes.firstOrNull { it.id == id }
fun tagColor(p: Pal, c: ClassItem?): Color = if (c == null) p.faint else p.tags[c.colorIdx % p.tags.size]

fun Modifier.leftBar(color: Color, w: Float = 3f): Modifier = drawBehind { drawRect(color, Offset.Zero, androidx.compose.ui.geometry.Size(w.dp.toPx(), size.height)) }

// ───────────── shared page scaffold: clock + stats header ─────────────
@Composable
fun PageScaffold(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 90.dp)) {
        DeskHeader()
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
fun DeskHeader(extra: (@Composable () -> Unit)? = null) {
    val p = pal(); val d = Store.read()
    val now by produceState(LocalDateTime.now()) { while (true) { value = LocalDateTime.now(); delay(1000) } }
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        T("SYSTEM TIME", 10f, p.faint, display = true, spacing = .16f)
        Spacer(Modifier.height(4.dp))
        T(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")), 26f, p.accent, display = true, spacing = .03f, glow = true)
        Spacer(Modifier.height(4.dp))
        T(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())).uppercase(), 11.5f, p.dim, spacing = .03f)
        Spacer(Modifier.height(14.dp))
        val dayName = DAY_NAMES[now.dayOfWeek.value - 1]
        val classesToday = d.classes.count { dayName in it.days }
        val dueSoon = d.homework.count { !it.done && daysUntil(it.due) <= 2 }
        val done = d.homework.count { it.done }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBlock(classesToday, "Classes Today", Modifier.weight(1f))
            StatBlock(dueSoon, "Due Soon", Modifier.weight(1f))
            StatBlock(done, "Completed", Modifier.weight(1f))
        }
    }
}

@Composable
fun StatBlock(n: Int, cap: String, modifier: Modifier) {
    val p = pal(); val shape = RoundedCornerShape(5.dp)
    Column(modifier.clip(shape).background(p.panel).border(1.dp, p.border, shape).padding(horizontal = 6.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T("$n", 20f, p.accent, display = true, glow = true)
        Spacer(Modifier.height(2.dp))
        T(cap, 9f, p.faint, spacing = .1f, upper = true, align = TextAlign.Center)
    }
}

@Composable
fun HwItem(h: Hw, compact: Boolean) {
    val p = pal()
    val cls = classById(h.classId)
    val overdue = !h.done && daysUntil(h.due) < 0
    val soon = !h.done && daysUntil(h.due) <= 2
    val bar = if (overdue) p.warn else tagColor(p, cls)
    val shape = RoundedCornerShape(5.dp)
    Row(
        Modifier.fillMaxWidth().alpha2(if (h.done) .5f else 1f).hud(p).clip(shape).background(p.panel).border(1.dp, p.border, shape).leftBar(bar)
            .padding(start = 17.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top
    ) {
        Check(h.done, Modifier.padding(top = 2.dp)) { Store.mutate { homework.firstOrNull { it.id == h.id }?.let { it.done = !it.done } } }
        Column(Modifier.weight(1f)) {
            T(h.title, 13.5f, p.text, bold = true, strike = h.done)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (cls != null) T(cls.name, 11f, p.dim)
                val badge = if (h.due.isNotEmpty()) (if (overdue) "OVERDUE · " + fmtDate(h.due) else fmtDate(h.due)) else "NO DUE DATE"
                val c = if (overdue || soon) p.warn else p.good
                Box(Modifier.clip(RoundedCornerShape(3.dp)).background(if (overdue || soon) p.warnBg else p.goodBg).border(1.dp, c, RoundedCornerShape(3.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    T(badge, 10.5f, c, bold = true, spacing = .03f)
                }
            }
            if (h.notes.isNotEmpty() && !compact) { Spacer(Modifier.height(6.dp)); T(h.notes, 12f, p.dim, lineH = 1.4f) }
        }
        if (!compact) Btn("Remove", ghost = true, small = true) { Store.mutate { homework.removeAll { it.id == h.id } } }
    }
}

fun Modifier.alpha2(a: Float): Modifier = if (a >= 1f) this else this.graphicsLayer { alpha = a }

// ───────────── TODAY ─────────────
val QC_TYPES = listOf("hw-today" to "Objective · due today", "hw-tomorrow" to "Objective · due tomorrow", "hw" to "Objective · no date", "note" to "Note")
private var qcMode = "hw-today"

@Composable
fun TodayPage() {
    val p = pal(); val d = Store.read(); val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(qcMode) }
    val n = Store.nprefs()
    PageScaffold {
        if (!n.on && !n.dismissed) {
            CardBox(Modifier.leftBar(p.accent).padding(bottom = 0.dp)) {
                T("Turn on reminders?", 12.5f, p.text, display = true, spacing = .05f)
                Spacer(Modifier.height(2.dp))
                T("Get a heads-up before classes, when objectives are due, and when a timer finishes.", 12f, p.dim, lineH = 1.5f)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Turn on", primary = true) { enableReminders() }
                    Btn("Not now") { Store.mutate { Store.nprefs().dismissed = true } }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        CardBox {
            Field(text, { text = it.take(200) }, "Quick capture — type, then press Enter", onDone = {
                submitQuick(text, mode); text = ""
            })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pick("Capture as", QC_TYPES.map { PickOpt(it.second, it.first) }, mode, { mode = it; qcMode = it }, Modifier.weight(1f))
                Btn("Add", primary = true) { if (text.isNotBlank()) { submitQuick(text, mode); text = "" } }
            }
        }
        SectionTitle("Schedule")
        val dayName = DAY_NAMES[LocalDate.now().dayOfWeek.value - 1]
        val todays = d.classes.filter { dayName in it.days }.sortedBy { it.time }
        if (todays.isEmpty()) Empty("Nothing on the books today", "Add a class in the Classes tab and it'll show up here on the right days.")
        else CardBox {
            todays.forEachIndexed { i, c ->
                val col = tagColor(p, c)
                Row(
                    Modifier.fillMaxWidth().then(if (i < todays.size - 1) Modifier.drawBehind {
                        drawLine(p.border, Offset(0f, size.height), Offset(size.width, size.height), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * density, 3f * density)))
                    } else Modifier).padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    T(fmtTime(c.time), 12.5f, p.dim, modifier = Modifier.width(78.dp).padding(top = 2.dp))
                    Column(Modifier.width(14.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.padding(top = 5.dp).size(9.dp).clip(CircleShape).background(col))
                        if (i < todays.size - 1) Box(Modifier.padding(top = 4.dp).width(1.dp).weight(1f).background(p.border))
                    }
                    Column(Modifier.weight(1f)) {
                        T(c.name, 14.5f, p.text, display = true, spacing = .02f)
                        Spacer(Modifier.height(3.dp))
                        T(listOf(c.professor, c.location).filter { it.isNotEmpty() }.joinToString(" · "), 11.5f, p.dim)
                    }
                }
            }
        }
        SectionTitle("Objectives Due Soon")
        val up = d.homework.filter { !it.done }.sortedBy { it.due.ifEmpty { "9999" } }.take(6)
        if (up.isEmpty()) Empty("Nothing outstanding", "Add an assignment in the Objectives tab to track it here.")
        else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { up.forEach { HwItem(it, true) } }
    }
}

private fun submitQuick(text0: String, mode: String) {
    val text = text0.trim(); if (text.isEmpty()) return
    qcMode = mode
    if (mode == "note") {
        Store.mutate { notes.add(NoteItem(uid(), if (text.length > 40) text.take(40) + "…" else text, text, System.currentTimeMillis())) }
    } else {
        var due = ""
        if (mode != "hw") due = dateKey(LocalDate.now().plusDays(if (mode == "hw-tomorrow") 1 else 0))
        Store.mutate { homework.add(Hw(uid(), text, "", due, "", false, System.currentTimeMillis())) }
    }
    toast(if (mode == "note") "Saved to Notes" else "Added to Objectives")
}

// ───────────── CLASSES ─────────────
@Composable
fun ClassesPage() {
    val p = pal(); val d = Store.read()
    var editId by remember { mutableStateOf<String?>(null) }
    val ed = d.classes.firstOrNull { it.id == editId }
    var name by remember(editId) { mutableStateOf(ed?.name ?: "") }
    var prof by remember(editId) { mutableStateOf(ed?.professor ?: "") }
    var loc by remember(editId) { mutableStateOf(ed?.location ?: "") }
    var time by remember(editId) { mutableStateOf(ed?.time ?: "") }
    var days by remember(editId) { mutableStateOf((ed?.days ?: mutableListOf()).toSet()) }
    var color by remember(editId) { mutableIntStateOf(ed?.colorIdx ?: 0) }
    PageScaffold {
        CardBox {
            T(if (ed != null) "Edit class" else "Add a class", 14f, p.text, display = true, spacing = .06f, upper = true)
            Spacer(Modifier.height(12.dp))
            Lbl("Class name"); Field(name, { name = it }, "Calculus II")
            Spacer(Modifier.height(12.dp))
            Lbl("Professor (optional)"); Field(prof, { prof = it })
            Spacer(Modifier.height(12.dp))
            Lbl("Location (optional)"); Field(loc, { loc = it }, "Room 214")
            Spacer(Modifier.height(12.dp))
            Lbl("Days")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                DAY_NAMES.forEach { dn -> DayChip(dn, dn in days, Modifier.weight(1f)) { days = if (dn in days) days - dn else days + dn } }
            }
            Spacer(Modifier.height(12.dp))
            Lbl("Time"); TimeField(time, { time = it }, Modifier.widthIn(max = 160.dp))
            Spacer(Modifier.height(12.dp))
            Lbl("Color")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                p.tags.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(4.dp)).background(c)
                            .border(if (i == color) 2.dp else 1.dp, if (i == color) p.text else p.border, RoundedCornerShape(4.dp))
                            .clickable2 { color = i }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn(if (ed != null) "Save changes" else "Add class", primary = true) {
                    if (name.isNotBlank()) {
                        val sorted = days.sortedBy { DAY_NAMES.indexOf(it) }.toMutableList()
                        if (ed != null) Store.mutate { classes.firstOrNull { it.id == ed.id }?.let { it.name = name.trim(); it.professor = prof.trim(); it.location = loc.trim(); it.days = sorted; it.time = time.ifEmpty { "09:00" }; it.colorIdx = color } }
                        else Store.mutate { classes.add(ClassItem(uid(), name.trim(), prof.trim(), loc.trim(), sorted, time.ifEmpty { "09:00" }, color)) }
                        editId = null; name = ""; prof = ""; loc = ""; time = ""; days = emptySet(); color = 0
                    }
                }
                if (ed != null) Btn("Cancel") { editId = null }
            }
        }
        SectionTitle("Course Roster")
        if (d.classes.isEmpty()) Empty("No classes yet", "Add your first class above with its days and time.")
        else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            d.classes.forEach { c ->
                val shape = RoundedCornerShape(5.dp)
                Row(
                    Modifier.fillMaxWidth().hud(p).clip(shape).background(p.panel).border(if (c.id == editId) 2.dp else 1.dp, if (c.id == editId) p.accent else p.border, shape)
                        .leftBar(tagColor(p, c)).padding(start = 17.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        T(c.name, 14.5f, p.text, display = true)
                        Spacer(Modifier.height(3.dp))
                        T(listOf(c.professor, c.location).filter { it.isNotEmpty() }.joinToString(" · "), 11.5f, p.dim)
                        Spacer(Modifier.height(3.dp))
                        T(c.days.joinToString(" ") + " · " + fmtTime(c.time), 11f, p.faint)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Btn("Edit", ghost = true, small = true) { editId = c.id }
                        Btn("Remove", ghost = true, small = true) { if (editId == c.id) editId = null; Store.mutate { classes.removeAll { it.id == c.id } } }
                    }
                }
            }
        }
    }
}

fun Modifier.clickable2(onClick: () -> Unit): Modifier = this.then(Modifier.clickable { Sfx.ui("tap"); onClick() })

// ───────────── OBJECTIVES ─────────────
@Composable
fun ObjectivesPage() {
    val p = pal(); val d = Store.read()
    var title by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    PageScaffold {
        CardBox {
            T("Add an objective", 14f, p.text, display = true, spacing = .06f, upper = true)
            Spacer(Modifier.height(12.dp))
            Lbl("Title"); Field(title, { title = it }, "Problem set 4")
            Spacer(Modifier.height(12.dp))
            Lbl("Due date (optional)"); DateField(due, { due = it })
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipB("Today", false, big = true) { due = dateKey(LocalDate.now()) }
                ChipB("Tomorrow", false, big = true) { due = dateKey(LocalDate.now().plusDays(1)) }
                ChipB("Next week", false, big = true) { due = dateKey(LocalDate.now().plusDays(7)) }
                ChipB("No date", false, big = true) { due = "" }
            }
            Spacer(Modifier.height(12.dp))
            Lbl("Notes (optional)"); Field(notes, { notes = it }, "Chapters 3-4, show your work", lines = 3)
            Spacer(Modifier.height(14.dp))
            Btn("Add objective", primary = true) {
                if (title.isNotBlank()) {
                    Store.mutate { homework.add(Hw(uid(), title.trim(), "", due, notes.trim(), false, System.currentTimeMillis())) }
                    title = ""; due = ""; notes = ""
                }
            }
        }
        SectionTitle("Outstanding")
        val open = d.homework.filter { !it.done }.sortedBy { it.due.ifEmpty { "9999" } }
        if (open.isEmpty()) Empty("All caught up", "New objectives you add will show up here.")
        else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { open.forEach { HwItem(it, false) } }
        val done = d.homework.filter { it.done }
        if (done.isNotEmpty()) {
            SectionTitle("Complete")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { done.forEach { HwItem(it, false) } }
        }
    }
}

// ───────────── NOTES ─────────────
@Composable
fun NotesPage() {
    val p = pal(); val d = Store.read()
    var editId by remember { mutableStateOf<String?>(null) }
    val cur = d.notes.firstOrNull { it.id == editId }
    var title by remember(editId) { mutableStateOf(cur?.title ?: "") }
    var body by remember(editId) { mutableStateOf(cur?.body ?: "") }
    PageScaffold {
        SectionTitle("Notepad")
        CardBox {
            Lbl("Title"); Field(title, { title = it }, "Untitled note")
            Spacer(Modifier.height(12.dp))
            Lbl("Note"); Field(body, { body = it }, "Write anything…", lines = 9)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(if (cur != null) "Editing saved note" else "New note", 10f, p.faint, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Btn("Clear") { editId = null; title = ""; body = "" }
                    Btn(if (cur != null) "Update note" else "Save note", primary = true) {
                        val t = title.trim().ifEmpty { "Untitled note" }; val b = body.trim()
                        if (b.isNotEmpty()) {
                            if (cur != null) Store.mutate { notes.firstOrNull { it.id == cur.id }?.let { it.title = t; it.body = b; it.updated = System.currentTimeMillis() } }
                            else Store.mutate { notes.add(NoteItem(uid(), t, b, System.currentTimeMillis())) }
                            editId = null; title = ""; body = ""
                        }
                    }
                }
            }
        }
        SectionTitle("Saved notes")
        val list = d.notes.sortedByDescending { it.updated }
        if (list.isEmpty()) Empty("No saved notes", "Your saved notes will stay here for preview, editing, and removal.")
        else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            list.forEach { n ->
                val shape = RoundedCornerShape(p.radius)
                Column(Modifier.fillMaxWidth().clip(shape).background(p.panel).border(if (editId == n.id) 2.dp else 1.dp, if (editId == n.id) p.accent else p.border, shape).padding(14.dp)) {
                    T(n.title.ifEmpty { "Untitled" }, 14f, p.text, display = true)
                    Spacer(Modifier.height(3.dp))
                    T(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.MEDIUM).format(java.util.Date(n.updated)), 10f, p.faint)
                    Spacer(Modifier.height(8.dp))
                    T(if (n.body.length > 180) n.body.take(180) + "…" else n.body, 12f, p.dim, lineH = 1.5f)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Preview", small = true) { editId = n.id }
                        Btn("Edit", small = true) { editId = n.id }
                        Btn("Remove", ghost = true, small = true) { if (editId == n.id) editId = null; Store.mutate { notes.removeAll { it.id == n.id } } }
                    }
                }
            }
        }
    }
}
