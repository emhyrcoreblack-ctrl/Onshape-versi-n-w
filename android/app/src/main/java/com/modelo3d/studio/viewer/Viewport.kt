package com.modelo3d.studio.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.modelo3d.studio.model.DimDraw
import com.modelo3d.studio.model.Poly3
import com.modelo3d.studio.model.Solid
import com.modelo3d.studio.model.V3
import com.modelo3d.studio.model.gridStep
import com.modelo3d.studio.model.planeLabel
import com.modelo3d.studio.model.planePoint
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private val L1 = V3(-0.35f, -0.55f, 0.75f).normalized()
private val L2 = V3(0.7f, 0.2f, 0.3f).normalized()
private val PLANE_NAMES = listOf("Top", "Front", "Right")

/** Modos de visualización de las piezas (menú del cubo de vistas). */
enum class ShadeMode { Shaded, NoEdges, HiddenEdges, Wire }

private class DrawItem(
    val depth: Float, val path: Path?, val fill: Color, val brush: Brush?,
    val p1: Offset, val p2: Offset, val outline: Color? = null
)

/** Envolvente convexa 2D (cadena monótona), para la sombra proyectada. */
private fun hull(pts: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    val p = pts.distinct().sortedWith(compareBy<Pair<Float, Float>>({ it.first }, { it.second }))
    if (p.size < 3) return p
    fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>) =
        (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
    val lo = ArrayList<Pair<Float, Float>>()
    for (q in p) {
        while (lo.size >= 2 && cross(lo[lo.size - 2], lo[lo.size - 1], q) <= 0f) lo.removeAt(lo.size - 1)
        lo.add(q)
    }
    val up = ArrayList<Pair<Float, Float>>()
    for (q in p.reversed()) {
        while (up.size >= 2 && cross(up[up.size - 2], up[up.size - 1], q) <= 0f) up.removeAt(up.size - 1)
        up.add(q)
    }
    lo.removeAt(lo.size - 1)
    up.removeAt(up.size - 1)
    return lo + up
}

