package com.modelo3d.studio.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/** Punto/vector 3D. Convención CAD: X derecha, Y profundidad, Z arriba. Unidades: cm. */
data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = V3(x * k, y * k, z * k)
    operator fun unaryMinus() = V3(-x, -y, -z)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(dot(this))
    fun normalized(): V3 {
        val l = length()
        return if (l < 1e-9f) this else this * (1f / l)
    }
}

val PLANES = listOf("Top", "Front", "Right")

fun planeLabel(name: String): String = when {
    name == "Top" -> "Superior (XY)"
    name == "Front" -> "Frontal (XZ)"
    name == "Right" -> "Derecho (YZ)"
    else -> "Cara de pieza"
}

/** Plano con origen o, ejes u y v (en el plano) y normal n. */
class PlaneDef(val o: V3, val u: V3, val v: V3, val n: V3)

private val planeCache = HashMap<String, PlaneDef>()

/** Marco de un plano a partir de su normal: u siempre horizontal, v "hacia arriba". */
fun frameFromNormal(o: V3, n0: V3): PlaneDef {
    val n = n0.normalized()
    val up = if (abs(n.z) < 0.9f) V3(0f, 0f, 1f) else V3(0f, 1f, 0f)
    val u = up.cross(n).normalized()
    val v = n.cross(u)
    return PlaneDef(o, u, v, n)
}

/** Clave de texto de un plano creado sobre la cara de una pieza. */
fun facePlaneKey(o: V3, n: V3): String =
    String.format(Locale.US, "F|%.6f,%.6f,%.6f|%.6f,%.6f,%.6f", o.x, o.y, o.z, n.x, n.y, n.z)

private fun parseV(s: String): V3 {
    val p = s.split(",")
    return V3(p[0].toFloat(), p[1].toFloat(), p[2].toFloat())
}

fun planeDef(plane: String): PlaneDef {
    planeCache[plane]?.let { return it }
    val zero = V3(0f, 0f, 0f)
    val d = when {
        plane == "Front" -> PlaneDef(zero, V3(1f, 0f, 0f), V3(0f, 0f, 1f), V3(0f, -1f, 0f))
        plane == "Right" -> PlaneDef(zero, V3(0f, 1f, 0f), V3(0f, 0f, 1f), V3(1f, 0f, 0f))
        plane.startsWith("F|") -> {
            val parts = plane.split("|")
            frameFromNormal(parseV(parts[1]), parseV(parts[2]))
        }
        else -> PlaneDef(zero, V3(1f, 0f, 0f), V3(0f, 1f, 0f), V3(0f, 0f, 1f))
    }
    planeCache[plane] = d
    return d
}

/** Punto 3D desde coordenadas (u,v) del plano y un desplazamiento n sobre su normal. */
fun planePoint(plane: String, u: Float, v: Float, n: Float = 0f): V3 {
    val d = planeDef(plane)
    return d.o + d.u * u + d.v * v + d.n * n
}

/** Vector (sin origen) en las coordenadas del plano. */
fun planeVec(plane: String, u: Float, v: Float, n: Float = 0f): V3 {
    val d = planeDef(plane)
    return d.u * u + d.v * v + d.n * n
}

/** Línea o lazo 3D (contorno de un boceto). */
class Poly3(val pts: List<V3>, val closed: Boolean, val fixed: List<Boolean>? = null)

fun shapePolys(plane: String, shapes: List<Shape>): List<Poly3> = shapes.map { s ->
    val (pts, closed) = outline2D(s)
    val fixed: List<Boolean>? = when (s) {
        is LineS -> listOf(s.fixed)
        is RectS -> listOf(s.fixW, s.fixH, s.fixW, s.fixH)
        is CircleS -> if (s.fixed) List(pts.size) { true } else null
        is ArcS -> null
    }
    Poly3(pts.map { planePoint(plane, it.u, it.v, 0f) }, closed, fixed)
}

