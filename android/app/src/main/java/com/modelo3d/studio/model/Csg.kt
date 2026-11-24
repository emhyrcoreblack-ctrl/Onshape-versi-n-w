package com.modelo3d.studio.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Vector en doble precisión para las operaciones booleanas. */
class D3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: D3) = D3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: D3) = D3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = D3(x * k, y * k, z * k)
    fun dot(o: D3) = x * o.x + y * o.y + z * o.z
    fun cross(o: D3) = D3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun len() = sqrt(dot(this))
    fun unit(): D3 {
        val l = len()
        return if (l < 1e-12) this else this * (1.0 / l)
    }
}

private const val EPS = 1e-5
private const val COPLANAR = 0
private const val FRONT = 1
private const val BACK = 2
private const val SPANNING = 3

class CPlane(val n: D3, val w: Double) {
    fun flipped() = CPlane(n * -1.0, -w)

    /** Reparte un polígono respecto a este plano (algoritmo BSP clásico). */
    fun split(
        poly: CPoly,
        coplanarFront: MutableList<CPoly>, coplanarBack: MutableList<CPoly>,
        front: MutableList<CPoly>, back: MutableList<CPoly>
    ) {
        var polyType = 0
        val types = IntArray(poly.v.size)
        for (i in poly.v.indices) {
            val t = n.dot(poly.v[i]) - w
            val ty = if (t < -EPS) BACK else if (t > EPS) FRONT else COPLANAR
            polyType = polyType or ty
            types[i] = ty
        }
        when (polyType) {
            COPLANAR -> if (n.dot(poly.plane.n) > 0.0) coplanarFront.add(poly) else coplanarBack.add(poly)
            FRONT -> front.add(poly)
            BACK -> back.add(poly)
            else -> {
                val fv = ArrayList<D3>()
                val bv = ArrayList<D3>()
                for (i in poly.v.indices) {
                    val j = (i + 1) % poly.v.size
                    val ti = types[i]
                    val tj = types[j]
                    val vi = poly.v[i]
                    val vj = poly.v[j]
                    if (ti != BACK) fv.add(vi)
                    if (ti != FRONT) bv.add(vi)
                    if ((ti or tj) == SPANNING) {
                        val t = (w - n.dot(vi)) / n.dot(vj - vi)
                        val v = vi + (vj - vi) * t
                        fv.add(v)
                        bv.add(v)
                    }
                }
                if (fv.size >= 3) front.add(CPoly(fv, poly.plane))
                if (bv.size >= 3) back.add(CPoly(bv, poly.plane))
            }
        }
    }
}

class CPoly(val v: List<D3>, val plane: CPlane) {
    fun flipped() = CPoly(v.reversed(), plane.flipped())

    companion object {
        fun of(v: List<D3>): CPoly? {
            if (v.size < 3) return null
            var nx = 0.0
            var ny = 0.0
            var nz = 0.0
            for (i in v.indices) {
                val a = v[i]
                val b = v[(i + 1) % v.size]
                nx += (a.y - b.y) * (a.z + b.z)
                ny += (a.z - b.z) * (a.x + b.x)
                nz += (a.x - b.x) * (a.y + b.y)
            }
            val l = sqrt(nx * nx + ny * ny + nz * nz)
            if (l < 1e-10) return null
            val n = D3(nx / l, ny / l, nz / l)
            return CPoly(v, CPlane(n, n.dot(v[0])))
        }
    }
}

/** Nodo del árbol BSP. */
class CNode(polys: List<CPoly>? = null) {
    private var plane: CPlane? = null
    private var front: CNode? = null
    private var back: CNode? = null
    private val polygons = ArrayList<CPoly>()

    init {
        if (polys != null && polys.isNotEmpty()) build(polys)
    }

    fun invert() {
        for (i in polygons.indices) polygons[i] = polygons[i].flipped()
        plane = plane?.flipped()
        front?.invert()
        back?.invert()
        val t = front
        front = back
        back = t
    }

    fun clipPolygons(polys: List<CPoly>): List<CPoly> {
        val pl = plane ?: return ArrayList(polys)
        var f: List<CPoly> = ArrayList()
        var b: List<CPoly> = ArrayList()
        val fl = ArrayList<CPoly>()
        val bl = ArrayList<CPoly>()
        for (p in polys) pl.split(p, fl, bl, fl, bl)
        f = fl
        b = bl
        val ff = front
        val bb = back
        if (ff != null) f = ff.clipPolygons(f)
        val bres: List<CPoly> = if (bb != null) bb.clipPolygons(b) else emptyList()
        return f + bres
    }

