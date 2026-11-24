package com.modelo3d.studio.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Punto 2D en las coordenadas (u, v) del plano del boceto. */
data class Pt(val u: Float, val v: Float)

sealed interface Shape
data class LineS(val a: Pt, val b: Pt, val fixed: Boolean = false) : Shape
/** Rectángulo: esquina inferior-izquierda (x, y) y tamaño (w, h). */
data class RectS(
    val x: Float, val y: Float, val w: Float, val h: Float,
    val fixW: Boolean = false, val fixH: Boolean = false
) : Shape
data class CircleS(val c: Pt, val r: Float, val fixed: Boolean = false) : Shape
/** Arco: inicio a, un punto intermedio m sobre el arco y final c. */
data class ArcS(val a: Pt, val m: Pt, val c: Pt) : Shape

enum class SketchTool(val label: String, val needed: Int) {
    Line("Línea", 2),
    RectCorner("Rectángulo por esquina", 2),
    RectCenter("Rectángulo por punto central", 2),
    Circle("Círculo por punto central", 2),
    Circle3("Círculo por 3 puntos", 3),
    Arc3("Arco por 3 puntos", 3),
    Dimension("Cota", 0)
}

private val TWO_PI = (2.0 * PI).toFloat()

fun dist(a: Pt, b: Pt): Float = hypot(a.u - b.u, a.v - b.v)

fun hintFor(tool: SketchTool?, pending: Int): String = when {
    tool == null -> "Abre Herramientas y elige una."
    tool == SketchTool.Dimension -> "Toca una cota o un elemento para poner o cambiar su medida."
    else -> "Toca o arrastra para colocar el punto ${pending + 1} de ${tool.needed}."
}

fun circumcircle(a: Pt, b: Pt, c: Pt): CircleS? {
    val d = 2f * (a.u * (b.v - c.v) + b.u * (c.v - a.v) + c.u * (a.v - b.v))
    if (abs(d) < 1e-6f) return null
    val a2 = a.u * a.u + a.v * a.v
    val b2 = b.u * b.u + b.v * b.v
    val c2 = c.u * c.u + c.v * c.v
    val ux = (a2 * (b.v - c.v) + b2 * (c.v - a.v) + c2 * (a.v - b.v)) / d
    val uy = (a2 * (c.u - b.u) + b2 * (a.u - c.u) + c2 * (b.u - a.u)) / d
    val cen = Pt(ux, uy)
    return CircleS(cen, dist(cen, a))
}

/** Crea la figura a partir de los puntos tocados (null si todavía no es válida). */
fun shapeFrom(tool: SketchTool, pts: List<Pt>): Shape? {
    when (tool) {
        SketchTool.Line ->
            if (pts.size >= 2 && dist(pts[0], pts[1]) > 1e-4f) return LineS(pts[0], pts[1])
        SketchTool.RectCorner ->
            if (pts.size >= 2) {
                val w = abs(pts[1].u - pts[0].u)
                val h = abs(pts[1].v - pts[0].v)
                if (w > 1e-4f && h > 1e-4f) {
                    return RectS(min(pts[0].u, pts[1].u), min(pts[0].v, pts[1].v), w, h)
                }
            }
        SketchTool.RectCenter ->
            if (pts.size >= 2) {
                val dx = abs(pts[1].u - pts[0].u)
                val dy = abs(pts[1].v - pts[0].v)
                if (dx > 1e-4f && dy > 1e-4f) {
                    return RectS(pts[0].u - dx, pts[0].v - dy, 2f * dx, 2f * dy)
                }
            }
        SketchTool.Circle ->
            if (pts.size >= 2) {
                val r = dist(pts[0], pts[1])
                if (r > 1e-4f) return CircleS(pts[0], r)
            }
        SketchTool.Circle3 ->
            if (pts.size >= 3) return circumcircle(pts[0], pts[1], pts[2])
        SketchTool.Arc3 ->
            if (pts.size >= 3 && circumcircle(pts[0], pts[2], pts[1]) != null) {
                return ArcS(pts[0], pts[2], pts[1])
            }
        SketchTool.Dimension -> {}
    }
    return null
}

