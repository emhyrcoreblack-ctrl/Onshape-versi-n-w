package com.modelo3d.studio.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.modelo3d.studio.model.V3
import kotlin.math.cos
import kotlin.math.sin

enum class NavMode { Standard, Pan, Orbit, Zoom }

class OrbitCamera {
    var yaw by mutableFloatStateOf(-35f)
    var pitch by mutableFloatStateOf(25f)
    var zoom by mutableFloatStateOf(40f)   // píxeles por unidad (cm)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)
    var viewSize by mutableStateOf(IntSize.Zero)
    var navMode by mutableStateOf(NavMode.Standard)
    var locked by mutableStateOf(false)
    var perspective by mutableStateOf(false)

    fun setView(newYaw: Float, newPitch: Float) {
        yaw = newYaw; pitch = newPitch; panX = 0f; panY = 0f
    }

    /** Ajusta el zoom para que quepa una escena de radio [extent]. */
    fun fit(extent: Float) {
        val m = minOf(viewSize.width, viewSize.height).toFloat()
        if (m > 0f) zoom = (m * 0.45f / maxOf(extent, 1f)).coerceIn(6f, 400f)
    }

    /** Rotación pura (sin zoom ni pan): (x pantalla, arriba, profundidad). */
    fun rotate(p: V3): Triple<Float, Float, Float> {
        val ya = Math.toRadians(yaw.toDouble())
        val pa = Math.toRadians(pitch.toDouble())
        val cyw = cos(ya).toFloat(); val syw = sin(ya).toFloat()
        val cp = cos(pa).toFloat(); val sp = sin(pa).toFloat()
        val x1 = p.x * cyw - p.y * syw
        val y1 = p.x * syw + p.y * cyw
        return Triple(x1, y1 * sp + p.z * cp, y1 * cp - p.z * sp)
    }

    /** Proyecta un punto 3D a pantalla. Devuelve (posición, profundidad). */
    fun project(p: V3, cx: Float, cy: Float): Pair<Offset, Float> {
        val (x1, up, depth) = rotate(p)
        val k = kf(depth)
        return Offset(cx + panX + x1 * zoom * k, cy + panY - up * zoom * k) to depth
    }

    /** Pantalla -> coordenadas (u,v) del plano. Válido en las vistas normales al plano. */
    fun toPlane(px: Float, py: Float, cx: Float, cy: Float, origin: V3): Pair<Float, Float> {
        val (ox, oup, od) = rotate(origin)
        val s = zoom * kf(od)
        val u0 = (px - cx - panX) / s
        val v0 = -(py - cy - panY) / s
        return (u0 - ox) to (v0 - oup)
    }

    /** Desplaza la vista para que el punto p quede en el centro de la pantalla. */
    fun centerOn(p: V3) {
        val (x1, up, depth) = rotate(p)
        val k = kf(depth)
        panX = -x1 * zoom * k
        panY = up * zoom * k
    }

    /** Factor de escala por profundidad: 1 en vista ortogonal. */
    private fun kf(depth: Float): Float =
        if (perspective) CAM_DIST / (CAM_DIST + maxOf(depth, -CAM_DIST * 0.8f)) else 1f

    companion object { const val CAM_DIST = 70f }
}
