@file:OptIn(ExperimentalLayoutApi::class)

package com.dl.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ───────────── tiny text prompt (HTML's askText) ─────────────
@Composable
fun AskText(title: String, label: String, hint: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by remember { mutableStateOf("") }
    Sheet(title, onDismiss, listOf(SheetBtn("Cancel"), SheetBtn("Add", primary = true) { val t = v.trim(); if (t.isEmpty()) false else { onOk(t); true } })) {
        Lbl(label); Field(v, { v = it }, hint)
    }
}

// ═════════════════════════ BOOKMARKS ═════════════════════════
object BmState { var collection by mutableStateOf("All"); var editId by mutableStateOf<String?>(null) }

@Composable
fun BookmarksPage() {
    val p = pal(); val d = Store.read(); val ctx = LocalContext.current; val s = BmState
    val editing = d.bookmarks.firstOrNull { it.id == s.editId }
    val collections = if (d.collections.isEmpty()) listOf("General") else d.collections.toList()
    var title by remember(s.editId) { mutableStateOf(editing?.title ?: "") }
    var url by remember(s.editId) { mutableStateOf(editing?.url ?: "") }
    var note by remember(s.editId) { mutableStateOf(editing?.note ?: "") }
    var coll by remember(s.editId) { mutableStateOf(editing?.collection ?: collections.first()) }
    var askNew by remember { mutableStateOf(false) }
    PageScaffold {
        SectionTitle("Bookmarks")
        CardBox {
            T(if (editing != null) "Edit bookmark" else "Add bookmark", 14f, p.text, display = true, spacing = .06f, upper = true)
            Spacer(Modifier.height(12.dp))
            Lbl("Title"); Field(title, { title = it }, "Interesting article")
            Spacer(Modifier.height(12.dp))
            Lbl("Collection"); Pick("Collection", collections.map { PickOpt(it, it) }, coll, { coll = it })
            Spacer(Modifier.height(12.dp))
            Lbl("URL"); Field(url, { url = it }, "https://…", kb = androidx.compose.ui.text.input.KeyboardType.Uri)
            Spacer(Modifier.height(12.dp))
            Lbl("Note"); Field(note, { note = it }, "Why you saved this", lines = 3)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Btn(if (editing != null) "Update bookmark" else "Add bookmark", primary = true) {
                    val t = title.trim(); var u = url.trim()
                    if (t.isNotEmpty() && u.isNotEmpty()) {
                        if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(u)) u = "https://$u"
                        if (editing != null) Store.mutate { bookmarks.firstOrNull { it.id == editing.id }?.let { it.title = t; it.url = u; it.collection = coll; it.note = note.trim(); it.updated = System.currentTimeMillis() } }
                        else Store.mutate { bookmarks.add(BookmarkItem(uid(), t, u, coll, note.trim(), System.currentTimeMillis())) }
                        s.editId = null; title = ""; url = ""; note = ""
                    }
                }
                Btn("Clear") { s.editId = null; title = ""; url = ""; note = "" }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(16.dp).height(1.dp).background(p.accent)); T("SAVED", 11.5f, p.dim, display = true, spacing = .14f)
            }
            Pick("Filter", listOf(PickOpt("All", "All")) + collections.map { PickOpt(it, it) }, s.collection, { s.collection = it }, Modifier.width(170.dp))
        }
        Spacer(Modifier.height(12.dp))
        val list = d.bookmarks.filter { s.collection == "All" || it.collection == s.collection }.sortedByDescending { it.updated }
        if (list.isEmpty()) Empty("No bookmarks in this collection", "Save YouTube videos, Instagram posts, articles, references, or any useful URL here.")
        else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { b ->
                val sh = RoundedCornerShape(5.dp)
                Column(Modifier.fillMaxWidth().clip(sh).background(p.panel).border(1.dp, p.border, sh).leftBar(p.accent2).padding(start = 15.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            T(b.title, 13.5f, p.text, bold = true, maxLines = 2)
                            T(b.url, 10.5f, p.faint, maxLines = 1)
                        }
                        Box(Modifier.clip(RoundedCornerShape(3.dp)).border(1.dp, p.border, RoundedCornerShape(3.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                            T(b.collection, 9.5f, p.accent2, spacing = .06f, upper = true)
                        }
                    }
                    if (b.note.isNotEmpty()) { Spacer(Modifier.height(6.dp)); T(b.note, 11.5f, p.dim, lineH = 1.5f) }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Btn("Open", small = true) { openUrl(ctx, b.url) }
                        Btn("Edit", small = true) { s.editId = b.id }
                        Btn("Remove", ghost = true, small = true) { Store.mutate { bookmarks.removeAll { it.id == b.id } } }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(16.dp).height(1.dp).background(p.accent)); T("COLLECTIONS", 11.5f, p.dim, display = true, spacing = .14f)
        }
        Spacer(Modifier.height(12.dp))
        CardBox {
            collections.forEach { c ->
                val count = d.bookmarks.count { it.collection == c }; val on = s.collection == c; val sh = RoundedCornerShape(4.dp)
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(sh).background(if (on) p.accent.copy(alpha = .1f) else Color.Transparent).border(1.dp, if (on) p.accent else p.border, sh)
                        .clickable { Sfx.ui("tap"); s.collection = c }.padding(horizontal = 11.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) { T(c, 12.5f, p.text, modifier = Modifier.weight(1f)); T("$count", 10f, p.faint) }
            }
            Spacer(Modifier.height(4.dp))
            Btn("Add collection", Modifier.fillMaxWidth()) { askNew = true }
        }
    }
    if (askNew) AskText("New collection", "Collection name", "e.g. Physics", { askNew = false }) { name ->
        if (name !in Store.d.collections) Store.mutate { collections.add(name) }
    }
}

