@file:OptIn(ExperimentalLayoutApi::class)

package com.dl.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

@Composable fun pal(): Pal = LocalPal.current

// ───────────── text ─────────────
@Composable
fun T(
    text: String, size: Float = 13f, color: Color = pal().text, display: Boolean = false, bold: Boolean = false,
    spacing: Float = 0f, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Unspecified, maxLines: Int = Int.MAX_VALUE,
    upper: Boolean = false, strike: Boolean = false, weight: Int = 0, glow: Boolean = false, lineH: Float = 0f
) {
    val p = pal()
    val w = if (weight > 0) FontWeight(weight) else if (bold || display) FontWeight.Bold else FontWeight.Normal
    val fam = if (display) Fonts.display(if (weight > 0) FontWeight(weight) else FontWeight.Bold) else Fonts.mono
    BasicText(
        if (upper) text.uppercase() else text, modifier,
        style = TextStyle(
            color = color, fontSize = size.sp, fontFamily = fam,
            fontWeight = if (display) w else if (bold) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = (spacing * size).sp, textAlign = align,
            textDecoration = if (strike) TextDecoration.LineThrough else null,
            lineHeight = if (lineH > 0f) (lineH * size).sp else TextStyle.Default.lineHeight,
            shadow = if (glow && !p.lite) androidx.compose.ui.graphics.Shadow(p.accent.copy(alpha = .42f), Offset.Zero, 14f) else null
        ),
        maxLines = maxLines, overflow = if (maxLines < Int.MAX_VALUE) TextOverflow.Ellipsis else TextOverflow.Clip
    )
}

fun Modifier.dashed(color: Color, radius: Dp, width: Dp = 1.dp): Modifier = drawBehind {
    drawRoundRect(color, cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
}

/** Soft accent glow behind a rounded box (the CSS box-shadow: var(--glow)). */
fun Modifier.glowBox(color: Color, radius: Dp, blur: Dp = 14.dp): Modifier = drawBehind {
    drawIntoCanvas { c ->
        val paint = Paint()
        val fw = paint.asFrameworkPaint()
        fw.color = color.copy(alpha = 1f).toArgb()
        fw.setShadowLayer(blur.toPx(), 0f, 0f, color.toArgb())
        c.drawRoundRect(2f, 2f, size.width - 2f, size.height - 2f, radius.toPx(), radius.toPx(), paint)
    }
}

/** HUD corner brackets: top-left and bottom-right, 12px, 2px accent. */
fun Modifier.hud(p: Pal): Modifier = drawBehind {
    val l = 12.dp.toPx(); val w = 2.dp.toPx(); val c = p.accent.copy(alpha = .85f)
    drawLine(c, Offset(-1f, -1f + w / 2), Offset(l, -1f + w / 2), w); drawLine(c, Offset(-1f + w / 2, -1f), Offset(-1f + w / 2, l), w)
    drawLine(c, Offset(size.width + 1f, size.height + 1f - w / 2), Offset(size.width - l, size.height + 1f - w / 2), w)
    drawLine(c, Offset(size.width + 1f - w / 2, size.height + 1f), Offset(size.width + 1f - w / 2, size.height - l), w)
}

// ───────────── buttons ─────────────
@Composable
fun Btn(
    text: String, modifier: Modifier = Modifier, primary: Boolean = false, ghost: Boolean = false, small: Boolean = false,
    danger: Boolean = false, enabled: Boolean = true, kind: String = "tap", onClick: () -> Unit
) {
    val p = pal()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) .96f else 1f, label = "btn")
    val shape = RoundedCornerShape(4.dp)
    val bg = if (primary) p.accent else if (ghost) Color.Transparent else p.panel2
    val bd = if (primary) p.accent else if (danger) p.warn else if (ghost) Color.Transparent else p.border
    val fg = if (primary) p.ink else if (danger) p.warn else p.text
    Box(
        modifier.graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (enabled) 1f else .45f }
            .then(if (primary && !p.lite) Modifier.glowBox(p.accent.copy(alpha = .42f), 4.dp) else Modifier)
            .clip(shape).background(bg).border(1.dp, bd, shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled) { Sfx.ui(kind); onClick() }
            .defaultMinSize(minHeight = if (small) 38.dp else 44.dp)
            .padding(horizontal = if (small) 12.dp else 14.dp, vertical = if (small) 8.dp else 10.dp),
        contentAlignment = Alignment.Center
    ) { T(text, if (small) 10.5f else 11.5f, fg, bold = true, spacing = .06f, upper = true, align = TextAlign.Center) }
}