    fun clipTo(bsp: CNode) {
        val kept = bsp.clipPolygons(polygons)
        polygons.clear()
        polygons.addAll(kept)
        front?.clipTo(bsp)
        back?.clipTo(bsp)
    }

    fun allPolygons(): List<CPoly> {
        val res = ArrayList<CPoly>(polygons)
        front?.let { res.addAll(it.allPolygons()) }
        back?.let { res.addAll(it.allPolygons()) }
        return res
    }

    fun build(polys: List<CPoly>) {
        if (polys.isEmpty()) return
        if (plane == null) plane = polys[0].plane
        val pl = plane ?: return
        val f = ArrayList<CPoly>()
        val b = ArrayList<CPoly>()
        for (p in polys) pl.split(p, polygons, polygons, f, b)
        if (f.isNotEmpty()) {
            val nf = front ?: CNode().also { front = it }
            nf.build(f)
        }
        if (b.isNotEmpty()) {
            val nb = back ?: CNode().also { back = it }
            nb.build(b)
        }
    }
}

object Csg {
    fun union(a: List<CPoly>, b: List<CPoly>): List<CPoly> {
        val na = CNode(a)
        val nb = CNode(b)
        na.clipTo(nb)
        nb.clipTo(na)
        nb.invert()
        nb.clipTo(na)
        nb.invert()
        na.build(nb.allPolygons())
        return na.allPolygons()
    }

    fun subtract(a: List<CPoly>, b: List<CPoly>): List<CPoly> {
        val na = CNode(a)
        val nb = CNode(b)
        na.invert()
        na.clipTo(nb)
        nb.clipTo(na)
        nb.invert()
        nb.clipTo(na)
        nb.invert()
        na.build(nb.allPolygons())
        na.invert()
        return na.allPolygons()
    }

    fun intersect(a: List<CPoly>, b: List<CPoly>): List<CPoly> {
        val na = CNode(a)
        val nb = CNode(b)
        na.invert()
        nb.clipTo(na)
        nb.invert()
        na.clipTo(nb)
        nb.clipTo(na)
        na.build(nb.allPolygons())
        na.invert()
        return na.allPolygons()
    }
}

// ------------------------------------------------------------------
// Conversión entre piezas (Solid) y polígonos convexos
// ------------------------------------------------------------------

private fun d3(v: V3) = D3(v.x.toDouble(), v.y.toDouble(), v.z.toDouble())

private fun isConvex(p: CPoly): Boolean {
    val n = p.v.size
    for (i in 0 until n) {
        val a = p.v[i]
        val b = p.v[(i + 1) % n]
        val c = p.v[(i + 2) % n]
        if ((b - a).cross(c - b).dot(p.plane.n) < -1e-9) return false
    }
    return true
}

/** Divide un polígono cóncavo en triángulos (recorte de orejas en su propio plano). */
private fun splitConcave(p: CPoly): List<CPoly> {
    val n = p.plane.n
    val ref = if (abs(n.x) < 0.9) D3(1.0, 0.0, 0.0) else D3(0.0, 1.0, 0.0)
    val ux = ref.cross(n).unit()
    val vx = n.cross(ux)
    val pts2 = p.v.map { Pt(it.dot(ux).toFloat(), it.dot(vx).toFloat()) }
    val rev = signedArea(pts2) < 0f
    val poly2 = if (rev) pts2.reversed() else pts2
    val pv = if (rev) p.v.reversed() else p.v
    val out = ArrayList<CPoly>()
    for (t in triangulate(poly2)) {
        val c = CPoly.of(listOf(pv[t[0]], pv[t[1]], pv[t[2]])) ?: continue
        out.add(if (c.plane.n.dot(n) < 0.0) c.flipped() else c)
    }
    return out
}

fun polysOfSolid(s: Solid): List<CPoly> {
    val out = ArrayList<CPoly>()
    for ((fi, f) in s.faces.withIndex()) {
        val no = d3(s.normals[fi])
        var poly = CPoly.of(f.map { d3(s.verts[it]) }) ?: continue
        if (poly.plane.n.dot(no) < 0.0) poly = poly.flipped()
        if (isConvex(poly)) out.add(poly) else out.addAll(splitConcave(poly))
    }
    return out
}

/** Pieza del modelo: una pieza prismática original o el resultado de una operación booleana. */
class Body(var solid: Solid?, var polys: List<CPoly>?, val opId: String) {
    private var cachedBox: DoubleArray? = null

