@file:OptIn(ExperimentalLayoutApi::class)

package com.dl.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

val THEME_GROUPS = listOf(
    Triple("text", "UI Elements & Text" to "Text, borders and dividers", listOf("#E6EEF8", "#FFFFFF", "#CBD5E1", "#FDE68A", "#A7F3D0", "#BAE6FD", "#FBCFE8", "#DDD6FE", "#FCA5A5", "#1E293B", "#0B2A44", "#000000")),
    Triple("bg", "Dominant Backgrounds" to "The main app background and panels", listOf("#0B1220", "#050B14", "#0F172A", "#111827", "#1A1A2E", "#0D1B1E", "#1C1917", "#0A0A0A", "#14181F", "#1B1034", "#EAF1F8", "#FFFFFF")),
    Triple("accent", "Accent" to "Buttons, highlights, glow and active tab", listOf("#38BDF8", "#22D3EE", "#2DD4BF", "#34D399", "#A3E635", "#FBBF24", "#FB923C", "#F87171", "#F472B6", "#C084FC", "#818CF8", "#0284C7"))
)
private val THEME_DEFAULT = ThemeColors("#E6EEF8", "#0B1220", "#38BDF8")

fun fmtWhen(ts: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))

private object Meta {
    private fun sp() = Store.app.getSharedPreferences("dl_meta", 0)
    var lastBackup: Long get() = sp().getLong("last", 0); set(v) { sp().edit().putLong("last", v).apply() }
    var undo: String? get() = sp().getString("undo", null); set(v) { sp().edit().putString("undo", v).apply() }
    var undoTs: Long get() = sp().getLong("undoTs", 0); set(v) { sp().edit().putLong("undoTs", v).apply() }
}

private object Bk {
    var staged by mutableStateOf<Store.Staged?>(null)
    var status by mutableStateOf<Pair<Boolean, String>?>(null)   // ok?, text
    var showCode by mutableStateOf("")
    var paste by mutableStateOf("")
    var tick by mutableIntStateOf(0)
}

fun enableReminders() {
    Store.mutate { val n = nprefs(); n.on = true; n.since = System.currentTimeMillis(); n.dismissed = true }
    if (!Notifier.permGranted(Store.app)) Ui.requestNotif()
    toast("Reminders on")
}
fun disableReminders() { Store.mutate { nprefs().on = false } }

@Composable
fun SubTitle(text: String) = T(text, 15f, pal().text, display = true, spacing = .04f, modifier = Modifier.padding(bottom = 8.dp))

@Composable
fun MutedS(text: String, modifier: Modifier = Modifier) = T(text, 11f, pal().faint, lineH = 1.5f, modifier = modifier)

@Composable
fun OptRow(title: String, desc: String, on: Boolean, onPreview: (() -> Unit)?, onClick: () -> Unit) {
    val p = pal(); val sh = RoundedCornerShape(5.dp)
    Row(Modifier.fillMaxWidth().clip(sh).border(if (on) 2.dp else 1.dp, if (on) p.accent else p.border, sh).background(if (on) p.accent.copy(alpha = .08f) else Color.Transparent), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable { Sfx.ui("tap"); onClick() }.padding(horizontal = 12.dp, vertical = 10.dp)) {
            T(title, 13f, p.text, bold = true); T(desc, 10.5f, p.faint)
        }
        if (onPreview != null) Box(Modifier.padding(end = 8.dp).size(44.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, p.border, RoundedCornerShape(4.dp)).clickable { onPreview() }, contentAlignment = Alignment.Center) { T("▶", 14f, p.text) }
    }
}

@Composable
fun OptGroup(title: String, opts: List<Opt>, cur: String, preview: ((String) -> Unit)?, onSelect: (String) -> Unit) {
    val p = pal()
    T(title.uppercase(), 10.5f, p.faint, spacing = .14f, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { opts.forEach { o -> OptRow(o.label, o.desc, o.id == cur, preview?.let { f -> { f(o.id) } }) { onSelect(o.id) } } }
}

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, onChange: (Float) -> Unit, onDone: (() -> Unit)? = null) {
    val p = pal()
    Column(Modifier.padding(bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth()) { T(label.uppercase(), 10.5f, p.dim, bold = true, spacing = .08f, modifier = Modifier.weight(1f)); T(shown, 11f, p.accent) }
        Slider(value, onChange, valueRange = range, onValueChangeFinished = { onDone?.invoke() }, colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.border))
    }
}