fun openUrl(ctx: android.content.Context, raw: String) {
    val u = if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(raw)) raw else "https://$raw"
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// ═════════════════════════ FILES ═════════════════════════
object FilesState { var selected by mutableStateOf<String?>(null) }

fun fileExt(name: String) = name.substringAfterLast('.', "file").lowercase()
fun fmtBytes(n: Long): String = if (n < 1024) "$n B" else if (n < 1024 * 1024) "%.1f KB".format(n / 1024.0) else "%.1f MB".format(n / 1024.0 / 1024.0)
fun fileOf(id: String) = File(Store.app.filesDir, "files/$id")

@Composable
fun FilesPage() {
    val p = pal(); val d = Store.read(); val ctx = LocalContext.current; val s = FilesState
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching {
                val cr = ctx.contentResolver
                var name = "file"; var size = 0L
                cr.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); val si = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (ni >= 0) name = c.getString(ni) ?: name; if (si >= 0) size = c.getLong(si)
                    }
                }
                val id = uid(); val dir = File(ctx.filesDir, "files"); dir.mkdirs()
                cr.openInputStream(uri)?.use { i -> File(dir, id).outputStream().use { o -> i.copyTo(o) } }
                val f = File(dir, id)
                Store.mutate { filesMeta.add(FileMeta(id, name, cr.getType(uri) ?: "", f.length().takeIf { it > 0 } ?: size, System.currentTimeMillis())) }
            }
        }
    }
    PageScaffold {
        SectionTitle("Files")
        CardBox {
            T("Import files", 14f, p.text, display = true, spacing = .06f, upper = true)
            Spacer(Modifier.height(12.dp))
            Lbl("PDF, PPT, Word, Excel, images, text")
            Btn("Choose files", Modifier.fillMaxWidth()) { picker.launch(arrayOf("*/*")) }
            Spacer(Modifier.height(7.dp))
            T("✓ Files are stored locally in this app, so they survive restarts on this device.", 10f, p.good)
        }
        SectionTitle("Library")
        if (d.filesMeta.isEmpty()) Empty("No files loaded", "Drop in a PDF, PowerPoint, Word document, spreadsheet, or other file to preview it here.")
        else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            d.filesMeta.forEach { f ->
                val sh = RoundedCornerShape(6.dp)
                Column(Modifier.fillMaxWidth().clip(sh).background(p.panel).border(1.dp, if (s.selected == f.id) p.accent else p.border, sh).padding(13.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(5.dp)).border(1.dp, p.border, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                        T(fileExt(f.name).uppercase().take(4), 11f, p.accent, display = true)
                    }
                    Spacer(Modifier.height(9.dp))
                    T(f.name, 12.5f, p.text, bold = true, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    T(fmtBytes(f.size) + " · " + f.type.ifEmpty { "unknown" }, 9.5f, p.faint)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Btn("Preview", small = true) { s.selected = f.id }
                        Btn("Open", small = true) {
                            runCatching {
                                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", fileOf(f.id))
                                val mime = f.type.ifEmpty { MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt(f.name)) ?: "*/*" }
                                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), f.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.onFailure { toast("No app can open this file type") }
                        }
                        Btn("Remove", ghost = true, small = true) {
                            fileOf(f.id).delete(); if (s.selected == f.id) s.selected = null
                            Store.mutate { filesMeta.removeAll { it.id == f.id } }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(16.dp).height(1.dp).background(p.accent)); T("PREVIEW", 11.5f, p.dim, display = true, spacing = .14f)
        }
        Spacer(Modifier.height(12.dp))
        val sel = d.filesMeta.firstOrNull { it.id == s.selected }
        if (sel == null) CardBox(Modifier.defaultMinSize(minHeight = 200.dp)) { Empty("Select a file", "Choose Preview on a loaded file to view it here.") }
        else FilePreview(sel)
    }
}