fun previewShapes(tool: SketchTool?, pending: List<Pt>, cur: Pt?): List<Shape> {
    if (tool == null || tool == SketchTool.Dimension || cur == null || pending.isEmpty()) return emptyList()
    val pts = pending + cur
    val s = shapeFrom(tool, pts)
    if (s != null) return listOf(s)
    if (pts.size == 2 && tool.needed == 3 && dist(pts[0], pts[1]) > 1e-4f) return listOf(LineS(pts[0], pts[1]))
    return emptyList()
}

fun arcPoints(s: ArcS): List<Pt> {
    val cc = circumcircle(s.a, s.m, s.c) ?: return listOf(s.a, s.c)
    val a0 = atan2(s.a.v - cc.c.v, s.a.u - cc.c.u)
    val am = atan2(s.m.v - cc.c.v, s.m.u - cc.c.u)
    val a1 = atan2(s.c.v - cc.c.v, s.c.u - cc.c.u)
    fun norm(x: Float): Float {
        var y = x % TWO_PI
        if (y < 0f) y += TWO_PI
        return y
    }
    val ccw = norm(am - a0) < norm(a1 - a0)
    val sweep = if (ccw) norm(a1 - a0) else -norm(a0 - a1)
    val n = max(8, (abs(sweep) / (PI.toFloat() / 72f)).toInt())
    return (0..n).map { i ->
        val t = a0 + sweep * i / n
        Pt(cc.c.u + cc.r * cos(t), cc.c.v + cc.r * sin(t))
    }
}

/** Contorno 2D de una figura y si es cerrado. */
fun outline2D(s: Shape): Pair<List<Pt>, Boolean> = when (s) {
    is LineS -> listOf(s.a, s.b) to false
    is RectS -> listOf(
        Pt(s.x, s.y), Pt(s.x + s.w, s.y), Pt(s.x + s.w, s.y + s.h), Pt(s.x, s.y + s.h)
    ) to true
    is CircleS -> (0 until 192).map { i ->
        val t = TWO_PI * i / 192f
        Pt(s.c.u + s.r * cos(t), s.c.v + s.r * sin(t))
    } to true
    is ArcS -> arcPoints(s) to false
}

fun keyPoints(shapes: List<Shape>): List<Pt> = shapes.flatMap { s ->
    when (s) {
        is LineS -> listOf(s.a, s.b)
        is RectS -> listOf(
            Pt(s.x, s.y), Pt(s.x + s.w, s.y), Pt(s.x + s.w, s.y + s.h), Pt(s.x, s.y + s.h),
            Pt(s.x + s.w / 2f, s.y + s.h / 2f)
        )
        is CircleS -> listOf(
            s.c, Pt(s.c.u + s.r, s.c.v), Pt(s.c.u - s.r, s.c.v),
            Pt(s.c.u, s.c.v + s.r), Pt(s.c.u, s.c.v - s.r)
        )
        is ArcS -> listOf(s.a, s.c)
    }
}

fun gridStep(zoom: Float): Float {
    val target = 22f / zoom
    return listOf(0.1f, 0.25f, 0.5f, 1f, 2f, 5f, 10f, 25f, 50f, 100f).firstOrNull { it >= target } ?: 100f
}

/** Imán: puntos clave cercanos y, si no hay, la rejilla. */
fun snapPoint(raw: Pt, shapes: List<Shape>, extra: List<Pt>, zoom: Float): Pt {
    val tol = 18f / zoom
    var best: Pt? = null
    var bd = tol
    for (q in keyPoints(shapes) + extra + Pt(0f, 0f)) {
        val d = dist(raw, q)
        if (d < bd) { bd = d; best = q }
    }
    if (best != null) return best
    val g = gridStep(zoom)
    return Pt(Math.round(raw.u / g) * g, Math.round(raw.v / g) * g)
}

fun distToSeg(p: Pt, a: Pt, b: Pt): Float {
    val dx = b.u - a.u
    val dy = b.v - a.v
    val l2 = dx * dx + dy * dy
    if (l2 < 1e-12f) return dist(p, a)
    val t = (((p.u - a.u) * dx + (p.v - a.v) * dy) / l2).coerceIn(0f, 1f)
    return dist(p, Pt(a.u + t * dx, a.v + t * dy))
}