    fun cpolys(): List<CPoly> {
        val p = polys
        if (p != null) return p
        val s = solid ?: return emptyList()
        val q = polysOfSolid(s)
        polys = q
        return q
    }

    fun box(): DoubleArray {
        val c = cachedBox
        if (c != null) return c
        val b = doubleArrayOf(1e30, 1e30, 1e30, -1e30, -1e30, -1e30)
        for (p in cpolys()) for (v in p.v) {
            b[0] = min(b[0], v.x); b[1] = min(b[1], v.y); b[2] = min(b[2], v.z)
            b[3] = max(b[3], v.x); b[4] = max(b[4], v.y); b[5] = max(b[5], v.z)
        }
        cachedBox = b
        return b
    }

    fun toSolid(name: String): Solid? {
        val s = solid
        if (s != null) return s.withName(name)
        val p = polys ?: return null
        return solidFromPolys(p, name, opId)
    }
}

private fun overlap(a: DoubleArray, b: DoubleArray): Boolean {
    val t = 1e-4
    return a[0] <= b[3] + t && b[0] <= a[3] + t &&
        a[1] <= b[4] + t && b[1] <= a[4] + t &&
        a[2] <= b[5] + t && b[2] <= a[5] + t
}

/** Las cajas se penetran de verdad (no solo se tocan). */
private fun overlapStrict(a: DoubleArray, b: DoubleArray): Boolean {
    val m = 1e-3
    return a[0] < b[3] - m && b[0] < a[3] - m &&
        a[1] < b[4] - m && b[1] < a[4] - m &&
        a[2] < b[5] - m && b[2] < a[5] - m
}

private fun solidBox(s: Solid): DoubleArray {
    val b = doubleArrayOf(1e30, 1e30, 1e30, -1e30, -1e30, -1e30)
    for (v in s.verts) {
        b[0] = min(b[0], v.x.toDouble()); b[1] = min(b[1], v.y.toDouble()); b[2] = min(b[2], v.z.toDouble())
        b[3] = max(b[3], v.x.toDouble()); b[4] = max(b[4], v.y.toDouble()); b[5] = max(b[5], v.z.toDouble())
    }
    return b
}

private fun touchesAny(bodies: List<Body>, tool: Solid): Boolean {
    val tb = solidBox(tool)
    return bodies.any { overlapStrict(it.box(), tb) }
}

private fun applyOp(bodies: MutableList<Body>, tool: Solid, op: BoolOp, id: String) {
    val tb = Body(tool, null, id)
    if (op == BoolOp.New) {
        bodies.add(tb)
        return
    }
    val tp = tb.cpolys()
    val tbox = tb.box()
    when (op) {
        BoolOp.Add -> {
            val hits = bodies.filter { overlap(it.box(), tbox) }
            if (hits.isEmpty()) {
                bodies.add(tb)
                return
            }
            var acc = tp
            for (h in hits) acc = Csg.union(h.cpolys(), acc)
            val first = bodies.indexOf(hits[0])
            bodies.removeAll(hits.toSet())
            bodies.add(min(first, bodies.size), Body(null, acc, id))
        }
        BoolOp.Cut -> {
            val iter = bodies.listIterator()
            while (iter.hasNext()) {
                val b = iter.next()
                if (!overlapStrict(b.box(), tbox)) continue
                val res = Csg.subtract(b.cpolys(), tp)
                if (res.isEmpty()) iter.remove() else iter.set(Body(null, res, id))
            }
        }
        BoolOp.Intersect -> {
            val iter = bodies.listIterator()
            while (iter.hasNext()) {
                val b = iter.next()
                if (!overlapStrict(b.box(), tbox)) continue
                val res = Csg.intersect(b.cpolys(), tp)
                if (res.isEmpty()) iter.remove() else iter.set(Body(null, res, id))
            }
        }
        BoolOp.New -> {}
    }
}

// ------------------------------------------------------------------
// Tipos de extremo: hasta el siguiente, pieza, cara, vértice
// ------------------------------------------------------------------

private fun rayTri(o: D3, d: D3, a: D3, b: D3, c: D3): Double? {
    val e1 = b - a
    val e2 = c - a
    val h = d.cross(e2)
    val det = e1.dot(h)
    if (abs(det) < 1e-12) return null
    val f = 1.0 / det
    val s = o - a
    val u = f * s.dot(h)
    if (u < -1e-9 || u > 1.0 + 1e-9) return null
    val q = s.cross(e1)
    val v = f * d.dot(q)
    if (v < -1e-9 || u + v > 1.0 + 1e-9) return null
    return f * e2.dot(q)
}

