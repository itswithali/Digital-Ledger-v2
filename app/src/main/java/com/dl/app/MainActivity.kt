@file:OptIn(ExperimentalLayoutApi::class)

package com.dl.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Ui { var requestNotif: () -> Unit = {} }
object Nav { var pending by mutableStateOf<String?>(null); fun goto(id: String) { pending = id } }

val BUILTIN_LABELS = mapOf("today" to "Today", "classes" to "Classes", "homework" to "Objectives", "notes" to "Notes", "calc" to "Compute",
    "focus" to "Focus", "schedule" to "Schedule", "bookmark" to "Bookmark", "files" to "Files", "customize" to "Customize")

fun tabLabel(id: String): String = BUILTIN_LABELS[id] ?: Store.d.customTabs.firstOrNull { "ct-" + it.id == id }?.name ?: "Tab"
fun tabIcon(id: String): String = if (id.startsWith("ct-")) Store.d.customTabs.firstOrNull { "ct-" + it.id == id }?.icon ?: "grid" else "tab-$id"
fun visibleTabs(d: AppData): List<String> {
    val all = DEFAULT_TAB_ORDER + d.customTabs.map { "ct-" + it.id }
    return d.preferences.tabOrder.filter { it in all && (it !in d.preferences.hiddenTabs || it == "customize") }.ifEmpty { listOf("today", "customize") }
}

class MainActivity : ComponentActivity() {
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this); Fonts.init(this); Icons.init(this); Emojis.init(this); Notifier.channel(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        Ui.requestNotif = { if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }
        if (savedInstanceState == null) {
            Boot.play(Store.p.bootStyle)
            Sfx.startup(false)
        }
        Thread { Notifier.rescheduleAll(this) }.start()
        setContent { App() }
    }

    override fun onResume() { super.onResume(); Notifier.visible = true }
    override fun onPause() { super.onPause(); Notifier.visible = false; Store.flush() }
}

@Composable
fun App() {
    Store.rev
    val pr = Store.p
    val cfg = LocalConfiguration.current
    val pal = makePal(pr, cfg.screenWidthDp <= 760)
    val d = LocalDensity.current
    LaunchedEffect(Unit) { while (true) { FocusT.tick(); WidgetTicker.tick(); delay(250) } }
    MaterialTheme(colorScheme = darkColorScheme()) {
        CompositionLocalProvider(LocalPal provides pal, LocalDensity provides Density(d.density * pr.uiScale.toFloat(), d.fontScale)) {
            Box(
                Modifier.fillMaxSize().background(pal.bg).drawBehind {
                    val step = 34.dp.toPx()
                    var x = 0f; while (x < size.width) { drawLine(pal.grid, Offset(x, 0f), Offset(x, size.height)); x += step }
                    var y = 0f; while (y < size.height) { drawLine(pal.grid, Offset(0f, y), Offset(size.width, y)); y += step }
                }.tvFlash().drawWithContent {
                    drawContent()
                    if (!pal.lite) { val st = 3.dp.toPx(); var yy = 0f; while (yy < size.height) { drawLine(androidx.compose.ui.graphics.Color.White.copy(alpha = pal.scan), Offset(0f, yy), Offset(size.width, yy), 1f); yy += st } }
                }
            ) {
                Shell()
            }
            ToastHost()
            BootOverlay()
        }
    }
}