fun hitShape(shapes: List<Shape>, p: Pt, tol: Float): Int? {
    var best: Int? = null
    var bd = tol
    shapes.forEachIndexed { i, s ->
        val (pts, closed) = outline2D(s)
        val n = pts.size
        val segs = if (closed) n else n - 1
        for (k in 0 until segs) {
            val d = distToSeg(p, pts[k], pts[(k + 1) % n])
            if (d < bd) { bd = d; best = i }
        }
    }
    return best
}

fun signedArea(p: List<Pt>): Float {
    var s = 0f
    for (i in p.indices) {
        val q = p[(i + 1) % p.size]
        s += p[i].u * q.v - q.u * p[i].v
    }
    return s / 2f
}

/** Lazos cerrados del boceto (orientados en sentido antihorario). */
fun profiles(shapes: List<Shape>): List<List<Pt>> {
    val loops = ArrayList<List<Pt>>()
    val edges = ArrayList<List<Pt>>()
    for (s in shapes) {
        val (pts, closed) = outline2D(s)
        if (closed) loops.add(pts) else edges.add(pts)
    }
    val used = BooleanArray(edges.size)
    val tol = 1e-3f
    for (i in edges.indices) {
        if (used[i]) continue
        used[i] = true
        val chain = ArrayList<Pt>(edges[i])
        var closedLoop = false
        while (true) {
            if (chain.size >= 3 && dist(chain.first(), chain.last()) < tol) { closedLoop = true; break }
            var found = false
            for (j in edges.indices) {
                if (used[j]) continue
                val e = edges[j]
                if (dist(e.first(), chain.last()) < tol) {
                    chain.addAll(e.drop(1)); used[j] = true; found = true; break
                }
                if (dist(e.last(), chain.last()) < tol) {
                    chain.addAll(e.reversed().drop(1)); used[j] = true; found = true; break
                }
            }
            if (!found) break
        }
        if (closedLoop) {
            chain.removeAt(chain.size - 1)
            loops.add(chain)
        }
    }
    return loops
        .filter { it.size >= 3 && abs(signedArea(it)) > 1e-6f }
        .map { if (signedArea(it) < 0f) it.reversed() else it }
}

/** Triangulación por recorte de orejas (polígono simple antihorario). */
fun triangulate(p: List<Pt>): List<IntArray> {
    val n = p.size
    if (n < 3) return emptyList()
    val idx = ArrayList<Int>((0 until n).toList())
    val out = ArrayList<IntArray>()
    fun cross(a: Pt, b: Pt, c: Pt) = (b.u - a.u) * (c.v - a.v) - (b.v - a.v) * (c.u - a.u)
    fun inTri(t: Pt, a: Pt, b: Pt, c: Pt): Boolean =
        cross(a, b, t) >= 0f && cross(b, c, t) >= 0f && cross(c, a, t) >= 0f
    var convex = true
    for (i in 0 until n) {
        if (cross(p[i], p[(i + 1) % n], p[(i + 2) % n]) < -1e-9f) { convex = false; break }
    }
    if (convex) return (1 until n - 1).map { intArrayOf(0, it, it + 1) }
    var guard = 0
    while (idx.size > 3 && guard < 10000) {
        guard++
        var clipped = false
        for (k in idx.indices) {
            val i0 = idx[(k + idx.size - 1) % idx.size]
            val i1 = idx[k]
            val i2 = idx[(k + 1) % idx.size]
            val a = p[i0]; val b = p[i1]; val c = p[i2]
            if (cross(a, b, c) <= 1e-9f) continue
            var ear = true
            for (m in idx) {
                if (m == i0 || m == i1 || m == i2) continue
                if (inTri(p[m], a, b, c)) { ear = false; break }
            }
            if (ear) {
                out.add(intArrayOf(i0, i1, i2))
                idx.removeAt(k)
                clipped = true
                break
            }
        }
        if (!clipped) break
    }
    if (idx.size == 3) {
        out.add(intArrayOf(idx[0], idx[1], idx[2]))
    } else if (idx.size > 3) {
        for (k in 1 until idx.size - 1) out.add(intArrayOf(idx[0], idx[k], idx[k + 1]))
    }
    return out
}