@Composable
fun Viewport(
    camera: OrbitCamera,
    visible: Map<String, Boolean>,
    highlight: String?,
    planeSize: Float,
    gridHalf: Float,
    solids: List<Solid>,
    polys: List<Poly3>,
    activePolys: List<Poly3>,
    previewPolys: List<Poly3>,
    labels: List<Pair<V3, String>>,
    dims: List<DimDraw>,
    cursor: V3?,
    sketchPlane: String?,
    sketchInput: ((Int, Float, Float) -> Unit)?,
    sketchOrigin: V3,
    quadHalf: Float,
    sketchTitle: String?,
    touchOffsetDp: Float,
    cursorSnap: Boolean,
    shadeMode: ShadeMode,
    guideH: V3?,
    guideV: V3?,
    snapLabel: String?,
    selectedFace: Pair<String, Int>?,
    onTap: ((Offset) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val paint = remember {
        android.graphics.Paint().apply { isAntiAlias = true; textSize = 32f }
    }
    val sketchRef = rememberUpdatedState(sketchInput)
    val originRef = rememberUpdatedState(sketchOrigin)
    val tapRef = rememberUpdatedState(onTap)
    val density = LocalDensity.current.density
    val offsetRef = rememberUpdatedState(touchOffsetDp * density)
    val fingerState = remember { mutableStateOf<Offset?>(null) }
    val accent = Color(0xFF6FA3E8)
    val gold = Color(0xFFD9B26A)
    val steel = Color(0xFFC3C8D2)

    Canvas(
        modifier
            .background(Brush.verticalGradient(listOf(Color(0xFF111114), Color(0xFF050506))))
            .onSizeChanged { camera.viewSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown()
                    var last = first.position
                    var multi = false
                    var maxMove = 0f
                    val cxp = size.width / 2f
                    val cyp = size.height / 2f
                    fun report(phase: Int, pos: Offset) {
                        val f = sketchRef.value
                        if (f != null) {
                            fingerState.value = if (phase == 0) pos else null
                            val (u, v) = camera.toPlane(pos.x, pos.y - offsetRef.value, cxp, cyp, originRef.value)
                            f(phase, u, v)
                        }
                    }
                    report(0, last)
                    do {
                        val ev = awaitPointerEvent()
                        val down = ev.changes.filter { it.pressed }
                        if (down.size >= 2) multi = true
                        if (down.size == 1) {
                            last = down[0].position
                            maxMove = max(maxMove, hypot(last.x - first.position.x, last.y - first.position.y))
                            if (sketchRef.value != null && !multi) {
                                report(0, last)
                            } else {
                                val d = down[0].positionChange()
                                when (camera.navMode) {
                                    NavMode.Pan -> { camera.panX += d.x; camera.panY += d.y }
                                    NavMode.Zoom ->
                                        camera.zoom = (camera.zoom * (1f - d.y * 0.006f)).coerceIn(6f, 400f)
                                    else -> if (!camera.locked) {
                                        camera.yaw += d.x * 0.4f
                                        camera.pitch = (camera.pitch + d.y * 0.4f).coerceIn(-89f, 89f)
                                    } else {
                                        camera.panX += d.x; camera.panY += d.y
                                    }
                                }
                            }
                        } else if (down.size >= 2) {
                            camera.zoom = (camera.zoom * ev.calculateZoom()).coerceIn(6f, 400f)
                            val p = ev.calculatePan()
                            camera.panX += p.x; camera.panY += p.y
                        }
                        ev.changes.forEach { if (it.positionChanged()) it.consume() }
                    } while (ev.changes.any { it.pressed })
                    fingerState.value = null
                    if (!multi) {
                        report(1, last)
                        val tp = tapRef.value
                        if (tp != null && sketchRef.value == null && maxMove < 14f) tp(first.position)
                    }
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        fun pr(v: V3) = camera.project(v, cx, cy)

        // Texto con halo oscuro para que se lea sobre cualquier fondo
        fun label(text: String, at: Offset, color: Color) {
            val nc = drawContext.canvas.nativeCanvas
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 7f
            paint.color = 0xE6050506.toInt()
            nc.drawText(text, at.x + 8f, at.y - 8f, paint)
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = color.toArgb()
            nc.drawText(text, at.x + 8f, at.y - 8f, paint)
        }

        // Rejilla: sobre el plano del boceto, o sobre XY
        val minor = Color(0x14FFFFFF)
        val major = Color(0x2EFFFFFF)
        if (sketchPlane != null) {
            val gs = gridStep(camera.zoom)
            val n = (gridHalf / gs).toInt().coerceIn(2, 80)
            for (i in -n..n) {
                val a = i * gs
                val col = if (i % 5 == 0) major else minor
                drawLine(col, pr(planePoint(sketchPlane, a, -n * gs)).first, pr(planePoint(sketchPlane, a, n * gs)).first, 1f)
                drawLine(col, pr(planePoint(sketchPlane, -n * gs, a)).first, pr(planePoint(sketchPlane, n * gs, a)).first, 1f)
            }
        } else {
            val step = if (gridHalf > 12f) 5f else 1f
            val range = 10f * step
            for (i in -10..10) {
                val a = i * step
                val col = if (i % 5 == 0) major else minor
                drawLine(col, pr(V3(a, -range, 0f)).first, pr(V3(a, range, 0f)).first, 1f)
                drawLine(col, pr(V3(-range, a, 0f)).first, pr(V3(range, a, 0f)).first, 1f)
            }
        }

        // Sombra proyectada en el suelo
        for (solid in solids) {
            if (solid.verts.minOf { it.z } < -0.001f) continue
            val hp = hull(solid.verts.map { Pair(it.x + 0.47f * it.z, it.y + 0.73f * it.z) })
            if (hp.size >= 3) {
                val sp = Path()
                hp.forEachIndexed { i, q ->
                    val o = pr(V3(q.first, q.second, 0f)).first
                    if (i == 0) sp.moveTo(o.x, o.y) else sp.lineTo(o.x, o.y)
                }
                sp.close()
                drawPath(sp, Color(0x55000000), style = Fill)
            }
        }

        // Planos de referencia (de lejos a cerca)
        val s = planeSize
        val planes = PLANE_NAMES.filter { visible[it] != false }.map { name ->
            name to listOf(
                planePoint(name, -s, -s), planePoint(name, s, -s),
                planePoint(name, s, s), planePoint(name, -s, s)
            )
        }.sortedByDescending { (_, c) -> c.map { pr(it).second }.average() }
        for ((name, corners) in planes) {
            val pts = corners.map { pr(it).first }
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                close()
            }
            val sel = name == highlight
            drawPath(path, accent.copy(alpha = if (sel) 0.22f else 0.10f), style = Fill)
            drawPath(path, accent.copy(alpha = if (sel) 0.95f else 0.55f), style = Stroke(width = if (sel) 3.5f else 1.5f))
            label(planeLabel(name), pts[3], accent)
        }

        // Plano de boceto sobre la cara de una pieza
        if (sketchPlane != null && sketchPlane !in PLANE_NAMES) {
            val hs = quadHalf
            val corners = listOf(
                planePoint(sketchPlane, -hs, -hs), planePoint(sketchPlane, hs, -hs),
                planePoint(sketchPlane, hs, hs), planePoint(sketchPlane, -hs, hs)
            )
            val pts = corners.map { pr(it).first }
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                close()
            }
            drawPath(path, accent.copy(alpha = 0.12f), style = Fill)
            drawPath(path, accent.copy(alpha = 0.95f), style = Stroke(width = 3f))
            label(sketchTitle ?: "", pts[3], accent)
        }

        // Piezas: azul claro con brillo en las caras de frente, caras laterales oscuras y aristas negras finas
        val items = ArrayList<DrawItem>()
        val hiddenEdges = ArrayList<Pair<Offset, Offset>>()
        val edgeColor = if (shadeMode == ShadeMode.Wire) Color(0xFFDCE3EE) else Color(0xFF0B0E13)
        val lightBlue = Color(0xFF9DCBE6)
        val darkSlate = Color(0xFF4B5F6E)
        val glow = Color(0xFFEFFFFF)
        // Color de una superficie según su normal (se usa también en cada vértice de las superficies curvas)
        fun shade(n: V3): Color {
            val facing = (-camera.rotate(n).third).coerceIn(0f, 1f)
            val x = ((facing - 0.15f) / 0.7f).coerceIn(0f, 1f)
            val sm = x * x * (3f - 2f * x)
            val t = (0.85f * sm + 0.15f * max(0f, n.dot(L1))).coerceIn(0f, 1f)
            return lerp(darkSlate, lightBlue, t)
        }
        for (solid in solids) {
            val front = BooleanArray(solid.faces.size)
            val fdepth = FloatArray(solid.faces.size)
            for ((fi, f) in solid.faces.withIndex()) {
                var nrm = solid.normals[fi]
                var nd = camera.rotate(nrm).third
                var flip = 1f
                if (solid.twoSided && nd > 0f) {
                    nrm = -nrm
                    nd = -nd
                    flip = -1f
                }
                if (nd > -0.0001f) continue
                front[fi] = true
                val pts = f.map { pr(solid.verts[it]) }
                val path = Path().apply {
                    moveTo(pts[0].first.x, pts[0].first.y)
                    for (i in 1 until pts.size) lineTo(pts[i].first.x, pts[i].first.y)
                    close()
                }
                val facing = (-nd).coerceIn(0f, 1f)
                val x = ((facing - 0.15f) / 0.7f).coerceIn(0f, 1f)
                val smooth = x * x * (3f - 2f * x)
                val t = (0.85f * smooth + 0.15f * max(0f, nrm.dot(L1))).coerceIn(0f, 1f)
                val isSel = selectedFace != null && selectedFace.first == solid.name &&
                    (if (solid.faceGroup.isEmpty()) selectedFace.second == fi
                    else solid.faceGroup.getOrNull(selectedFace.second) == solid.faceGroup.getOrNull(fi))
                val base = if (isSel) Color(0xFF9DB3A2) else lerp(darkSlate, lightBlue, t)
                val minX = pts.minOf { it.first.x }
                val maxX = pts.maxOf { it.first.x }
                val minY = pts.minOf { it.first.y }
                val maxY = pts.maxOf { it.first.y }
                val w = maxX - minX
                val h = maxY - minY
                var brush: Brush? = null
                if (!isSel && smooth > 0.6f && w > 60f && h > 60f) {
                    brush = Brush.radialGradient(
                        colors = listOf(lerp(base, glow, 0.8f), base, lerp(base, darkSlate, 0.25f)),
                        center = Offset(minX + 0.28f * w, minY + 0.22f * h),
                        radius = max(w, h) * 0.95f
                    )
                }
                // Superficies curvas: degradado continuo entre facetas vecinas (sombreado suave)
                val sp = solid.sideNormals.getOrNull(fi)
                if (!isSel && sp != null) {
                    val nL = sp.first * flip
                    val nR = sp.second * flip
                    val e = pts[1].first - pts[0].first
                    val vdir = pts[3].first - pts[0].first
                    val vl = vdir.getDistance()
                    if (vl > 1f) {
                        val vh = vdir / vl
                        val proj = e.x * vh.x + e.y * vh.y
                        val g = Offset(e.x - proj * vh.x, e.y - proj * vh.y)
                        if (g.getDistance() > 1f) {
                            brush = Brush.linearGradient(
                                colors = listOf(shade(nL), shade(nR)),
                                start = pts[0].first,
                                end = pts[0].first + g
                            )
                        }
                    }
                }
                fdepth[fi] = pts.map { it.second }.average().toFloat()
                if (shadeMode != ShadeMode.Wire) {
                    items.add(DrawItem(fdepth[fi], path, base, brush, Offset.Zero, Offset.Zero, if (isSel) Color(0xFFE6B422) else null))
                }
            }
            for (e in solid.edges) {
                val f2 = e.f2
                val vis1 = front[e.f1]
                val vis2 = f2 >= 0 && front[f2]
                if (!vis1 && !vis2) {
                    if (shadeMode == ShadeMode.HiddenEdges || shadeMode == ShadeMode.Wire) {
                        hiddenEdges.add(pr(solid.verts[e.a]).first to pr(solid.verts[e.b]).first)
                    }
                    continue
                }
                if (shadeMode == ShadeMode.NoEdges) continue
                var key = Float.MAX_VALUE
                if (vis1) key = min(key, fdepth[e.f1])
                if (vis2) key = min(key, fdepth[f2])
                items.add(
                    DrawItem(
                        key - 0.002f, null, edgeColor, null,
                        pr(solid.verts[e.a]).first, pr(solid.verts[e.b]).first
                    )
                )
            }
        }
        items.sortByDescending { it.depth }
        for (item in items) {
            val pth = item.path
            if (pth != null) {
                val br = item.brush
                if (br != null) {
                    drawPath(pth, br, style = Fill)
                    drawPath(pth, br, style = Stroke(width = 1.5f))
                } else {
                    drawPath(pth, item.fill, style = Fill)
                    drawPath(pth, item.fill, style = Stroke(width = 1.5f))
                }
                val ol = item.outline
                if (ol != null) drawPath(pth, ol, style = Stroke(width = 2.5f))
            } else {
                drawLine(item.fill, item.p1, item.p2, 1.8f)
            }
        }
        val dashHidden = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        for ((p1, p2) in hiddenEdges) {
            drawLine(Color(0x99AAB4C0), p1, p2, 1.5f, pathEffect = dashHidden)
        }

        // Bocetos
        fun drawPoly(poly: Poly3, color: Color, width: Float) {
            val p = poly.pts.map { pr(it).first }
            if (p.size < 2) return
            val segs = if (poly.closed) p.size else p.size - 1
            val fx = poly.fixed
            for (i in 0 until segs) {
                val c = if (fx != null && i < fx.size && fx[i]) Color(0xFF4CD964) else color
                drawLine(c, p[i], p[(i + 1) % p.size], width)
            }
            if (poly.closed) {
                if (p.size <= 6) p.forEach { drawCircle(color, width * 1.4f, it) }
            } else {
                drawCircle(color, width * 1.4f, p.first())
                drawCircle(color, width * 1.4f, p.last())
            }
        }
        polys.forEach { drawPoly(it, gold.copy(alpha = 0.85f), 3f) }
        activePolys.forEach { drawPoly(it, gold, 4.5f) }
        previewPolys.forEach { drawPoly(it, Color.White.copy(alpha = 0.9f), 3f) }
        for ((pt, text) in labels) label(text, pr(pt).first, Color.White)

        // Cotas al estilo Onshape: lineas de extension, flechas y numero girado
        fun arrow(tip: Offset, outward: Offset, col: Color) {
            val l = outward.getDistance()
            if (l < 1e-3f) return
            val d = outward / l
            val base = tip - d * 18f
            val n = Offset(-d.y, d.x) * 6f
            val path = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(base.x + n.x, base.y + n.y)
                lineTo(base.x - n.x, base.y - n.y)
                close()
            }
            drawPath(path, col, style = Fill)
        }
        fun dimText(text: String, at: Offset, angle: Float, selected: Boolean) {
            val nc = drawContext.canvas.nativeCanvas
            val w = paint.measureText(text)
            nc.save()
            nc.rotate(angle, at.x, at.y)
            if (selected) {
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = 0xFFF0D080.toInt()
                nc.drawRoundRect(at.x - w / 2f - 8f, at.y - 26f, at.x + w / 2f + 8f, at.y + 12f, 8f, 8f, paint)
                paint.color = 0xFF1A1405.toInt()
                nc.drawText(text, at.x - w / 2f, at.y + 9f, paint)
            } else {
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = 7f
                paint.color = 0xE6050506.toInt()
                nc.drawText(text, at.x - w / 2f, at.y + 9f, paint)
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = 0xFFE6EAF0.toInt()
                nc.drawText(text, at.x - w / 2f, at.y + 9f, paint)
            }
            nc.restore()
        }
        for (d in dims) {
            val col = if (d.selected) Color(0xFFFFC107) else if (d.fixed) Color(0xFF4CD964) else Color(0xFFE6EAF0)
            val pa0 = pr(d.a).first
            val pb0 = pr(d.b).first
            if (d.radial) {
                drawLine(col, pa0, pb0, 2f)
                arrow(pb0, pb0 - pa0, col)
                dimText(d.text, Offset((pa0.x + pb0.x) / 2f, (pa0.y + pb0.y) / 2f - 16f), 0f, d.selected)
                continue
            }
            if (d.selected) drawLine(col, pa0, pb0, 5f)
            val off = 40f / camera.zoom
            val pa = pr(d.a + d.dir * off).first
            val pb = pr(d.b + d.dir * off).first
            val len = (pb - pa).getDistance()
            if (len < 2f) continue
            val u = (pb - pa) / len
            drawLine(col, pr(d.a + d.dir * (off * 0.12f)).first, pr(d.a + d.dir * (off * 1.2f)).first, 1.5f)
            drawLine(col, pr(d.b + d.dir * (off * 0.12f)).first, pr(d.b + d.dir * (off * 1.2f)).first, 1.5f)
            val tw = paint.measureText(d.text)
            val mid = Offset((pa.x + pb.x) / 2f, (pa.y + pb.y) / 2f)
            var ang = Math.toDegrees(atan2(u.y.toDouble(), u.x.toDouble())).toFloat()
            if (ang > 90f) ang -= 180f
            if (ang < -90f) ang += 180f
            if (len > tw + 70f) {
                val gap = tw / 2f + 14f
                drawLine(col, pa, mid - u * gap, 2f)
                drawLine(col, mid + u * gap, pb, 2f)
            } else {
                drawLine(col, pa, pb, 2f)
            }
            arrow(pa, pa - pb, col)
            arrow(pb, pb - pa, col)
            dimText(d.text, mid, ang, d.selected)
        }

        // Cursor de dibujo
        if (cursor != null) {
            val c = pr(cursor).first
            // Guías de nivel: misma altura / misma vertical que otro punto
            val dash = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
            val gcol = Color(0xFF6FC3FF)
            if (guideH != null) {
                drawLine(gcol.copy(alpha = 0.85f), Offset(0f, c.y), Offset(size.width, c.y), 2f, pathEffect = dash)
                drawCircle(gcol, 7f, pr(guideH).first)
            }
            if (guideV != null) {
                drawLine(gcol.copy(alpha = 0.85f), Offset(c.x, 0f), Offset(c.x, size.height), 2f, pathEffect = dash)
                drawCircle(gcol, 7f, pr(guideV).first)
            }
            val fp = fingerState.value
            if (fp != null && sketchInput != null) {
                drawLine(Color.White.copy(alpha = 0.35f), fp, c, 2f)
                drawCircle(Color.White.copy(alpha = 0.22f), 30f, fp)
            }
            if (cursorSnap) {
                drawCircle(gold, 30f, c, style = Stroke(width = 4f))
                drawCircle(gold, 6f, c)
            }
            if (snapLabel != null) label(snapLabel, Offset(c.x + 30f, c.y - 22f), gold)
            drawCircle(Color.White, 16f, c, style = Stroke(width = 2.5f))
            drawLine(Color.White, Offset(c.x - 26f, c.y), Offset(c.x + 26f, c.y), 2f)
            drawLine(Color.White, Offset(c.x, c.y - 26f), Offset(c.x, c.y + 26f), 2f)
        }

        // Ejes con letras
        if (visible["Origin"] != false) {
            val o = pr(V3(0f, 0f, 0f)).first
            val len = gridHalf
            val red = Color(0xFFFF5252)
            val green = Color(0xFF4CD964)
            val blue = Color(0xFF4DA3FF)
            drawLine(red.copy(alpha = 0.3f), o, pr(V3(-len, 0f, 0f)).first, 2f)
            drawLine(green.copy(alpha = 0.3f), o, pr(V3(0f, -len, 0f)).first, 2f)
            drawLine(blue.copy(alpha = 0.3f), o, pr(V3(0f, 0f, -len)).first, 2f)
            val ex = pr(V3(len, 0f, 0f)).first
            val ey = pr(V3(0f, len, 0f)).first
            val ez = pr(V3(0f, 0f, len)).first
            drawLine(red, o, ex, 4f); label("X", ex, red)
            drawLine(green, o, ey, 4f); label("Y", ey, green)
            drawLine(blue, o, ez, 4f); label("Z", ez, blue)
            drawCircle(Color.White, 7f, o)
        }
    }
}
