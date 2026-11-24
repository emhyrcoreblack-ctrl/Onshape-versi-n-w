package com.modelo3d.studio.viewer

import androidx.compose.ui.geometry.Offset
import com.modelo3d.studio.model.PLANES
import com.modelo3d.studio.model.Solid
import com.modelo3d.studio.model.V3
import com.modelo3d.studio.model.facePlaneKey
import com.modelo3d.studio.model.planePoint
import com.modelo3d.studio.model.vertexKey
import kotlin.math.hypot

/** Cara de una pieza elegida con un toque: su plano de dibujo y su tamaño aproximado. */
class FacePick(val solidName: String, val faceIndex: Int, val plane: String, val half: Float)

private fun pointInPolygon(p: List<Offset>, t: Offset): Boolean {
    var c = false
    var j = p.size - 1
    for (i in p.indices) {
        if ((p[i].y > t.y) != (p[j].y > t.y) &&
            t.x < (p[j].x - p[i].x) * (t.y - p[i].y) / (p[j].y - p[i].y) + p[i].x
        ) c = !c
        j = i
    }
    return c
}

/** Devuelve la cara plana visible más cercana bajo el toque (px, py), o null. */
fun pickFace(camera: OrbitCamera, solids: List<Solid>, px: Float, py: Float): FacePick? {
    val cx = camera.viewSize.width / 2f
    val cy = camera.viewSize.height / 2f
    val t = Offset(px, py)
    var best: FacePick? = null
    var bestD = Float.MAX_VALUE
    for (s in solids) {
        for ((fi, f) in s.faces.withIndex()) {
            if (!s.selectable[fi]) continue
            val nrm = s.normals[fi]
            if (camera.rotate(nrm).third > -0.0001f) continue
            val pts = f.map { camera.project(s.verts[it], cx, cy) }
            if (!pointInPolygon(pts.map { it.first }, t)) continue
            val d = pts.map { it.second }.average().toFloat()
            if (d < bestD) {
                bestD = d
                val cen = f.fold(V3(0f, 0f, 0f)) { a, i -> a + s.verts[i] } * (1f / f.size)
                val half = f.maxOf { (s.verts[it] - cen).length() } * 1.35f
                best = FacePick(s.name, fi, facePlaneKey(cen, nrm), half)
            }
        }
    }
    return best
}

/** Devuelve el plano de referencia visible más cercano bajo el toque, o null. */
fun pickPlaneAt(
    camera: OrbitCamera, half: Float, visible: Map<String, Boolean>, px: Float, py: Float
): String? {
    val cx = camera.viewSize.width / 2f
    val cy = camera.viewSize.height / 2f
    val t = Offset(px, py)
    var best: String? = null
    var bd = Float.MAX_VALUE
    for (name in PLANES) {
        if (visible[name] == false) continue
        val corners = listOf(
            planePoint(name, -half, -half), planePoint(name, half, -half),
            planePoint(name, half, half), planePoint(name, -half, half)
        )
        val pr = corners.map { camera.project(it, cx, cy) }
        if (!pointInPolygon(pr.map { it.first }, t)) continue
        val d = pr.map { it.second }.average().toFloat()
        if (d < bd) { bd = d; best = name }
    }
    return best
}

/** Nombre de la pieza cuya cara visible está bajo el toque (cualquier cara, curva o plana). */
fun pickBody(camera: OrbitCamera, solids: List<Solid>, px: Float, py: Float): String? {
    val cx = camera.viewSize.width / 2f
    val cy = camera.viewSize.height / 2f
    val t = Offset(px, py)
    var best: String? = null
    var bestD = Float.MAX_VALUE
    for (s in solids) {
        for ((fi, f) in s.faces.withIndex()) {
            val nrm = s.normals[fi]
            if (!s.twoSided && camera.rotate(nrm).third > -0.0001f) continue
            val pts = f.map { camera.project(s.verts[it], cx, cy) }
            if (!pointInPolygon(pts.map { it.first }, t)) continue
            val d = pts.map { it.second }.average().toFloat()
            if (d < bestD) {
                bestD = d
                best = s.name
            }
        }
    }
    return best
}

/** Vértice más cercano al toque (a menos de ~50 px), como texto "x,y,z". */
fun pickVertex(camera: OrbitCamera, solids: List<Solid>, px: Float, py: Float): String? {
    val cx = camera.viewSize.width / 2f
    val cy = camera.viewSize.height / 2f
    var best: V3? = null
    var bd = 50f
    for (s in solids) {
        for (v in s.verts) {
            val p = camera.project(v, cx, cy).first
            val d = hypot(p.x - px, p.y - py)
            if (d < bd) {
                bd = d
                best = v
            }
        }
    }
    return best?.let { vertexKey(it) }
}