/**
 * Mueve el punto [from] a [to] y arrastra los extremos de las demás líneas y arcos que
 * coincidían con él (como la restricción "Coincidente" de Onshape): el contorno se deforma
 * pero sigue cerrado.
 */
fun moveCoincident(shapes: List<Shape>, skip: Int, from: Pt, to: Pt, tol: Float = 1e-3f): List<Shape> =
    shapes.mapIndexed { i, s ->
        if (i == skip) s else when (s) {
            is LineS -> s.copy(
                a = if (dist(s.a, from) < tol) to else s.a,
                b = if (dist(s.b, from) < tol) to else s.b
            )
            is ArcS -> ArcS(
                if (dist(s.a, from) < tol) to else s.a,
                s.m,
                if (dist(s.c, from) < tol) to else s.c
            )
            else -> s
        }
    }

/** Resultado del imán: punto final, y puntos con los que está alineado (misma altura / misma vertical). */
class SnapResult(val pt: Pt, val point: Boolean, val hRef: Pt?, val vRef: Pt?) {
    val magnet: Boolean get() = hRef != null && vRef != null
}

/**
 * Primero imán a puntos clave, luego guías de nivel (misma altura o misma vertical que otro punto)
 * y por último la rejilla.
 */
fun snapInfo(raw: Pt, shapes: List<Shape>, extra: List<Pt>, zoom: Float): SnapResult {
    val tol = 18f / zoom
    val cands = keyPoints(shapes) + extra + Pt(0f, 0f)
    var bestP: Pt? = null
    var bd = tol
    for (q in cands) {
        val d = dist(raw, q)
        if (d < bd) { bd = d; bestP = q }
    }
    if (bestP != null) return SnapResult(bestP, true, bestP, bestP)

    val atol = 12f / zoom
    var hRef: Pt? = null
    var vRef: Pt? = null
    var hd = atol
    var vd = atol
    for (q in cands) {
        val dv = abs(raw.v - q.v)
        if (dv < hd) { hd = dv; hRef = q }
        val du = abs(raw.u - q.u)
        if (du < vd) { vd = du; vRef = q }
    }
    val g = gridStep(zoom)
    val u = vRef?.u ?: (Math.round(raw.u / g) * g)
    val v = hRef?.v ?: (Math.round(raw.v / g) * g)
    return SnapResult(Pt(u, v), false, hRef, vRef)
}

/** Texto de lectura mientras dibujas: coordenadas, medidas y si estás a nivel. */
fun readout(tool: SketchTool?, pending: List<Pt>, cur: Pt): String {
    val base = "X ${fmt(cur.u)} · Y ${fmt(cur.v)}"
    if (tool == null || pending.isEmpty()) return base
    val a = pending.last()
    return when (tool) {
        SketchTool.Line -> {
            var deg = Math.toDegrees(atan2((cur.v - a.v).toDouble(), (cur.u - a.u).toDouble())).toFloat()
            if (deg < 0f) deg += 360f
            val lvl = when {
                abs(cur.v - a.v) < 1e-4f -> " · Horizontal"
                abs(cur.u - a.u) < 1e-4f -> " · Vertical"
                else -> ""
            }
            "L ${fmt(dist(a, cur))} · ${fmt(deg)}°$lvl\n$base"
        }
        SketchTool.RectCorner, SketchTool.RectCenter ->
            "${fmt(abs(cur.u - pending[0].u))} × ${fmt(abs(cur.v - pending[0].v))}\n$base"
        SketchTool.Circle -> "R ${fmt(dist(pending[0], cur))}\n$base"
        else -> base
    }
}

/** Unidades de la calculadora de cotas. El modelo siempre trabaja en cm. */
enum class LengthUnit(val label: String, val toCm: Float) {
    Cm("cm", 1f), Mm("mm", 0.1f), M("m", 100f), In("in", 2.54f)
}

/** Una cota concreta: figura [index] y cual de sus medidas ([which]: 0 = ancho/longitud/radio, 1 = alto). */
class DimRef(val index: Int, val which: Int)