@Composable
fun Shell() {
    val p = pal(); val d = Store.read(); val cfg = LocalConfiguration.current
    val wide = cfg.screenWidthDp > 760
    val ids = visibleTabs(d)
    val pager = rememberPagerState { ids.size }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var newTab by remember { mutableStateOf(false) }
    val curId = ids.getOrElse(pager.currentPage) { "today" }

    LaunchedEffect(Nav.pending, ids) {
        val t = Nav.pending
        if (t != null) { val i = ids.indexOf(t); if (i >= 0) { pager.animateScrollToPage(i); Nav.pending = null } }
    }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(pager.settledPage) { if (first) first = false else Sfx.ui("tab"); val cid = ids.getOrElse(pager.settledPage) { "" }.removePrefix("ct-"); EditState.tabs.removeAll { it != cid } }

    Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (wide) {
            Column(Modifier.width(d.preferences.sidebarSize.dp).fillMaxHeight().background(p.panel).drawBehind { drawLine(p.border, Offset(size.width, 0f), Offset(size.width, size.height), 1f) }.verticalScroll(rememberScrollState()).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                    HexMark(38.dp, 14f); T("DIGITAL LEDGER", 12f, p.text, display = true, spacing = .12f)
                }
                TabList(ids, curId, { i -> scope.launch { pager.animateScrollToPage(i) } }) { newTab = true }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (!wide) {
                Row(
                    Modifier.fillMaxWidth().background(p.bg.copy(alpha = .96f)).drawBehind { drawLine(p.border, Offset(0f, size.height), Offset(size.width, size.height), 1f) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HexMark(38.dp, 14f)
                    T(tabLabel(curId), 13f, p.text, display = true, spacing = .1f, upper = true, maxLines = 1, modifier = Modifier.weight(1f))
                    val sh = RoundedCornerShape(4.dp)
                    Box(Modifier.width(38.dp).height(34.dp).clip(sh).border(1.dp, p.border, sh).background(p.panel2).clickable { Sfx.ui("tap"); menu = !menu }, contentAlignment = Alignment.Center) {
                        T(if (menu) "✕" else "☰", 16f, p.text)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !EditState.on(curId.removePrefix("ct-")), beyondViewportPageCount = 1, key = { ids.getOrElse(it) { "x$it" } }) { page ->
                    Page(ids.getOrElse(page) { "today" })
                }
                if (menu && !wide) {
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .4f)).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { menu = false })
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = (cfg.screenHeightDp * .62f).dp).background(p.bg).border(1.dp, p.border).verticalScroll(rememberScrollState()).padding(10.dp)
                    ) {
                        TabList(ids, curId, { i -> menu = false; scope.launch { pager.animateScrollToPage(i) } }) { menu = false; newTab = true }
                    }
                }
            }
        }
    }
    if (newTab) TabDialog(null, { newTab = false }) { nt -> Nav.goto("ct-" + nt.id) }
}

@Composable
fun TabList(ids: List<String>, cur: String, onPick: (Int) -> Unit, onNew: () -> Unit) {
    val p = pal()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ids.forEachIndexed { i, id ->
            val on = id == cur
            Row(
                Modifier.fillMaxWidth().then(if (on) Modifier.background(p.accent.copy(alpha = .1f)).leftBar(p.accent) else Modifier).clickable { Sfx.ui("tab"); onPick(i) }.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Ico(tabIcon(id), 18.dp, if (on) p.accent else p.dim)
                T(tabLabel(id), 12f, if (on) p.text else p.dim, bold = on, spacing = .1f, upper = true, maxLines = 1, modifier = Modifier.weight(1f))
                if (on) Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(p.accent))
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().dashed(p.border, 4.dp).clickable { Sfx.ui("tap"); onNew() }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            T("+ New tab", 11f, p.text, bold = true, spacing = .08f, upper = true)
        }
    }
}

@Composable
fun Page(id: String) {
    when (id) {
        "today" -> TodayPage()
        "classes" -> ClassesPage()
        "homework" -> ObjectivesPage()
        "notes" -> NotesPage()
        "calc" -> ComputePage()
        "focus" -> FocusPage()
        "schedule" -> SchedulePage()
        "bookmark" -> BookmarksPage()
        "files" -> FilesPage()
        "customize" -> CustomizePage()
        else -> if (id.startsWith("ct-")) CustomTabPage(id.removePrefix("ct-")) else Unit
    }
}