private fun hitsOf(body: Body, o: D3, d: D3): List<Double> {
    val out = ArrayList<Double>()
    for (p in body.cpolys()) {
        for (k in 1 until p.v.size - 1) {
            val t = rayTri(o, d, p.v[0], p.v[k], p.v[k + 1]) ?: continue
            if (t > 1e-3) out.add(t)
        }
    }
    return out
}

/** Profundidad (a lo largo de la normal) que pide el tipo de extremo, o null si no se puede calcular. */
fun resolveEnd(e: ExtrudeOp, plane: String, poly: List<Pt>, bodies: List<Body>): Float? {
    val dir = if (e.reverse) -1f else 1f
    val t0 = if (e.offOn) dir * e.startOffset else 0f
    val nW = planeVec(plane, 0f, 0f, 1f)
    var dv = nW
    val axis = when (e.dirAxis) {
        1 -> V3(1f, 0f, 0f)
        2 -> V3(0f, 1f, 0f)
        3 -> V3(0f, 0f, 1f)
        else -> null
    }
    if (axis != null && abs(axis.dot(nW)) > 0.05f) {
        val a = if (axis.dot(nW) > 0f) axis else -axis
        val c = a.dot(nW)
        dv = nW + (a - nW * c) * (1f / c)
    }
    val dray = d3(dv * dir)
    val step = max(1, poly.size / 16)
    val samples = ArrayList<Pt>()
    var i = 0
    while (i < poly.size) { samples.add(poly[i]); i += step }
    samples.add(Pt(poly.map { it.u }.average().toFloat(), poly.map { it.v }.average().toFloat()))
    val origins = samples.map { d3(planePoint(plane, it.u, it.v, t0)) }

    when (e.endType) {
        EndType.ToNext -> {
            var best = Double.MAX_VALUE
            for (b in bodies) for (o in origins) {
                val h = hitsOf(b, o, dray)
                if (h.isNotEmpty()) best = min(best, h.min())
            }
            return if (best < Double.MAX_VALUE) best.toFloat() else null
        }
        EndType.ToPart -> {
            val ref = e.endRef ?: return null
            val idx = bodies.indices.firstOrNull { "Part ${it + 1}" == ref } ?: return null
            var best = -1.0
            for (o in origins) {
                val h = hitsOf(bodies[idx], o, dray)
                if (h.isNotEmpty()) best = max(best, h.max())
            }
            return if (best > 0.0) best.toFloat() else null
        }
        EndType.ToFace -> {
            val ref = e.endRef ?: return null
            if (!ref.startsWith("F|")) return null
            val pd = planeDef(ref)
            val nf = d3(pd.n)
            val po = d3(pd.o)
            var best = -1.0
            for (o in origins) {
                val den = dray.dot(nf)
                if (abs(den) < 1e-6) continue
                val t = (po - o).dot(nf) / den
                if (t > 1e-3) best = max(best, t)
            }
            return if (best > 0.0) best.toFloat() else null
        }
        EndType.ToVertex -> {
            val ref = e.endRef ?: return null
            val p = ref.split(",")
            if (p.size != 3) return null
            val vv = D3(p[0].toDouble(), p[1].toDouble(), p[2].toDouble())
            val t = (vv - origins.last()).dot(d3(nW)) * dir
            return if (t > 1e-3) t.toFloat() else null
        }
        EndType.Blind -> return null
    }
    return null
}