fun dimsOf(s: Shape): List<Int> = when (s) {
    is RectS -> listOf(0, 1)
    is ArcS -> emptyList()
    else -> listOf(0)
}

fun dimValue(s: Shape, which: Int): Float? = when (s) {
    is RectS -> if (which == 0) s.w else s.h
    is CircleS -> s.r
    is LineS -> dist(s.a, s.b)
    is ArcS -> null
}

/** Devuelve la figura con la medida cambiada (null si no es válida). */
fun withDim(s: Shape, which: Int, v: Float, moveStart: Boolean): Shape? {
    if (v <= 0f) return null
    return when (s) {
        is RectS -> if (which == 0) s.copy(w = v, fixW = true) else s.copy(h = v, fixH = true)
        is CircleS -> s.copy(r = v, fixed = true)
        is LineS -> {
            val len = dist(s.a, s.b)
            if (len < 1e-6f) null else {
                val k = v / len
                if (moveStart) {
                    s.copy(a = Pt(s.b.u + (s.a.u - s.b.u) * k, s.b.v + (s.a.v - s.b.v) * k), fixed = true)
                } else {
                    s.copy(b = Pt(s.a.u + (s.b.u - s.a.u) * k, s.a.v + (s.b.v - s.a.v) * k), fixed = true)
                }
            }
        }
        is ArcS -> null
    }
}

/** Posicion (en el plano) del texto de una cota; coincide con lo que dibuja shapeDims. */
fun dimAnchor(s: Shape, which: Int, zoom: Float): Pt? {
    val off = 40f / zoom
    return when (s) {
        is LineS -> {
            val len = dist(s.a, s.b)
            if (len < 1e-4f) null else Pt(
                (s.a.u + s.b.u) / 2f - (s.b.v - s.a.v) / len * off,
                (s.a.v + s.b.v) / 2f + (s.b.u - s.a.u) / len * off
            )
        }
        is RectS -> if (which == 0) Pt(s.x + s.w / 2f, s.y + s.h + off) else Pt(s.x + s.w + off, s.y + s.h / 2f)
        is CircleS -> Pt(s.c.u + s.r * 0.35f, s.c.v + s.r * 0.35f)
        is ArcS -> null
    }
}

/** Busca la cota tocada: primero por su texto y, si [edges], por el borde del elemento. */
fun hitDim(shapes: List<Shape>, p: Pt, zoom: Float, edges: Boolean): DimRef? {
    val tolL = 34f / zoom
    for ((i, s) in shapes.withIndex()) {
        for (w in dimsOf(s)) {
            val a = dimAnchor(s, w, zoom) ?: continue
            if (dist(a, p) < tolL) return DimRef(i, w)
        }
    }
    if (!edges) return null
    val i = hitShape(shapes, p, 24f / zoom) ?: return null
    return when (val s = shapes[i]) {
        is RectS -> {
            val dH = min(abs(p.v - s.y), abs(p.v - (s.y + s.h)))
            val dV = min(abs(p.u - s.x), abs(p.u - (s.x + s.w)))
            DimRef(i, if (dH <= dV) 0 else 1)
        }
        is ArcS -> null
        else -> DimRef(i, 0)
    }
}

fun isDimFixed(s: Shape, which: Int): Boolean = when (s) {
    is RectS -> if (which == 0) s.fixW else s.fixH
    is LineS -> s.fixed
    is CircleS -> s.fixed
    is ArcS -> false
}

fun setFixed(s: Shape, which: Int, f: Boolean): Shape = when (s) {
    is RectS -> if (which == 0) s.copy(fixW = f) else s.copy(fixH = f)
    is LineS -> s.copy(fixed = f)
    is CircleS -> s.copy(fixed = f)
    is ArcS -> s
}

/** True si el cambio alargó o acortó una línea de otra figura cuya cota está fija. */
fun violatesFixed(before: List<Shape>, after: List<Shape>, skip: Int): Boolean {
    for (i in before.indices) {
        if (i == skip || i >= after.size) continue
        val b = before[i]
        val a = after[i]
        if (b is LineS && a is LineS && b.fixed && abs(dist(b.a, b.b) - dist(a.a, a.b)) > 1e-3f) return true
    }
    return false
}