@Composable
fun ChipB(text: String, on: Boolean, modifier: Modifier = Modifier, big: Boolean = false, onClick: () -> Unit) {
    val p = pal(); val shape = RoundedCornerShape(50)
    Box(
        modifier.clip(shape).background(if (on) p.accent else p.panel2).border(1.dp, if (on) p.accent else p.border, shape)
            .clickable { Sfx.ui("tap"); onClick() }
            .defaultMinSize(minHeight = if (big) 34.dp else 28.dp).padding(horizontal = if (big) 12.dp else 9.dp, vertical = if (big) 7.dp else 3.dp),
        contentAlignment = Alignment.Center
    ) { T(text, if (big) 11f else 10.5f, if (on) p.ink else p.dim) }
}

@Composable
fun DayChip(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = pal(); val shape = RoundedCornerShape(4.dp)
    Box(
        modifier.clip(shape).background(if (on) p.accent else p.panel2).border(1.dp, if (on) p.accent else p.border, shape)
            .clickable { Sfx.ui("tap"); onClick() }.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { T(text, 11f, if (on) p.ink else p.dim, bold = true, spacing = .04f) }
}

@Composable
fun ModeBtn(text: String, on: Boolean, onClick: () -> Unit) {
    val p = pal(); val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier.clip(shape).background(if (on) p.accent else p.panel2).border(1.dp, if (on) p.accent else p.border, shape)
            .clickable { Sfx.ui("tap"); onClick() }.padding(horizontal = 13.dp, vertical = 10.dp)
    ) { T(text, 11f, if (on) p.ink else p.dim, bold = true, spacing = .05f, upper = true) }
}