@Composable
fun ColorGroup(key: String, title: String, hint: String, colors: List<String>) {
    val p = pal(); val tc = Store.p.themeColors ?: THEME_DEFAULT
    val cur = (when (key) { "text" -> tc.text; "bg" -> tc.bg; else -> tc.accent }).uppercase()
    val isPreset = colors.any { it.equals(cur, true) }
    var hex by remember(cur) { mutableStateOf(cur) }
    var custom by remember { mutableStateOf(false) }
    fun set(c: String) {
        Store.mutate {
            val n = preferences.themeColors?.copy() ?: THEME_DEFAULT.copy()
            when (key) { "text" -> n.text = c; "bg" -> n.bg = c; else -> n.accent = c }
            preferences.themeColors = n
        }
    }
    Column(Modifier.padding(bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            T(title, 13f, p.text, display = true, spacing = .04f); T(hint, 10.5f, p.faint)
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.forEach { c -> Dot(colorOf(c), c, c.equals(cur, true)) { set(c) } }
            Dot(null, if (isPreset) "Custom" else cur, !isPreset) { custom = true }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Lbl("Color code")
            Field(hex, { hex = it.take(7) }, "#RRGGBB", Modifier.weight(1f), size = 14f, dense = true)
            Btn("Apply", small = true) { normHex(hex)?.let { set(it) } ?: toast("Use a color like #38BDF8") }
        }
    }
    if (custom) ColorPickerSheet(cur, { custom = false }) { set(it) }
}

@Composable
private fun Dot(c: Color?, label: String, on: Boolean, onClick: () -> Unit) {
    val p = pal()
    Column(Modifier.width(52.dp).clickable { Sfx.ui("tap"); onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).then(if (c != null) Modifier.background(c) else Modifier.background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))))
                .border(if (on) 3.dp else 1.dp, if (on) p.accent else p.border, CircleShape), contentAlignment = Alignment.Center
        ) { if (c == null) T("+", 16f, Color.White, bold = true) }
        Spacer(Modifier.height(3.dp))
        T(label, 8.5f, if (on) p.text else p.faint, maxLines = 1)
    }
}

@Composable
private fun ColorPickerSheet(cur: String, onClose: () -> Unit, onUse: (String) -> Unit) {
    val rgb = rgbOf(normHex(cur) ?: "#38BDF8")
    val hsv = FloatArray(3).also { android.graphics.Color.RGBToHSV(rgb[0], rgb[1], rgb[2], it) }
    var h by remember { mutableFloatStateOf(hsv[0]) }; var s by remember { mutableFloatStateOf(hsv[1]) }; var v by remember { mutableFloatStateOf(hsv[2]) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    val hexStr = "#%06X".format(argb and 0xFFFFFF)
    Sheet("Custom color", onClose, listOf(SheetBtn("Cancel"), SheetBtn("Use color", primary = true) { onUse(hexStr); true })) {
        Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(6.dp)).background(Color(argb)), contentAlignment = Alignment.Center) { T(hexStr, 14f, if (lumOf(hexStr) > .4) Color.Black else Color.White, bold = true) }
        Spacer(Modifier.height(8.dp))
        SliderRow("Hue", h, 0f..360f, "${h.toInt()}°", { h = it })
        SliderRow("Saturation", s, 0f..1f, "${(s * 100).toInt()}%", { s = it })
        SliderRow("Brightness", v, 0f..1f, "${(v * 100).toInt()}%", { v = it })
    }
}