/** Construye todas las piezas del modelo aplicando las operaciones en orden. */
fun buildSolids(ops: List<Op>): List<Solid> {
    val sketches = ops.filterIsInstance<SketchOp>().associateBy { it.id }
    val bodies = ArrayList<Body>()
    for (e in ops.filterIsInstance<ExtrudeOp>()) {
        val s = sketches[e.sketchId] ?: continue
        val all = profiles(s.shapes)
        val chosen = e.regions?.let { sel -> all.filterIndexed { i, _ -> i in sel } } ?: all
        val op = if (e.kind == ExtrudeKind.Surface) BoolOp.New else e.op
        val cutLike = op == BoolOp.Cut || op == BoolOp.Intersect
        val onFace = s.plane.startsWith("F|")
        val tools = ArrayList<Solid>()
        for (poly in chosen) {
            var ee = e
            // Cortar o intersecar sobre una cara: por defecto la herramienta entra en el material
            if (cutLike && onFace) ee = ee.copy(reverse = !ee.reverse)
            if (ee.endType != EndType.Blind) {
                val d = resolveEnd(ee, s.plane, poly, bodies)
                if (d != null && d > 1e-4f) ee = ee.copy(depth = d, symmetric = false, secOn = false)
            }
            // La herramienta de corte sobresale un poco por detrás para no quedar pegada a la cara
            if (op == BoolOp.Cut && !ee.secOn && !ee.symmetric) {
                // sobresale 0.2 mm por ambos extremos para que un corte pasante no deje una película
                ee = ee.copy(secOn = true, second = 0.02f, depth = ee.depth + 0.02f)
            }
            var t = buildExtrusion(s.plane, poly, ee, "tmp") ?: continue
            // Si en un plano normal la herramienta no toca nada, se prueba hacia el otro lado
            if (cutLike && !onFace && !e.reverse && !touchesAny(bodies, t)) {
                val alt = buildExtrusion(s.plane, poly, ee.copy(reverse = !ee.reverse), "tmp")
                if (alt != null && touchesAny(bodies, alt)) t = alt
            }
            tools.add(t)
        }
        for (t in tools) applyOp(bodies, t, op, e.id)
    }
    val out = ArrayList<Solid>()
    for (b in bodies) {
        val sol = b.toSolid("Part ${out.size + 1}") ?: continue
        out.add(sol)
    }
    return out
}

// ------------------------------------------------------------------
// Polígonos -> Solid (malla unida, aristas reales, grupos de caras)
// ------------------------------------------------------------------

private class VMap(private val tol: Double) {
    val pts = ArrayList<D3>()
    private val cells = HashMap<Long, MutableList<Int>>()
    private val cell = 1e-3

    private fun key(ix: Long, iy: Long, iz: Long): Long =
        (ix * 73856093L) xor (iy * 19349663L) xor (iz * 83492791L)

    fun add(p: D3): Int {
        val cx = floor(p.x / cell).toLong()
        val cy = floor(p.y / cell).toLong()
        val cz = floor(p.z / cell).toLong()
        for (dx in -1L..1L) for (dy in -1L..1L) for (dz in -1L..1L) {
            val list = cells[key(cx + dx, cy + dy, cz + dz)] ?: continue
            for (id in list) {
                val q = pts[id]
                if (abs(q.x - p.x) < tol && abs(q.y - p.y) < tol && abs(q.z - p.z) < tol) return id
            }
        }
        pts.add(p)
        cells.getOrPut(key(cx, cy, cz)) { ArrayList() }.add(pts.size - 1)
        return pts.size - 1
    }
}

/** Inserta en cada arista los vértices que caen sobre ella (corrige uniones en T). */
private fun repairT(faces: List<IntArray>, pts: List<D3>): List<IntArray> {
    val cs = 2.0
    val tol = 2e-5
    val grid = HashMap<Long, MutableList<Int>>()
    fun gk(ix: Int, iy: Int, iz: Int): Long =
        (ix.toLong() * 73856093L) xor (iy.toLong() * 19349663L) xor (iz.toLong() * 83492791L)
    for ((id, p) in pts.withIndex()) {
        grid.getOrPut(gk(floor(p.x / cs).toInt(), floor(p.y / cs).toInt(), floor(p.z / cs).toInt())) {
            ArrayList()
        }.add(id)
    }
    val out = ArrayList<IntArray>()
    for (f in faces) {
        val nf = ArrayList<Int>()
        for (i in f.indices) {
            val a = f[i]
            val b = f[(i + 1) % f.size]
            nf.add(a)
            val pa = pts[a]
            val pb = pts[b]
            val ab = pb - pa
            val l2 = ab.dot(ab)
            if (l2 < 1e-12) continue
            val extras = ArrayList<Pair<Double, Int>>()
            val x0 = floor((min(pa.x, pb.x) - tol) / cs).toInt()
            val x1 = floor((max(pa.x, pb.x) + tol) / cs).toInt()
            val y0 = floor((min(pa.y, pb.y) - tol) / cs).toInt()
            val y1 = floor((max(pa.y, pb.y) + tol) / cs).toInt()
            val z0 = floor((min(pa.z, pb.z) - tol) / cs).toInt()
            val z1 = floor((max(pa.z, pb.z) + tol) / cs).toInt()
            for (ix in x0..x1) for (iy in y0..y1) for (iz in z0..z1) {
                val list = grid[gk(ix, iy, iz)] ?: continue
                for (id in list) {
                    if (id == a || id == b) continue
                    val p = pts[id]
                    val t = (p - pa).dot(ab) / l2
                    if (t <= 1e-6 || t >= 1.0 - 1e-6) continue
                    val proj = pa + ab * t
                    val d = p - proj
                    if (abs(d.x) < tol && abs(d.y) < tol && abs(d.z) < tol) extras.add(Pair(t, id))
                }
            }
            extras.sortBy { it.first }
            for (x in extras) if (nf.last() != x.second) nf.add(x.second)
        }
        out.add(nf.toIntArray())
    }
    return out
}