/** Cotas que se muestran junto a las figuras. */
fun shapeLabels(plane: String, shapes: List<Shape>): List<Pair<V3, String>> {
    val out = ArrayList<Pair<V3, String>>()
    for (s in shapes) {
        when (s) {
            is LineS -> out.add(
                planePoint(plane, (s.a.u + s.b.u) / 2f, (s.a.v + s.b.v) / 2f, 0f) to fmt(dist(s.a, s.b))
            )
            is RectS -> {
                out.add(planePoint(plane, s.x + s.w / 2f, s.y, 0f) to fmt(s.w))
                out.add(planePoint(plane, s.x + s.w, s.y + s.h / 2f, 0f) to fmt(s.h))
            }
            is CircleS -> out.add(planePoint(plane, s.c.u + s.r, s.c.v, 0f) to ("R" + fmt(s.r)))
            is ArcS -> {}
        }
    }
    return out
}

/** Arista real del sólido: entre dos vértices y las caras que une (f2 = -1 si solo toca una). */
class Edge(val a: Int, val b: Int, val f1: Int, val f2: Int)

/**
 * Pieza de caras planas: malla, normales hacia afuera, triángulos, aristas reales y propiedades.
 * [sideNormals]: por cara, normales suavizadas (izquierda, derecha) en superficies curvas.
 */
class Solid(
    val name: String,
    val verts: List<V3>,
    val faces: List<IntArray>,
    val normals: List<V3>,
    val tris: List<IntArray>,
    val edges: List<Edge>,
    val selectable: List<Boolean>,
    val volume: Float,
    val area: Float,
    val opId: String = "",
    val sideNormals: List<Pair<V3, V3>?> = emptyList(),
    val twoSided: Boolean = false,
    val faceGroup: List<Int> = emptyList()
)

/** Desplaza un polígono antihorario hacia adentro la distancia d (hacia afuera si d < 0). */
fun offsetPolygon(poly: List<Pt>, d: Float): List<Pt> {
    if (abs(d) < 1e-6f) return poly
    val n = poly.size
    return List(n) { i ->
        val p0 = poly[(i + n - 1) % n]
        val p1 = poly[i]
        val p2 = poly[(i + 1) % n]
        val e1u = p1.u - p0.u
        val e1v = p1.v - p0.v
        val l1 = hypot(e1u, e1v)
        val e2u = p2.u - p1.u
        val e2v = p2.v - p1.v
        val l2 = hypot(e2u, e2v)
        if (l1 < 1e-9f || l2 < 1e-9f) p1 else {
            val n1u = -e1v / l1
            val n1v = e1u / l1
            val n2u = -e2v / l2
            val n2v = e2u / l2
            val k = d / max(0.2f, 1f + n1u * n2u + n1v * n2v)
            Pt(p1.u + (n1u + n2u) * k, p1.v + (n1v + n2v) * k)
        }
    }
}

/** Normales suavizadas por lado: null si la cara es plana entre esquinas marcadas. */
private fun sidePairs(sn: List<V3>): List<Pair<V3, V3>?> {
    val n = sn.size
    return List(n) { i ->
        val cur = sn[i]
        val prev = sn[(i + n - 1) % n]
        val next = sn[(i + 1) % n]
        val sl = cur.dot(prev) >= 0.866f
        val sr = cur.dot(next) >= 0.866f
        if (!sl && !sr) null else Pair(
            if (sl) (cur + prev).normalized() else cur,
            if (sr) (cur + next).normalized() else cur
        )
    }
}

private class MeshBuilder {
    val verts = ArrayList<V3>()
    val faces = ArrayList<IntArray>()
    val normals = ArrayList<V3>()
    val tris = ArrayList<IntArray>()

    fun addTri(a: Int, b: Int, c: Int, out: V3) {
        val t = (verts[b] - verts[a]).cross(verts[c] - verts[a])
        if (t.dot(out) < 0f) tris.add(intArrayOf(a, c, b)) else tris.add(intArrayOf(a, b, c))
    }

    /** Cara convexa (cuadrilátero): triangulación en abanico. */
    fun addFace(idx: IntArray, out: V3) {
        faces.add(idx)
        normals.add(out)
        for (k in 1 until idx.size - 1) addTri(idx[0], idx[k], idx[k + 1], out)
    }

