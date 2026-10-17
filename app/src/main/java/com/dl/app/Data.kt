package com.dl.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.time.Instant

// Field names match the HTML app's JSON exactly, so its backups restore here and vice-versa.

fun uid(): String = java.lang.Long.toString(System.currentTimeMillis(), 36) + java.lang.Long.toString((Math.random() * 2e9).toLong(), 36).take(6)

data class ClassItem(
    var id: String = "", var name: String = "", var professor: String = "", var location: String = "",
    var days: MutableList<String> = mutableListOf(), var time: String = "09:00", var colorIdx: Int = 0
)

data class Hw(
    var id: String = "", var title: String = "", var classId: String = "", var due: String = "",
    var notes: String = "", var done: Boolean = false, var created: Long = 0
)

data class NoteItem(var id: String = "", var title: String = "", var body: String = "", var updated: Long = 0)
data class EventItem(var id: String = "", var title: String = "", var date: String = "", var time: String = "", var type: String = "event")
data class BookmarkItem(
    var id: String = "", var title: String = "", var url: String = "", var collection: String = "General",
    var note: String = "", var updated: Long = 0
)
data class FocusEntry(var id: String = "", var ts: Long = 0, var minutes: Int = 0, var label: String = "", var classId: String = "")
data class FileMeta(var id: String = "", var name: String = "", var type: String = "", var size: Long = 0, var updated: Long = 0)
data class Pos(var x: Int = 0, var y: Int = 0)

data class Widget(
    var id: String = "", var type: String = "", var title: String = "", var w: Int = 2, var h: Int = 2,
    var data: MutableMap<String, Any?> = mutableMapOf(), var pos: MutableMap<String, Pos> = mutableMapOf()
) {
    fun str(k: String, d: String = ""): String = when (val v = data[k]) { null -> d; is Double -> if (v == Math.rint(v)) v.toLong().toString() else v.toString(); else -> v.toString() }
    fun num(k: String, d: Double = 0.0): Double = when (val v = data[k]) { is Double -> v; is Int -> v.toDouble(); is Long -> v.toDouble(); is String -> v.toDoubleOrNull() ?: d; else -> d }
    fun bool(k: String, d: Boolean = false): Boolean = when (val v = data[k]) { is Boolean -> v; null -> d; else -> d }
    @Suppress("UNCHECKED_CAST")
    fun list(k: String): MutableList<Any?> {
        val v = data[k]
        if (v is MutableList<*>) return v as MutableList<Any?>
        val n = mutableListOf<Any?>(); data[k] = n; return n
    }
    @Suppress("UNCHECKED_CAST")
    fun map(k: String): MutableMap<String, Any?> {
        val v = data[k]
        if (v is MutableMap<*, *>) return v as MutableMap<String, Any?>
        val n = mutableMapOf<String, Any?>(); data[k] = n; return n
    }
}

data class CustomTab(var id: String = "", var name: String = "Tab", var icon: String = "grid", var widgets: MutableList<Widget> = mutableListOf())

data class ThemeColors(var text: String = "#E6EEF8", var bg: String = "#0B1220", var accent: String = "#38BDF8")

data class Notify(
    var on: Boolean = false, var dismissed: Boolean = false, var since: Long = 0, var timers: Boolean = true,
    var hw: Boolean = true, var hwTime: String = "08:00", var hwEve: Boolean = true, var hwEveTime: String = "18:00",
    var cls: Boolean = true, var clsLead: Int = 10
)

val DEFAULT_TAB_ORDER = listOf("today", "classes", "homework", "notes", "calc", "focus", "schedule", "bookmark", "files", "customize")

data class Prefs(
    var tabOrder: MutableList<String> = DEFAULT_TAB_ORDER.toMutableList(),
    var uiScale: Double = 1.0, var sidebarSize: Int = 232, var cardPad: Int = 16, var radius: Int = 6,
    var sound: Boolean = true, var uiSound: Boolean = true,
    var hiddenTabs: MutableList<String> = mutableListOf(),
    var bootStyle: String = "tv", var bootSound: String = "crt", var uiPack: String = "scifi", var volume: Double = 1.0,
    var themeColors: ThemeColors? = null, var perf: String = "auto", var notify: Notify? = null
)

data class AppData(
    var classes: MutableList<ClassItem> = mutableListOf(),
    var homework: MutableList<Hw> = mutableListOf(),
    var notes: MutableList<NoteItem> = mutableListOf(),
    var focusLog: MutableList<FocusEntry> = mutableListOf(),
    var events: MutableList<EventItem> = mutableListOf(),
    var bookmarks: MutableList<BookmarkItem> = mutableListOf(),
    var collections: MutableList<String> = mutableListOf("General", "Study", "Research"),
    var filesMeta: MutableList<FileMeta> = mutableListOf(),
    var customTabs: MutableList<CustomTab> = mutableListOf(),
    var preferences: Prefs = Prefs()
)

object Store {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private var inited = false
    lateinit var app: Context
    private val handler = Handler(Looper.getMainLooper())
    private val saver = Runnable { saveNow() }

    var rev by mutableIntStateOf(0)
    var d: AppData = AppData()