private class EAcc(val a: Int, val b: Int) {
    val fs = ArrayList<Int>(2)
}

fun solidFromPolys(polys: List<CPoly>, name: String, opId: String): Solid? {
    val vm = VMap(1e-5)
    val faces0 = ArrayList<IntArray>()
    val fn = ArrayList<D3>()
    val fw = ArrayList<Double>()
    for (p in polys) {
        val idx = ArrayList<Int>()
        for (pt in p.v) {
            val id = vm.add(pt)
            if (idx.isEmpty() || idx.last() != id) idx.add(id)
        }
        if (idx.size > 1 && idx.first() == idx.last()) idx.removeAt(idx.size - 1)
        if (idx.size < 3) continue
        faces0.add(idx.toIntArray())
        fn.add(p.plane.n)
        fw.add(p.plane.w)
    }
    if (faces0.isEmpty()) return null
    val faces = repairT(faces0, vm.pts)
    val verts = vm.pts.map { V3(it.x.toFloat(), it.y.toFloat(), it.z.toFloat()) }
    val normals = fn.map { V3(it.x.toFloat(), it.y.toFloat(), it.z.toFloat()) }

    val tris = ArrayList<IntArray>()
    for (f in faces) for (k in 1 until f.size - 1) tris.add(intArrayOf(f[0], f[k], f[k + 1]))

    // Aristas reales y superficies curvas
    val emap = HashMap<Long, EAcc>()
    for ((fi, f) in faces.withIndex()) {
        for (i in f.indices) {
            val a = f[i]
            val b = f[(i + 1) % f.size]
            if (a == b) continue
            val lo = min(a, b)
            val hi = max(a, b)
            emap.getOrPut(lo.toLong() * 4_000_037L + hi) { EAcc(lo, hi) }.fs.add(fi)
        }
    }
    val edges = ArrayList<Edge>()
    val curved = BooleanArray(faces.size)
    for (ea in emap.values) {
        if (ea.fs.size == 2) {
            val d = normals[ea.fs[0]].dot(normals[ea.fs[1]])
            if (d < 0.866f) edges.add(Edge(ea.a, ea.b, ea.fs[0], ea.fs[1]))
            else if (d < 0.99999f) {
                curved[ea.fs[0]] = true
                curved[ea.fs[1]] = true
            }
        } else {
            edges.add(Edge(ea.a, ea.b, ea.fs[0], if (ea.fs.size > 1) ea.fs[1] else -1))
        }
    }

    // Caras coplanares comparten grupo (para seleccionarlas juntas)
    val groups = HashMap<String, Int>()
    val faceGroup = ArrayList<Int>()
    for (i in faces.indices) {
        val n = fn[i]
        val key = "${Math.round(n.x * 1000)},${Math.round(n.y * 1000)},${Math.round(n.z * 1000)},${Math.round(fw[i] * 1000)}"
        faceGroup.add(groups.getOrPut(key) { groups.size })
    }
    val selectable = List(faces.size) { !curved[it] }
    var vol = 0f
    var area = 0f
    for (t in tris) {
        val a = verts[t[0]]
        val b = verts[t[1]]
        val c = verts[t[2]]
        vol += a.dot(b.cross(c)) / 6f
        area += 0.5f * (b - a).cross(c - a).length()
    }
    return Solid(
        name, verts, faces, normals, tris, edges, selectable,
        abs(vol), area, opId, emptyList(), false, faceGroup
    )
}

fun Solid.withName(n: String): Solid = Solid(
    n, verts, faces, normals, tris, edges, selectable, volume, area, opId, sideNormals, twoSided, faceGroup
)

/** Texto "x,y,z" de un vértice, para guardarlo como referencia de extremo. */
fun vertexKey(v: V3): String = String.format(Locale.US, "%.4f,%.4f,%.4f", v.x, v.y, v.z)