@Composable
fun FilePreview(f: FileMeta) {
    val p = pal(); val ext = fileExt(f.name)
    val file = fileOf(f.id)
    when {
        ext == "pdf" -> {
            val pages by produceState<List<ImageBitmap>>(emptyList(), f.id) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        val out = ArrayList<ImageBitmap>()
                        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
                            for (i in 0 until minOf(r.pageCount, 30)) r.openPage(i).use { pg ->
                                val w = 1100; val h = (w.toFloat() * pg.height / pg.width).toInt()
                                val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); bm.eraseColor(android.graphics.Color.WHITE)
                                pg.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); out.add(bm.asImageBitmap())
                            }
                        }
                        out as List<ImageBitmap>
                    }.getOrDefault(emptyList())
                }
            }
            if (pages.isEmpty()) T("Loading preview…", 12f, p.faint)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.forEach { Image(it, null, Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.FillWidth) }
            }
        }
        ext in listOf("png", "jpg", "jpeg", "gif", "webp") -> {
            val bmp by produceState<ImageBitmap?>(null, f.id) { value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull() } }
            val b = bmp
            if (b != null) Image(b, null, Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(Color(2, 10, 18).copy(alpha = .78f)).padding(10.dp), contentScale = ContentScale.Fit)
            else T("Loading preview…", 12f, p.faint)
        }
        ext in listOf("txt", "csv", "md", "json", "html", "css", "js") -> {
            val txt by produceState("", f.id) { value = withContext(Dispatchers.IO) { runCatching { file.readText().take(200_000) }.getOrDefault("") } }
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 200.dp).heightIn(max = 620.dp).clip(RoundedCornerShape(5.dp)).background(Color(2, 10, 18).copy(alpha = .78f)).border(1.dp, p.border, RoundedCornerShape(5.dp)).verticalScroll(rememberScrollState()).padding(16.dp)) {
                T(txt, 12f, Color(0xFFD7F2FF))
            }
        }
        else -> Empty(f.name, "Preview is not available for ${ext.uppercase()} files inside the app. Use Open to hand the file to another app.")
    }
}