    val p: Prefs get() = d.preferences
    fun nprefs(): Notify { if (d.preferences.notify == null) d.preferences.notify = Notify(); return d.preferences.notify!! }

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        app = ctx.applicationContext
        val js = app.getSharedPreferences("dl", 0).getString("data", null)
        if (js != null) runCatching { gson.fromJson(js, AppData::class.java) }.getOrNull()?.let { d = normalize(it) }
    }

    /** Reading data inside a composable: touch `rev` so the UI refreshes after every change. */
    fun read(): AppData { rev; return d }

    fun touch() { rev++; handler.removeCallbacks(saver); handler.postDelayed(saver, 180) }
    fun mutate(f: AppData.() -> Unit) { d.f(); touch() }

    fun flush() { handler.removeCallbacks(saver); if (inited) saveNow() }

    private fun saveNow() {
        app.getSharedPreferences("dl", 0).edit().putString("data", gson.toJson(d)).apply()
        Thread { Notifier.rescheduleAll(app) }.start()
    }

    private fun normalize(x: AppData): AppData {
        @Suppress("SENSELESS_COMPARISON")
        if (x.preferences == null) x.preferences = Prefs()
        val pr = x.preferences
        @Suppress("SENSELESS_COMPARISON")
        if (pr.tabOrder == null) pr.tabOrder = DEFAULT_TAB_ORDER.toMutableList()
        @Suppress("SENSELESS_COMPARISON")
        if (pr.hiddenTabs == null) pr.hiddenTabs = mutableListOf()
        @Suppress("SENSELESS_COMPARISON")
        if (x.collections == null || x.collections.isEmpty()) x.collections = mutableListOf("General", "Study", "Research")
        x.customTabs.forEach { t ->
            @Suppress("SENSELESS_COMPARISON")
            if (t.widgets == null) t.widgets = mutableListOf()
            t.widgets.forEach { w ->
                @Suppress("SENSELESS_COMPARISON")
                if (w.data == null) w.data = mutableMapOf()
                @Suppress("SENSELESS_COMPARISON")
                if (w.pos == null) w.pos = mutableMapOf()
                w.w = w.w.coerceIn(1, 4); w.h = w.h.coerceIn(1, 6)
            }
        }
        // custom tabs must be in the order list as "ct-<id>"
        val ids = DEFAULT_TAB_ORDER + x.customTabs.map { "ct-" + it.id }
        pr.tabOrder = pr.tabOrder.filter { it in ids }.toMutableList()
        ids.forEach { if (it !in pr.tabOrder) { val i = pr.tabOrder.indexOf("customize"); pr.tabOrder.add(if (i >= 0) i else pr.tabOrder.size, it) } }
        return x
    }

    // ---------------- backup ----------------
    const val BK_PREFIX = "DLBACKUP1:"
    const val BK_END = ":END"

    fun backupJson(): String {
        val o = com.google.gson.JsonObject()
        o.addProperty("app", "digital-ledger"); o.addProperty("version", 4)
        o.addProperty("exportedAt", Instant.now().toString())
        o.add("data", gson.toJsonTree(d))
        return gson.toJson(o)
    }

    private fun crc32(s: String): String {
        val t = LongArray(256) { n -> var c = n.toLong(); repeat(8) { c = if (c and 1L != 0L) (0xEDB88320L xor (c ushr 1)) else (c ushr 1) }; c and 0xFFFFFFFFL }
        var crc = 0xFFFFFFFFL
        for (ch in s) crc = t[((crc xor ch.code.toLong()) and 255L).toInt()] xor (crc ushr 8)
        return java.lang.Long.toHexString((crc xor 0xFFFFFFFFL) and 0xFFFFFFFFL)
    }

    fun backupCode(json: String = backupJson()): String =
        BK_PREFIX + crc32(json) + ":" + Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP) + BK_END

    class Staged(val data: AppData, val exportedAt: String?)

    /** Returns Staged or an error string. */
    fun parseBackup(textIn: String): Pair<Staged?, String?> {
        val text = textIn.replace("\uFEFF", "").trim()
        if (text.isEmpty()) return null to "Nothing to read yet. Paste your backup code or choose a backup file."
        var json = text
        val m = Regex("DLBACKUP1:([0-9a-f]{1,8}):([A-Za-z0-9+/=\\s]+):END").find(text)
        if (m != null) {
            json = try { String(Base64.decode(m.groupValues[2].replace(Regex("\\s+"), ""), Base64.DEFAULT), Charsets.UTF_8) }
            catch (e: Exception) { return null to "The backup code looks damaged. Copy it again in full and retry." }
            if (crc32(json) != m.groupValues[1]) return null to "The backup code is incomplete or changed while copying. Copy it again in full and retry."
        } else if (text.contains("DLBACKUP1:")) return null to "The backup code looks cut off (it should end with :END). Copy it again in full."
        val root = try { JsonParser.parseString(json) } catch (e: Exception) {
            try { JsonParser.parseString(json.replace(Regex("[\u201C\u201D]"), "\"").replace(Regex("[\u2018\u2019]"), "'")) } catch (e2: Exception) { return null to "That doesn't look like a Digital Ledger backup." }
        }
        if (!root.isJsonObject) return null to "That doesn't look like a Digital Ledger backup."
        val ro = root.asJsonObject
        val inc = if (ro.has("data") && ro.get("data").isJsonObject) ro.getAsJsonObject("data") else ro
        if (!inc.has("classes") || !inc.get("classes").isJsonArray || !inc.has("preferences")) return null to "That isn't a Digital Ledger backup."
        val parsed = try { normalize(gson.fromJson(inc, AppData::class.java)) } catch (e: Exception) { return null to "That isn't a Digital Ledger backup." }
        val at = if (ro.has("exportedAt") && !ro.get("exportedAt").isJsonNull) ro.get("exportedAt").asString else null
        return Staged(parsed, at) to null
    }

    fun replaceData(next: AppData) { d = next; touch() }
    fun snapshotJson(): String = gson.toJson(d)
    fun fromSnapshot(js: String): AppData? = runCatching { normalize(gson.fromJson(js, AppData::class.java)) }.getOrNull()
}