    /** Tapa con forma de polígono cualquiera: se triangula con el contorno 2D. */
    fun capFace(idx: IntArray, poly: List<Pt>, out: V3) {
        faces.add(idx)
        normals.add(out)
        for (t in triangulate(poly)) addTri(idx[t[0]], idx[t[1]], idx[t[2]], out)
    }
}

private fun meshVolume(verts: List<V3>, tris: List<IntArray>): Float {
    var v = 0f
    for (t in tris) v += verts[t[0]].dot(verts[t[1]].cross(verts[t[2]])) / 6f
    return abs(v)
}

private fun meshArea(verts: List<V3>, tris: List<IntArray>): Float {
    var a = 0f
    for (t in tris) a += 0.5f * (verts[t[1]] - verts[t[0]]).cross(verts[t[2]] - verts[t[0]]).length()
    return a
}

/**
 * Extruye un perfil cerrado (antihorario en u,v) según las opciones de la extrusión:
 * sólido, superficie o delgado, dirección opuesta, simétrico, desfase inicial,
 * desmoldeo y segunda posición final.
 */
fun buildExtrusion(plane: String, poly: List<Pt>, e: ExtrudeOp, name: String): Solid? {
    val n = poly.size
    if (n < 3 || e.depth <= 0f) return null
    val dir = if (e.reverse) -1f else 1f
    val t0 = if (e.offOn) dir * e.startOffset else 0f
    val tA: Float
    val tB: Float
    if (e.symmetric) {
        tA = t0 - e.depth / 2f
        tB = t0 + e.depth / 2f
    } else {
        tB = t0 + dir * e.depth
        tA = if (e.secOn) t0 - dir * e.second else t0
    }
    val tMin = min(tA, tB)
    val tMax = max(tA, tB)
    if (tMax - tMin < 1e-5f) return null
    val nUp = planeVec(plane, 0f, 0f, 1f)
    val mb = MeshBuilder()
    val edges = ArrayList<Edge>()
    // Dirección personalizada: se inclina la extrusión hacia un eje del mundo
    val nW = planeVec(plane, 0f, 0f, 1f)
    val axis = when (e.dirAxis) {
        1 -> V3(1f, 0f, 0f)
        2 -> V3(0f, 1f, 0f)
        3 -> V3(0f, 0f, 1f)
        else -> null
    }
    var shear = V3(0f, 0f, 0f)
    if (axis != null && abs(axis.dot(nW)) > 0.05f) {
        val a = if (axis.dot(nW) > 0f) axis else -axis
        val c = a.dot(nW)
        shear = (a - nW * c) * (1f / c)
    }
    fun p3(p: Pt, t: Float) = planePoint(plane, p.u, p.v, t) + shear * t

    if (e.kind == ExtrudeKind.Thin) {
        val th = max(e.thickness, 0.001f)
        val outer: List<Pt>
        val inner: List<Pt>
        if (!e.thinOut) {
            outer = poly
            inner = offsetPolygon(poly, th)
        } else {
            inner = poly
            outer = offsetPolygon(poly, -th)
        }
        for (p in outer) mb.verts.add(p3(p, tMin))
        for (p in outer) mb.verts.add(p3(p, tMax))
        for (p in inner) mb.verts.add(p3(p, tMin))
        for (p in inner) mb.verts.add(p3(p, tMax))
        val outN = ArrayList<V3>()
        for (i in 0 until n) {
            val j = (i + 1) % n
            val du = outer[j].u - outer[i].u
            val dv = outer[j].v - outer[i].v
            val len = hypot(du, dv)
            outN.add(if (len < 1e-9f) nUp else planeVec(plane, dv / len, -du / len, 0f))
        }
        for (i in 0 until n) {
            val j = (i + 1) % n
            mb.addFace(intArrayOf(i, j, 2 * n + j, 2 * n + i), -nUp)
        }
        for (i in 0 until n) {
            val j = (i + 1) % n
            mb.addFace(intArrayOf(n + i, n + j, 3 * n + j, 3 * n + i), nUp)
        }
        for (i in 0 until n) {
            val j = (i + 1) % n
            mb.addFace(intArrayOf(i, j, n + j, n + i), outN[i])
        }
        for (i in 0 until n) {
            val j = (i + 1) % n
            mb.addFace(intArrayOf(2 * n + i, 2 * n + j, 3 * n + j, 3 * n + i), -outN[i])
        }
        for (i in 0 until n) {
            val j = (i + 1) % n
            val prev = (i + n - 1) % n
            edges.add(Edge(i, j, i, 2 * n + i))
            edges.add(Edge(n + i, n + j, n + i, 2 * n + i))
            edges.add(Edge(2 * n + i, 2 * n + j, i, 3 * n + i))
            edges.add(Edge(3 * n + i, 3 * n + j, n + i, 3 * n + i))
            if (outN[prev].dot(outN[i]) < 0.866f) {
                edges.add(Edge(i, n + i, 2 * n + prev, 2 * n + i))
                edges.add(Edge(2 * n + i, 3 * n + i, 3 * n + prev, 3 * n + i))
            }
        }
        val sp = ArrayList<Pair<V3, V3>?>()
        repeat(2 * n) { sp.add(null) }
        sp.addAll(sidePairs(outN))
        sp.addAll(sidePairs(outN.map { -it }))
        return Solid(
            name, mb.verts, mb.faces, mb.normals, mb.tris, edges, List(4 * n) { false },
            meshVolume(mb.verts, mb.tris), meshArea(mb.verts, mb.tris), e.id, sp, false
        )
    }

    // Sólido o superficie (con desmoldeo opcional)
    val surface = e.kind == ExtrudeKind.Surface
    val tanA = if (e.dfOn) tan(Math.toRadians(e.draftDeg.coerceIn(0f, 60f).toDouble())).toFloat() else 0f
    val polyB = offsetPolygon(poly, max(0f, t0 - tMin) * tanA)
    val polyT = offsetPolygon(poly, max(0f, tMax - t0) * tanA)
    for (p in polyB) mb.verts.add(p3(p, tMin))
    for (p in polyT) mb.verts.add(p3(p, tMax))
    val sn = ArrayList<V3>()
    for (i in 0 until n) {
        val j = (i + 1) % n
        val edge = mb.verts[j] - mb.verts[i]
        val slant = mb.verts[n + i] - mb.verts[i]
        var nr = edge.cross(slant).normalized()
        val du = poly[j].u - poly[i].u
        val dv = poly[j].v - poly[i].v
        if (nr.dot(planeVec(plane, dv, -du, 0f)) < 0f) nr = -nr
        sn.add(nr)
    }
    if (!surface) {
        mb.capFace(IntArray(n) { it }, polyB, -nUp)
        mb.capFace(IntArray(n) { n + it }, polyT, nUp)
    }
    val base = mb.faces.size
    for (i in 0 until n) {
        val j = (i + 1) % n
        mb.addFace(intArrayOf(i, j, n + j, n + i), sn[i])
    }
    val sharp = BooleanArray(n) { i -> sn[(i + n - 1) % n].dot(sn[i]) < 0.866f }
    for (i in 0 until n) {
        val j = (i + 1) % n
        val prev = (i + n - 1) % n
        if (surface) {
            edges.add(Edge(i, j, base + i, -1))
            edges.add(Edge(n + i, n + j, base + i, -1))
        } else {
            edges.add(Edge(i, j, 0, base + i))
            edges.add(Edge(n + i, n + j, 1, base + i))
        }
        if (sharp[i]) edges.add(Edge(i, n + i, base + prev, base + i))
    }
    val selectable = ArrayList<Boolean>()
    if (!surface) {
        selectable.add(true)
        selectable.add(true)
    }
    for (i in 0 until n) selectable.add(!surface && sharp[i] && sharp[(i + 1) % n])
    val sp = ArrayList<Pair<V3, V3>?>()
    if (!surface) {
        sp.add(null)
        sp.add(null)
    }
    sp.addAll(sidePairs(sn))
    return Solid(
        name, mb.verts, mb.faces, mb.normals, mb.tris, edges, selectable,
        if (surface) 0f else meshVolume(mb.verts, mb.tris), meshArea(mb.verts, mb.tris),
        e.id, sp, surface
    )
}