// ───────────── containers ─────────────
@Composable
fun CardBox(modifier: Modifier = Modifier, hud: Boolean = true, pad: Dp? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = pal(); val shape = RoundedCornerShape(p.radius)
    Column(
        modifier.fillMaxWidth().then(if (hud) Modifier.hud(p) else Modifier)
            .clip(shape).background(p.panel).border(1.dp, p.border, shape).padding(pad ?: p.pad),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val p = pal()
    Row(modifier.padding(top = 26.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(16.dp).height(1.dp).background(p.accent))
        T(text, 11.5f, p.dim, display = true, spacing = .14f, upper = true)
    }
}

@Composable
fun Empty(title: String, body: String, extra: (@Composable ColumnScope.() -> Unit)? = null) {
    val p = pal(); val shape = RoundedCornerShape(p.radius)
    Column(Modifier.fillMaxWidth().hud(p).dashed(p.border, p.radius).padding(horizontal = 20.dp, vertical = 24.dp)) {
        T(title, 13.5f, p.text, display = true)
        Spacer(Modifier.height(4.dp))
        T(body, 12.5f, p.dim)
        extra?.invoke(this)
    }
}

@Composable
fun Lbl(text: String) {
    T(text, 10.5f, pal().dim, bold = true, spacing = .08f, upper = true, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
fun FieldBox(modifier: Modifier = Modifier, minH: Dp = 44.dp, content: @Composable BoxScope.() -> Unit) {
    val p = pal(); val shape = RoundedCornerShape(4.dp)
    Box(
        modifier.fillMaxWidth().defaultMinSize(minHeight = minH).clip(shape).background(p.panel2).border(1.dp, p.border, shape)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.CenterStart, content = content
    )
}

@Composable
fun Field(
    value: String, onChange: (String) -> Unit, hint: String = "", modifier: Modifier = Modifier, lines: Int = 1,
    kb: KeyboardType = KeyboardType.Text, size: Float = 16f, fill: Boolean = false, display: Boolean = false,
    onDone: (() -> Unit)? = null, focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    dense: Boolean = false, textAlign: TextAlign = TextAlign.Start
) {
    val p = pal(); val shape = RoundedCornerShape(4.dp)
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = lines == 1, minLines = lines,
        textStyle = TextStyle(color = p.text, fontSize = size.sp, fontFamily = if (display) Fonts.display(FontWeight.Bold) else Fonts.mono, textAlign = textAlign),
        cursorBrush = SolidColor(p.accent),
        keyboardOptions = KeyboardOptions(keyboardType = kb, imeAction = if (onDone != null) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Default),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier.onFocusChanged { focused = it.isFocused }.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        decorationBox = { inner ->
            Box(
                (if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).defaultMinSize(minHeight = if (lines == 1) (if (dense) 36.dp else 44.dp) else 0.dp)
                    .clip(shape).background(p.panel2).border(1.dp, if (focused) p.accent else p.border, shape)
                    .padding(horizontal = if (dense) 8.dp else 10.dp, vertical = if (dense) 6.dp else 9.dp),
                contentAlignment = if (lines == 1) Alignment.CenterStart else Alignment.TopStart
            ) {
                if (value.isEmpty()) T(hint, size, p.faint, modifier = if (textAlign == TextAlign.End) Modifier.fillMaxWidth() else Modifier, align = textAlign)
                inner()
            }
        }
    )
}

// ───────────── date/time ─────────────
@Composable
fun DateField(value: String, onPick: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "mm/dd/yyyy") {
    val ctx = LocalContext.current; val p = pal()
    FieldBox(modifier.clickable {
        val d = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
        DatePickerDialog(ctx, { _, y, m, day -> onPick(LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }) {
        val txt = runCatching { LocalDate.parse(value).let { "%02d/%02d/%04d".format(it.monthValue, it.dayOfMonth, it.year) } }.getOrDefault("")
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(if (txt.isEmpty()) placeholder else txt, 16f, if (txt.isEmpty()) p.faint else p.text, modifier = Modifier.weight(1f))
            Ico("calendar", 18.dp, p.dim)
        }
    }
}

@Composable
fun TimeField(value: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current; val p = pal()
    FieldBox(modifier.clickable {
        val t = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(9, 0))
        TimePickerDialog(ctx, { _, h, m -> onPick("%02d:%02d".format(h, m)) }, t.hour, t.minute, false).show()
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(if (value.isEmpty()) "--:-- --" else fmtTime(value), 16f, if (value.isEmpty()) p.faint else p.text, modifier = Modifier.weight(1f))
            Ico("clock", 18.dp, p.dim)
        }
    }
}

fun fmtTime(t: String?): String {
    if (t.isNullOrEmpty()) return ""
    val parts = t.split(":"); val h = parts[0].toIntOrNull() ?: return t
    val ap = if (h >= 12) "PM" else "AM"; var hh = h % 12; if (hh == 0) hh = 12
    return "$hh:${parts.getOrElse(1) { "00" }} $ap"
}

// ───────────── toasts ─────────────
object Toasts {
    class Item(val id: Long, val msg: String)
    val items = mutableStateListOf<Item>()
    private val h = android.os.Handler(android.os.Looper.getMainLooper())
    fun show(msg: String) {
        val it = Item(System.nanoTime(), msg)
        items.add(it)
        h.postDelayed({ items.remove(it) }, 3700)
    }
}
fun toast(msg: String) = Toasts.show(msg)

@Composable
fun ToastHost() {
    val p = pal()
    Box(Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.padding(bottom = 26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Toasts.items.takeLast(4).forEach { t ->
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true; delay(3200); shown = false }
                val a by animateFloatAsState(if (shown) 1f else 0f, label = "toast")
                Box(
                    Modifier.graphicsLayer { alpha = a; translationY = (1 - a) * 30f }
                        .then(if (!p.lite) Modifier.glowBox(p.accent.copy(alpha = .35f), 999.dp, 18.dp) else Modifier)
                        .clip(RoundedCornerShape(50)).background(p.bg).border(1.dp, p.accent, RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) { T(t.msg, 12f, p.text, spacing = .05f) }
            }
        }
    }
}

// ───────────── sheets / dialogs ─────────────
class SheetBtn(val label: String, val primary: Boolean = false, val danger: Boolean = false, val onClick: (() -> Boolean?)? = null)

@Composable
fun Sheet(title: String, onClose: () -> Unit, buttons: List<SheetBtn> = listOf(SheetBtn("Close")), content: @Composable ColumnScope.() -> Unit) {
    val p = pal()
    val cfg = LocalConfiguration.current
    val wide = cfg.screenWidthDp > 760
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(2, 6, 12).copy(alpha = .74f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
            contentAlignment = if (wide) Alignment.Center else Alignment.BottomCenter
        ) {
            val shape = if (wide) RoundedCornerShape(p.radius) else RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
            Column(
                Modifier.then(if (wide) Modifier.width(680.dp) else Modifier.fillMaxWidth()).heightIn(max = (cfg.screenHeightDp * .9f).dp)
                    .clip(shape).background(p.bg).border(1.dp, p.accent, shape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .navigationBarsPadding().imePadding()
                    .padding(horizontal = 16.dp).padding(bottom = 16.dp)
            ) {
                if (!wide) Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(42.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.faint.copy(alpha = .6f)))
                } else Spacer(Modifier.height(18.dp))
                T(title, 14f, p.text, display = true, spacing = .1f, upper = true, modifier = Modifier.padding(bottom = 12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
                if (buttons.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    buttons.forEach { b ->
                        Btn(b.label, Modifier.weight(1f), primary = b.primary, danger = b.danger) { if (b.onClick?.invoke() != false) onClose() }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmSheet(title: String, message: String, okLabel: String, danger: Boolean, onResult: (Boolean) -> Unit) {
    Sheet(title, { onResult(false) }, listOf(SheetBtn("Cancel") { onResult(false); true }, SheetBtn(okLabel, primary = !danger, danger = danger) { onResult(true); true })) {
        T(message, 13f, pal().dim, lineH = 1.55f)
    }
}

class PickOpt(val label: String, val value: String, val sub: String? = null, val color: Color? = null)

@Composable
fun Pick(title: String, options: List<PickOpt>, value: String, onPick: (String) -> Unit, modifier: Modifier = Modifier, dense: Boolean = false) {
    val p = pal()
    var open by remember { mutableStateOf(false) }
    val cur = options.firstOrNull { it.value == value } ?: options.firstOrNull()
    FieldBox(modifier.clickable { open = true }, if (dense) 36.dp else 44.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(cur?.label ?: "", if (dense) 14f else 16f, p.text, modifier = Modifier.weight(1f), maxLines = 1)
            T("▾", 14f, p.dim)
        }
    }
    if (open) {
        var q by remember { mutableStateOf("") }
        Sheet(title, { open = false }, listOf(SheetBtn("Cancel"))) {
            if (options.size > 9) { Field(q, { q = it }, "Search…"); Spacer(Modifier.height(8.dp)) }
            val shown = options.filter { q.isBlank() || (it.label + " " + (it.sub ?: "")).lowercase().contains(q.trim().lowercase()) }
            if (shown.isEmpty()) T("No matches", 12f, p.faint, modifier = Modifier.fillMaxWidth().padding(18.dp), align = TextAlign.Center)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                shown.forEach { o ->
                    val on = o.value == value
                    val shape = RoundedCornerShape(10.dp)
                    Row(
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).clip(shape)
                            .background(if (on) p.accent.copy(alpha = .12f) else p.panel2).border(1.dp, if (on) p.accent else p.border, shape)
                            .clickable { Sfx.ui("tap"); open = false; onPick(o.value) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (o.color != null) Box(Modifier.size(12.dp).clip(CircleShape).background(o.color))
                        Column(Modifier.weight(1f)) {
                            T(o.label, 14f, p.text)
                            if (o.sub != null) T(o.sub, 11f, p.dim)
                        }
                        if (on) T("✓", 16f, p.accent, bold = true)
                    }
                }
            }
        }
    }
}

@Composable
fun Check(on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = pal(); val shape = RoundedCornerShape(4.dp)
    Box(
        modifier.size(19.dp).clip(shape).background(if (on) p.good else p.panel2).border(2.dp, if (on) p.good else p.faint, shape)
            .clickable { Sfx.ui("tap"); onClick() },
        contentAlignment = Alignment.Center
    ) { if (on) T("✓", 12f, Color(0xFF04120C), bold = true) }
}

@Composable
fun HexMark(size: Dp, fontSize: Float, modifier: Modifier = Modifier) {
    val p = pal()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width; val h = this.size.height
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * .5f, 0f); lineTo(w, h * .25f); lineTo(w, h * .75f); lineTo(w * .5f, h); lineTo(0f, h * .75f); lineTo(0f, h * .25f); close()
            }
            drawPath(path, p.accent)
        }
        T("DL", fontSize, p.ink, display = true, weight = 800)
    }
}
