package com.dl.app

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser

/** The 188 logo icons + 10 tab icons from the HTML, drawn natively from their SVG shapes. */
object Icons {
    class El(val kind: String, val a: Map<String, String>) { var path: Path? = null }
    class Ic(val cat: String, val name: String, val tags: String, val els: List<El>)

    var list: List<Ic> = emptyList()
    val map = HashMap<String, Ic>()

    fun init(ctx: Context) {
        if (list.isNotEmpty()) return
        val txt = ctx.assets.open("icons.json").bufferedReader().readText()
        val arr = JsonParser.parseString(txt).asJsonArray
        val elRe = Regex("<(circle|path|rect|ellipse)([^>]*)/>")
        val atRe = Regex("([a-z-]+)=\"([^\"]*)\"")
        list = arr.map { je ->
            val o = je.asJsonObject
            val els = elRe.findAll(o.get("svg").asString).map { m ->
                El(m.groupValues[1], atRe.findAll(m.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] })
            }.toList()
            Ic(o.get("cat").asString, o.get("name").asString, o.get("tags").asString, els)
        }
        list.forEach { map[it.name] = it }
    }

    val cats = listOf("Sports", "Fitness", "Money", "Study", "Tech", "Life", "Creative", "General")
}

fun DrawScope.drawIc(ic: Icons.Ic, color: Color, px: Float) {
    val k = px / 24f
    withTransform({ scale(k, k, Offset.Zero) }) {
        val stroke = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        ic.els.forEach { e ->
            val a = e.a
            val filled = a["fill"] == "currentColor"
            val style = if (filled) Fill else stroke
            fun f(n: String) = a[n]?.toFloatOrNull() ?: 0f
            when (e.kind) {
                "circle" -> drawCircle(color, f("r"), Offset(f("cx"), f("cy")), style = style)
                "path" -> {
                    val p = e.path ?: PathParser().parsePathString(a["d"] ?: "").toPath().also { e.path = it }
                    drawPath(p, color, style = style)
                }
                "rect" -> drawRoundRect(color, Offset(f("x"), f("y")), Size(f("width"), f("height")), CornerRadius(f("rx")), style)
                "ellipse" -> {
                    val cx = f("cx"); val cy = f("cy")
                    val tr = a["transform"]?.let { Regex("rotate\\(([-0-9.]+)").find(it)?.groupValues?.get(1)?.toFloatOrNull() } ?: 0f
                    rotate(tr, Offset(cx, cy)) {
                        drawOval(color, Offset(cx - f("rx"), cy - f("ry")), Size(f("rx") * 2, f("ry") * 2), style = style)
                    }
                }
            }
        }
    }
}

/** name is an icon id from the library, or "emoji:X" for an emoji logo. */
@Composable
fun Ico(name: String?, size: Dp = 16.dp, color: Color = pal().text) {
    if (name != null && name.startsWith("emoji:")) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) { T(name.removePrefix("emoji:"), (size.value * 0.9f), Color.Unspecified) }
        return
    }
    val ic = Icons.map[name ?: ""] ?: Icons.map["grid"]
    Canvas(Modifier.size(size)) { if (ic != null) drawIc(ic, color, this.size.width) }
}