fun sceneExtent(solids: List<Solid>, outlines: List<List<V3>>): Float {
    var m = 0f
    fun see(p: V3) { m = max(m, max(abs(p.x), max(abs(p.y), abs(p.z)))) }
    solids.forEach { s -> s.verts.forEach { see(it) } }
    outlines.forEach { o -> o.forEach { see(it) } }
    return m
}

/** Formato corto: 20 -> "20", 4.85 -> "4.85". */
fun fmt(f: Float): String =
    if (abs(f - Math.round(f)) < 1e-4f) Math.round(f).toString()
    else String.format(Locale.US, "%.3f", f).trimEnd('0').trimEnd('.')

private fun num(v: Float) = String.format(Locale.US, "%.5f", v)

/** STL ASCII en milímetros (el modelo está en cm). */
fun exportStl(solids: List<Solid>): String {
    val sb = StringBuilder()
    for (s in solids) {
        val nm = s.name.replace(' ', '_')
        sb.append("solid ").append(nm).append('\n')
        for (t in s.tris) {
            val a = s.verts[t[0]]
            val b = s.verts[t[1]]
            val c = s.verts[t[2]]
            val n = (b - a).cross(c - a).normalized()
            sb.append(" facet normal ${num(n.x)} ${num(n.y)} ${num(n.z)}\n  outer loop\n")
            for (p in listOf(a, b, c)) {
                sb.append("   vertex ${num(p.x * 10f)} ${num(p.y * 10f)} ${num(p.z * 10f)}\n")
            }
            sb.append("  endloop\n endfacet\n")
        }
        sb.append("endsolid ").append(nm).append('\n')
    }
    return sb.toString()
}