@Composable
fun CustomizePage() {
    val p = pal(); val d = Store.read(); val ctx = LocalContext.current; val clip = LocalClipboardManager.current
    val pr = d.preferences; val n = Store.nprefs()
    var scaleLocal by remember(pr.uiScale) { mutableFloatStateOf(pr.uiScale.toFloat()) }
    var newTab by remember { mutableStateOf(false) }
    var editTab by remember { mutableStateOf<CustomTab?>(null) }
    var delTab by remember { mutableStateOf<CustomTab?>(null) }
    val dateStamp = java.time.LocalDate.now().toString()

    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(Store.backupJson().toByteArray(Charsets.UTF_8)) }
            Meta.lastBackup = System.currentTimeMillis(); Bk.tick++
            Bk.status = true to "Backup saved. Keep that file somewhere safe (Drive, Files, or send it to yourself)."
        }.onFailure { Bk.status = false to "Could not save the backup file." }
    }
    fun stage(text: String) {
        val (st, err) = Store.parseBackup(text)
        if (err != null) { Bk.staged = null; Bk.status = false to err } else { Bk.staged = st; Bk.status = null }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val txt = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }.getOrNull()
            if (txt == null) Bk.status = false to "Could not read that file. Try choosing it again."
            else if (txt.length > 25 * 1024 * 1024) Bk.status = false to "That file is too large to be a Digital Ledger backup."
            else stage(txt)
        }
    }
    val last = remember(Bk.tick) { Meta.lastBackup }
    val undoTs = remember(Bk.tick, Bk.status) { Meta.undoTs }

    PageScaffold {
        SectionTitle("Customization")

        // ── Backup & restore ──
        CardBox {
            SubTitle("Backup & restore")
            T(if (last > 0) "Last backup · " + fmtWhen(last) else "No backup made yet", 10.5f, p.faint, modifier = Modifier.padding(bottom = 10.dp))
            T("BACK UP", 10.5f, p.faint, spacing = .14f, modifier = Modifier.padding(bottom = 8.dp))
            Btn("Save backup file", Modifier.fillMaxWidth(), primary = true) { saveFile.launch("digital-ledger-backup-$dateStamp.json") }
            Spacer(Modifier.height(8.dp))
            Btn("Copy backup code", Modifier.fillMaxWidth()) {
                val code = Store.backupCode(); clip.setText(AnnotatedString(code)); Meta.lastBackup = System.currentTimeMillis(); Bk.tick++; Bk.showCode = ""
                Bk.status = true to "Backup code copied (${maxOf(1, Math.round(code.length / 1024f))} KB). Paste it into Google Keep, Notes, or a chat with yourself. After you update the app, paste it under Restore."
            }
            Spacer(Modifier.height(6.dp)); MutedS("Do this before installing an update, reinstalling, or switching phones.")
            if (Bk.showCode.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Field(Bk.showCode, {}, "", lines = 4, size = 11f) }
            Spacer(Modifier.height(16.dp))
            T("RESTORE", 10.5f, p.faint, spacing = .14f, modifier = Modifier.padding(bottom = 8.dp))
            Btn("Choose backup file", Modifier.fillMaxWidth()) { openFile.launch(arrayOf("*/*")) }
            Spacer(Modifier.height(12.dp))
            Lbl("Or paste a backup code")
            Field(Bk.paste, { Bk.paste = it; if (Regex("DLBACKUP1:[\\s\\S]+:END").containsMatchIn(it)) stage(it) }, "Paste your backup code here", lines = 3)
            Spacer(Modifier.height(8.dp))
            Btn("Read pasted code", Modifier.fillMaxWidth()) { stage(Bk.paste) }
            val st = Bk.staged
            if (st != null) {
                Spacer(Modifier.height(12.dp))
                val sh = RoundedCornerShape(6.dp); val dd = st.data
                Column(Modifier.fillMaxWidth().clip(sh).border(1.dp, p.accent, sh).background(p.accent.copy(alpha = .06f)).padding(14.dp)) {
                    T("READY TO RESTORE", 10.5f, p.faint, spacing = .14f)
                    if (st.exportedAt != null) { Spacer(Modifier.height(4.dp)); T("Backup made " + (runCatching { fmtWhen(java.time.Instant.parse(st.exportedAt).toEpochMilli()) }.getOrDefault(st.exportedAt)), 12f, p.dim) }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(dd.classes.size to "classes", dd.homework.size to "objectives", dd.notes.size to "notes", dd.events.size to "schedule items", dd.bookmarks.size to "bookmarks", dd.focusLog.size to "focus sessions", dd.customTabs.size to "custom tabs").forEach { (c, l) ->
                            Row(Modifier.clip(RoundedCornerShape(50)).border(1.dp, p.border, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)) { T("$c ", 11f, p.accent, bold = true); T(l, 11f, p.dim) }
                        }
                    }
                    Spacer(Modifier.height(8.dp)); T("This replaces the data currently in the app.", 11.5f, p.warn)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Restore now", primary = true) {
                            Meta.undo = Store.snapshotJson(); Meta.undoTs = System.currentTimeMillis()
                            val keepFiles = Store.d.filesMeta
                            dd.filesMeta = keepFiles
                            Store.replaceData(dd)
                            Bk.staged = null; Bk.paste = ""; Bk.showCode = ""
                            Bk.status = true to "Restored. Your data is back. Made a mistake? You can undo below."; Bk.tick++
                            toast("Backup restored")
                        }
                        Btn("Cancel") { Bk.staged = null }
                    }
                }
            }
            Bk.status?.let { (ok, msg) ->
                Spacer(Modifier.height(12.dp))
                val c = if (ok) p.good else p.warn
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(c.copy(alpha = .12f)).border(1.dp, c, RoundedCornerShape(5.dp)).padding(10.dp)) { T(msg, 11.5f, p.text, lineH = 1.5f) }
            }
            if (undoTs > 0 && Meta.undo != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("Last restore · " + fmtWhen(undoTs), 11f, p.faint, modifier = Modifier.weight(1f))
                    Btn("Undo restore", small = true) {
                        val prev = Meta.undo?.let { Store.fromSnapshot(it) }
                        if (prev == null) Bk.status = false to "Could not undo the restore."
                        else { prev.filesMeta = Store.d.filesMeta; Store.replaceData(prev); Meta.undo = null; Meta.undoTs = 0; Bk.status = true to "Restore undone. Your previous data is back."; Bk.tick++ }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            MutedS("Files added in the Files tab and pictures or videos placed in custom-tab widgets are stored on this device and are not part of a backup.")
        }
        Spacer(Modifier.height(16.dp))

        // ── Theme colors ──
        CardBox {
            SubTitle("Theme colors")
            MutedS("Pick a color circle (the code is shown under it) or choose your own. Changes apply instantly and are saved with your backup.", Modifier.padding(bottom = 14.dp))
            THEME_GROUPS.forEach { (k, tt, cols) -> ColorGroup(k, tt.first, tt.second, cols) }
            val tc = pr.themeColors ?: THEME_DEFAULT
            val ratio = contrastOf(normHex(tc.text) ?: "#E6EEF8", normHex(tc.bg) ?: "#0B1220")
            T(if (ratio < 4.5) "⚠ Low contrast between text and background (%.1f:1) — text may be hard to read.".format(ratio) else "Text contrast %.1f:1 — easy to read.".format(ratio), 11f, if (ratio < 4.5) p.warn else p.faint, lineH = 1.5f)
            Spacer(Modifier.height(8.dp))
            Btn("Reset to default theme", small = true) { Store.mutate { preferences.themeColors = null } }
        }
        Spacer(Modifier.height(16.dp))

        // ── Startup & sounds ──
        CardBox {
            SubTitle("Startup & sounds")
            MutedS("Tap an option to select it and ▶ to preview. Choices are saved with your backup.")
            OptGroup("Startup animation", BOOT_STYLES, pr.bootStyle, { id -> Boot.play(id); Sfx.startup(true) }) { id -> Store.mutate { preferences.bootStyle = id } }
            OptGroup("Startup sound", BOOT_SOUNDS, pr.bootSound, { id -> Sfx.startup(true, id) }) { id -> Store.mutate { preferences.bootSound = id } }
            OptGroup("Interface sounds", UI_PACKS, pr.uiPack, { id -> Sfx.previewUi(id) }) { id -> Store.mutate { preferences.uiPack = id } }
            Spacer(Modifier.height(14.dp))
            SliderRow("Sound volume", pr.volume.toFloat(), 0f..1f, "${Math.round(pr.volume * 100)}%", { v -> Store.mutate { preferences.volume = (Math.round(v * 20) / 20.0) } }, { Sfx.previewUi(pr.uiPack) })
        }
        Spacer(Modifier.height(16.dp))

        // ── Reminders ──
        CardBox {
            SubTitle("Reminders & notifications")
            val granted = Notifier.permGranted(ctx)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(if (n.on) "Reminders ON" else "Reminders OFF", if (n.on) 1 else 0)
                Pill(if (granted) "System notifications allowed" else "System notifications not allowed yet", if (granted) 1 else 0)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(if (n.on) "Turn off reminders" else "Turn on reminders", primary = !n.on) { if (n.on) disableReminders() else enableReminders() }
                Btn("Send a test") { if (!granted) Ui.requestNotif(); toast("Test sent — lock the screen or switch apps to see the system version."); Notifier.notify(ctx, 99, "Digital Ledger", "Reminders are working.") }
            }
            Spacer(Modifier.height(10.dp))
            NfRow("Timers & focus", "Alert when a timer or focus session finishes") { OnOff(n.timers) { v -> Store.mutate { nprefs().timers = v } } }
            NfRow("Objectives", "A reminder on the morning an objective is due, and once if it becomes overdue") {
                OnOff(n.hw) { v -> Store.mutate { nprefs().hw = v } }; TimeField(n.hwTime, { t -> Store.mutate { nprefs().hwTime = t } }, Modifier.width(140.dp))
            }
            NfRow("Evening before", "Heads-up the day before something is due") {
                OnOff(n.hwEve) { v -> Store.mutate { nprefs().hwEve = v } }; TimeField(n.hwEveTime, { t -> Store.mutate { nprefs().hwEveTime = t } }, Modifier.width(140.dp))
            }
            NfRow("Classes", "Before each class on the days you set in Classes") {
                OnOff(n.cls) { v -> Store.mutate { nprefs().cls = v } }
                Pick("Remind before class", listOf(0 to "When class starts", 5 to "5 min before", 10 to "10 min before", 15 to "15 min before", 30 to "30 min before", 60 to "1 hour before").map { PickOpt(it.second, it.first.toString()) }, n.clsLead.toString(), { v -> Store.mutate { nprefs().clsLead = v.toInt() } }, Modifier.width(190.dp))
            }
            Spacer(Modifier.height(8.dp))
            MutedS("Reminders are scheduled with Android’s alarm service, so they arrive even when the app is closed.")
        }
        Spacer(Modifier.height(16.dp))

        // ── Performance ──
        CardBox {
            SubTitle("Performance")
            MutedS("Lite mode turns off the animated scanlines, glow and looping effects so the app feels smoother and saves battery. Auto uses it on phones.", Modifier.padding(bottom = 14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(Triple("auto", "Auto", "Lite on phones and weaker devices"), Triple("on", "Lite always", "Smoothest, fewer effects"), Triple("off", "Full effects", "Scanlines, glow and animation")).forEach { (id, t, ds) ->
                    OptRow(t, ds, pr.perf == id, null) { Store.mutate { preferences.perf = id } }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ── My custom tabs ──
        CardBox {
            SubTitle("My custom tabs")
            MutedS("Create tabs with your own name and logo, then fill them with resizable widgets (calculator, pictures, clock, timer, notes, to-do and more).", Modifier.padding(bottom = 12.dp))
            if (d.customTabs.isEmpty()) MutedS("No custom tabs yet.")
            d.customTabs.forEach { t ->
                val sh = RoundedCornerShape(5.dp)
                Column(Modifier.fillMaxWidth().padding(bottom = 7.dp).clip(sh).background(p.panel).border(1.dp, p.border, sh).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Ico(t.icon, 22.dp, p.accent)
                        Column { T(t.name, 13f, p.text, bold = true); MutedS(t.widgets.size.toString() + " widget" + if (t.widgets.size == 1) "" else "s") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Btn("Open", small = true) { Nav.goto("ct-" + t.id) }
                        Btn("Rename / logo", small = true) { editTab = t }
                        Btn("Delete", small = true, danger = true) { delTab = t }
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Btn("+ New tab", primary = true) { newTab = true }
        }
        Spacer(Modifier.height(16.dp))

        // ── Tab organization ──
        CardBox {
            SubTitle("Tab organization")
            val order = pr.tabOrder
            order.forEachIndexed { i, name ->
                val hid = name in pr.hiddenTabs; val locked = name == "customize"
                val sh = RoundedCornerShape(5.dp)
                Row(Modifier.fillMaxWidth().padding(bottom = 7.dp).clip(sh).background(p.panel).border(1.dp, p.border, sh).padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Column(Modifier.weight(1f)) { T(tabLabel(name), 13f, p.text, bold = true); T(if (locked) "ALWAYS VISIBLE" else if (hid) "HIDDEN" else "VISIBLE", 9.5f, p.faint) }
                    Btn(if (hid) "Show" else "Hide", small = true, primary = !hid && !locked, enabled = !locked) { Store.mutate { if (hid) preferences.hiddenTabs.remove(name) else preferences.hiddenTabs.add(name) } }
                    Btn("↑", small = true, enabled = i > 0) { Store.mutate { val o = preferences.tabOrder; val t = o[i - 1]; o[i - 1] = o[i]; o[i] = t } }
                    Btn("↓", small = true, enabled = i < order.size - 1) { Store.mutate { val o = preferences.tabOrder; val t = o[i + 1]; o[i + 1] = o[i]; o[i] = t } }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ── Interface scale ──
        CardBox {
            SubTitle("Interface scale")
            SliderRow("Overall scale", scaleLocal, .85f..1.15f, "${Math.round(scaleLocal * 100)}%", { scaleLocal = it }) { Store.mutate { preferences.uiScale = (Math.round(scaleLocal * 100) / 100.0) } }
            SliderRow("Sidebar width", pr.sidebarSize.toFloat(), 190f..330f, "${pr.sidebarSize}px", { v -> Store.mutate { preferences.sidebarSize = v.toInt() } })
            SliderRow("Card padding", pr.cardPad.toFloat(), 10f..26f, "${pr.cardPad}px", { v -> Store.mutate { preferences.cardPad = v.roundToIntSafe() } })
            SliderRow("Corner radius", pr.radius.toFloat(), 0f..16f, "${pr.radius}px", { v -> Store.mutate { preferences.radius = v.roundToIntSafe() } })
            Spacer(Modifier.height(6.dp))
            Btn("Reset layout") {
                Store.mutate {
                    val o = preferences
                    val order = DEFAULT_TAB_ORDER.toMutableList(); var ix = order.indexOf("customize"); customTabs.forEach { t -> order.add(ix, "ct-" + t.id); ix++ }
                    preferences = Prefs(tabOrder = order, uiScale = 1.0, sidebarSize = 232, cardPad = 16, radius = 6, sound = o.sound, hiddenTabs = mutableListOf(), uiSound = o.uiSound, bootStyle = o.bootStyle, bootSound = o.bootSound, uiPack = o.uiPack, volume = o.volume, themeColors = o.themeColors, perf = o.perf, notify = o.notify)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ── Startup ──
        CardBox {
            SubTitle("Startup")
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) { T("Startup sound", 12.5f, p.text, modifier = Modifier.weight(1f)); OnOff(pr.sound) { v -> Store.mutate { preferences.sound = v }; if (v) Sfx.startup(true) } }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) { T("Interface sounds", 12.5f, p.text, modifier = Modifier.weight(1f)); OnOff(pr.uiSound) { v -> Store.mutate { preferences.uiSound = v }; Sfx.ui("ok") } }
            Btn("Replay startup", small = true) { Boot.play(pr.bootStyle); Sfx.startup(true) }
            Spacer(Modifier.height(7.dp))
            MutedS("Plays a short boot chime when the app opens.")
        }
    }
    if (newTab) TabDialog(null, { newTab = false }) { nt -> Nav.goto("ct-" + nt.id) }
    editTab?.let { t -> TabDialog(t, { editTab = null }) }
    delTab?.let { t ->
        ConfirmSheet("Delete tab", "Delete “" + t.name + "” and all its widgets?", "Delete", true) { ok ->
            delTab = null
            if (ok) {
                t.widgets.forEach { Media.delete(it.id); Notifier.cancelTimer(Store.app, it.id) }
                EditState.set(t.id, false)
                Store.mutate { customTabs.removeAll { it.id == t.id }; preferences.tabOrder.remove("ct-" + t.id); preferences.hiddenTabs.remove("ct-" + t.id) }
            }
        }
    }
}

fun Float.roundToIntSafe() = Math.round(this)

@Composable
private fun OnOff(on: Boolean, onChange: (Boolean) -> Unit) { Btn(if (on) "ON" else "OFF", small = true, primary = on) { onChange(!on) } }

@Composable
private fun Pill(text: String, good: Int) {
    val p = pal(); val c = if (good == 1) p.good else p.border
    Box(Modifier.clip(RoundedCornerShape(50)).border(1.dp, c, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp)) { T(text, 10f, if (good == 1) p.good else p.dim) }
}

@Composable
private fun NfRow(title: String, sub: String, controls: @Composable RowScope.() -> Unit) {
    val p = pal()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        T(title, 13f, p.text, bold = true); T(sub, 10.5f, p.faint, lineH = 1.4f)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = controls)
    }
}
