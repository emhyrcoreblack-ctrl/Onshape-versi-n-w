package com.modelo3d.studio.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.modelo3d.studio.model.V3

private fun v(x: Int, y: Int, z: Int) = V3(x.toFloat(), y.toFloat(), z.toFloat())

private class CubeFace(
    val label: String, val normal: V3, val corners: List<V3>, val yaw: Float, val pitch: Float
)

private class FaceHit(val pts: List<Offset>, val yaw: Float, val pitch: Float)

private val CUBE_FACES = listOf(
    CubeFace("Front", v(0, -1, 0), listOf(v(-1, -1, -1), v(1, -1, -1), v(1, -1, 1), v(-1, -1, 1)), 0f, 0f),
    CubeFace("Back", v(0, 1, 0), listOf(v(-1, 1, -1), v(1, 1, -1), v(1, 1, 1), v(-1, 1, 1)), 180f, 0f),
    CubeFace("Right", v(1, 0, 0), listOf(v(1, -1, -1), v(1, 1, -1), v(1, 1, 1), v(1, -1, 1)), -90f, 0f),
    CubeFace("Left", v(-1, 0, 0), listOf(v(-1, -1, -1), v(-1, 1, -1), v(-1, 1, 1), v(-1, -1, 1)), 90f, 0f),
    CubeFace("Top", v(0, 0, 1), listOf(v(-1, -1, 1), v(1, -1, 1), v(1, 1, 1), v(-1, 1, 1)), 0f, 90f),
    CubeFace("Bottom", v(0, 0, -1), listOf(v(-1, -1, -1), v(1, -1, -1), v(1, 1, -1), v(-1, 1, -1)), 0f, -90f)
)

private fun inside(p: List<Offset>, t: Offset): Boolean {
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

/** Cubo de vistas: gira con la cámara y, al tocar una cara, la lleva a esa vista. */
@Composable
fun ViewCube(camera: OrbitCamera, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 24f
            isFakeBoldText = true
            color = 0xFFEAF2FF.toInt()
        }
    }
    val hits = remember { ArrayList<FaceHit>() }

    Canvas(
        modifier
            .size(110.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val s = size.minDimension * 0.20f
        hits.clear()
        paint.color = 0xFFEAF2FF.toInt()
        val visible = CUBE_FACES
            .map { it to camera.rotate(it.normal).third }
            .filter { it.second < -0.001f }
            .sortedByDescending { it.second }
        for ((face, depth) in visible) {
            val pts = face.corners.map {
                val r = camera.rotate(it)
                Offset(c.x + r.first * s, c.y - r.second * s)
            }
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                close()
            }
            val t = (-depth).coerceIn(0f, 1f)
            drawPath(path, lerp(Color(0xFF283243), Color(0xFF4F6A93), t), style = Fill)
            drawPath(path, Color(0xCC9DB7DD), style = Stroke(width = 2f))
            val mid = Offset(pts.map { it.x }.average().toFloat(), pts.map { it.y }.average().toFloat())
            drawContext.canvas.nativeCanvas.drawText(face.label, mid.x, mid.y + 8f, paint)
            hits.add(FaceHit(pts, face.yaw, face.pitch))
        }

        // Triada de ejes desde la esquina inferior
        val corner = v(-1, -1, -1)
        val o0 = camera.rotate(corner)
        val o = Offset(c.x + o0.first * s, c.y - o0.second * s)
        val axes = listOf(
            Triple("X", v(1, 0, 0), Color(0xFFFF5252)),
            Triple("Y", v(0, 1, 0), Color(0xFF4CD964)),
            Triple("Z", v(0, 0, 1), Color(0xFF4DA3FF))
        )
        for ((name, dir, col) in axes) {
            val e = camera.rotate(corner + dir * 1.5f)
            val end = Offset(c.x + e.first * s, c.y - e.second * s)
            drawLine(col, o, end, 3f)
            paint.color = col.toArgb()
            drawContext.canvas.nativeCanvas.drawText(name, end.x + 4f, end.y + 4f, paint)
        }
    }
}