/** Dimensiones de la caja envolvente de todas las piezas (cm). */
fun sizeOf(solids: List<Solid>): V3? {
    if (solids.isEmpty()) return null
    val all = solids.flatMap { it.verts }
    return V3(
        all.maxOf { it.x } - all.minOf { it.x },
        all.maxOf { it.y } - all.minOf { it.y },
        all.maxOf { it.z } - all.minOf { it.z }
    )
}

fun volumeOf(s: Solid): Float = s.volume

fun areaOf(s: Solid): Float = s.area

/** Cota lista para dibujar: puntos medidos a-b, direccion hacia donde se separa y su texto. */
class DimDraw(
    val a: V3, val b: V3, val dir: V3, val text: String, val selected: Boolean, val radial: Boolean,
    val fixed: Boolean = false
)

fun shapeDims(plane: String, shapes: List<Shape>, sel: DimRef?): List<DimDraw> {
    val out = ArrayList<DimDraw>()
    fun p3(p: Pt) = planePoint(plane, p.u, p.v, 0f)
    shapes.forEachIndexed { i, s ->
        fun isSel(w: Int) = sel != null && sel.index == i && sel.which == w
        when (s) {
            is LineS -> {
                val len = dist(s.a, s.b)
                if (len > 1e-4f) {
                    val nu = -(s.b.v - s.a.v) / len
                    val nv = (s.b.u - s.a.u) / len
                    out.add(DimDraw(p3(s.a), p3(s.b), planeVec(plane, nu, nv), fmt(len), isSel(0), false, s.fixed))
                }
            }
            is RectS -> {
                out.add(
                    DimDraw(
                        p3(Pt(s.x, s.y + s.h)), p3(Pt(s.x + s.w, s.y + s.h)),
                        planeVec(plane, 0f, 1f), fmt(s.w), isSel(0), false, s.fixW
                    )
                )
                out.add(
                    DimDraw(
                        p3(Pt(s.x + s.w, s.y)), p3(Pt(s.x + s.w, s.y + s.h)),
                        planeVec(plane, 1f, 0f), fmt(s.h), isSel(1), false, s.fixH
                    )
                )
            }
            is CircleS -> {
                val q = Pt(s.c.u + s.r * 0.7071f, s.c.v + s.r * 0.7071f)
                out.add(DimDraw(p3(s.c), p3(q), V3(0f, 0f, 0f), "R" + fmt(s.r), isSel(0), true, s.fixed))
            }
            is ArcS -> {}
        }
    }
    return out
}
