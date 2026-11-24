package com.modelo3d.studio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class IconKind {
    Cube, Pencil, Extrude, Ops, Dots, Arrow, Frame, Move, Rotate, Zoom, Lock, Unlock, Menu, Layers, Plane, Tree, Eye, EyeOff, Origin, Undo, Redo, Clock, Balance,
    ToolLine, ToolRect, ToolRectCenter, ToolCircle, ToolCircle3, ToolArc, ToolDim, ToolSoon
}

@Composable
fun CadIcon(kind: IconKind, tint: Color, iconSize: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(iconSize)) {
        val u = this.size.minDimension / 24f
        val sw = 1.7f * u
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, p(x1, y1), p(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
        fun poly(vararg pts: Pair<Float, Float>, close: Boolean = true, fill: Boolean = false) {
            val path = Path()
            pts.forEachIndexed { i, q ->
                if (i == 0) path.moveTo(q.first * u, q.second * u) else path.lineTo(q.first * u, q.second * u)
            }
            if (close) path.close()
            drawPath(path, tint, style = if (fill) Fill else st)
        }

        when (kind) {
            IconKind.Cube -> {
                poly(12f to 2.5f, 20f to 7f, 20f to 17f, 12f to 21.5f, 4f to 17f, 4f to 7f)
                line(12f, 12f, 4f, 7f); line(12f, 12f, 20f, 7f); line(12f, 12f, 12f, 21.5f)
            }
            IconKind.Pencil -> {
                poly(4f to 20f, 5.2f to 15f, 15f to 5.2f, 18.8f to 9f, 9f to 18.8f)
                line(13f, 7.2f, 16.8f, 11f)
            }
            IconKind.Extrude -> {
                poly(5f to 10f, 15f to 10f, 15f to 20f, 5f to 20f)
                poly(5f to 10f, 9f to 6f, 19f to 6f, 15f to 10f)
                poly(15f to 10f, 19f to 6f, 19f to 16f, 15f to 20f)
            }
            IconKind.Ops -> {
                drawCircle(tint, 6.5f * u, p(9.5f, 12f), style = st)
                drawCircle(tint, 6.5f * u, p(14.5f, 12f), style = st)
            }
            IconKind.Dots -> {
                drawCircle(tint, 1.8f * u, p(5f, 12f))
                drawCircle(tint, 1.8f * u, p(12f, 12f))
                drawCircle(tint, 1.8f * u, p(19f, 12f))
            }
            IconKind.Arrow ->
                poly(6f to 3f, 6f to 19f, 10.5f to 15f, 14f to 21f, 16.5f to 19.8f, 13f to 14f, 19f to 14f, fill = true)
            IconKind.Frame -> {
                line(4f, 9f, 4f, 4f); line(4f, 4f, 9f, 4f)
                line(15f, 4f, 20f, 4f); line(20f, 4f, 20f, 9f)
                line(4f, 15f, 4f, 20f); line(4f, 20f, 9f, 20f)
                line(20f, 15f, 20f, 20f); line(15f, 20f, 20f, 20f)
            }
            IconKind.Move -> {
                line(12f, 3f, 12f, 21f); line(3f, 12f, 21f, 12f)
                poly(9f to 6f, 12f to 3f, 15f to 6f, close = false)
                poly(9f to 18f, 12f to 21f, 15f to 18f, close = false)
                poly(6f to 9f, 3f to 12f, 6f to 15f, close = false)
                poly(18f to 9f, 21f to 12f, 18f to 15f, close = false)
            }
            IconKind.Rotate -> {
                drawArc(tint, -50f, 280f, false, p(4f, 4f), Size(16f * u, 16f * u), style = st)
                line(17.1f, 5.9f, 17.6f, 2.2f); line(17.1f, 5.9f, 20.8f, 6.6f)
            }
            IconKind.Zoom -> {
                drawCircle(tint, 6.5f * u, p(10.5f, 10.5f), style = st)
                line(15.3f, 15.3f, 20.5f, 20.5f)
            }
            IconKind.Lock -> {
                drawRoundRect(tint, p(6f, 11f), Size(12f * u, 10f * u), CornerRadius(2f * u), style = st)
                drawArc(tint, 180f, 180f, false, p(8.5f, 4.5f), Size(7f * u, 7f * u), style = st)
                line(8.5f, 8f, 8.5f, 11f); line(15.5f, 8f, 15.5f, 11f)
            }
            IconKind.Unlock -> {
                drawRoundRect(tint, p(6f, 11f), Size(12f * u, 10f * u), CornerRadius(2f * u), style = st)
                drawArc(tint, 180f, 180f, false, p(8.5f, 2.5f), Size(7f * u, 7f * u), style = st)
                line(8.5f, 6f, 8.5f, 11f)
            }
            IconKind.Menu -> { line(4f, 7f, 20f, 7f); line(4f, 12f, 20f, 12f); line(4f, 17f, 20f, 17f) }
            IconKind.Layers -> {
                poly(12f to 3f, 21f to 8f, 12f to 13f, 3f to 8f)
                poly(3f to 12f, 12f to 17f, 21f to 12f, close = false)
                poly(3f to 16f, 12f to 21f, 21f to 16f, close = false)
            }
            IconKind.Plane -> poly(5f to 8f, 15f to 5f, 19f to 16f, 9f to 19f)
            IconKind.Tree -> {
                drawRoundRect(tint, p(5f, 3f), Size(14f * u, 18f * u), CornerRadius(2.5f * u), style = st)
                line(8.5f, 8f, 15.5f, 8f); line(8.5f, 12f, 15.5f, 12f); line(8.5f, 16f, 13f, 16f)
            }
            IconKind.Eye -> {
                drawOval(tint, p(2.5f, 7f), Size(19f * u, 10f * u), style = st)
                drawCircle(tint, 2.6f * u, p(12f, 12f), style = st)
            }
            IconKind.EyeOff -> {
                drawOval(tint, p(2.5f, 7f), Size(19f * u, 10f * u), style = st)
                line(4f, 20f, 20f, 4f)
            }
            IconKind.Undo -> {
                poly(9f to 5f, 4f to 10f, 9f to 15f, close = false)
                line(4f, 10f, 14f, 10f)
                drawArc(tint, -90f, 180f, false, p(9.5f, 10f), Size(9f * u, 9f * u), style = st)
                line(14f, 19f, 8f, 19f)
            }
            IconKind.Redo -> {
                poly(15f to 5f, 20f to 10f, 15f to 15f, close = false)
                line(20f, 10f, 10f, 10f)
                drawArc(tint, -90f, -180f, false, p(5.5f, 10f), Size(9f * u, 9f * u), style = st)
                line(10f, 19f, 16f, 19f)
            }
            IconKind.Clock -> {
                drawCircle(tint, 8.5f * u, p(12f, 12f), style = st)
                line(12f, 7f, 12f, 12f); line(12f, 12f, 15.5f, 14f)
            }
            IconKind.Balance -> {
                line(12f, 4f, 12f, 20f); line(8f, 20f, 16f, 20f); line(4f, 7f, 20f, 7f)
                line(4f, 7f, 2f, 13f); line(4f, 7f, 6f, 13f); line(2f, 13f, 6f, 13f)
                line(20f, 7f, 18f, 13f); line(20f, 7f, 22f, 13f); line(18f, 13f, 22f, 13f)
            }
            IconKind.ToolLine -> {
                line(5f, 19f, 19f, 5f)
                drawCircle(tint, 1.9f * u, p(5f, 19f), style = st)
                drawCircle(tint, 1.9f * u, p(19f, 5f), style = st)
            }
            IconKind.ToolRect -> {
                poly(5f to 7f, 19f to 7f, 19f to 17f, 5f to 17f)
                drawCircle(tint, 1.6f * u, p(5f, 17f), style = st)
                drawCircle(tint, 1.6f * u, p(19f, 7f), style = st)
            }
            IconKind.ToolRectCenter -> {
                poly(4f to 6f, 20f to 6f, 20f to 18f, 4f to 18f)
                drawCircle(tint, 1.8f * u, p(12f, 12f), style = st)
                drawCircle(tint, 1.6f * u, p(20f, 6f), style = st)
            }
            IconKind.ToolCircle -> {
                drawCircle(tint, 7f * u, p(12f, 12f), style = st)
                drawCircle(tint, 1.6f * u, p(12f, 12f), style = st)
                drawCircle(tint, 1.5f * u, p(19f, 12f), style = st)
            }
            IconKind.ToolCircle3 -> {
                drawCircle(tint, 7f * u, p(12f, 12f), style = st)
                drawCircle(tint, 1.5f * u, p(12f, 5f), style = st)
                drawCircle(tint, 1.5f * u, p(5.9f, 15.5f), style = st)
                drawCircle(tint, 1.5f * u, p(18.1f, 15.5f), style = st)
            }
            IconKind.ToolArc -> {
                drawArc(tint, 200f, 140f, false, p(4f, 8f), Size(16f * u, 16f * u), style = st)
                drawCircle(tint, 1.6f * u, p(4.5f, 13.3f), style = st)
                drawCircle(tint, 1.6f * u, p(19.5f, 13.3f), style = st)
            }
            IconKind.ToolDim -> {
                line(6f, 18f, 18f, 6f)
                line(6f, 18f, 6f, 13f); line(6f, 18f, 11f, 18f)
                line(18f, 6f, 18f, 11f); line(18f, 6f, 13f, 6f)
            }
            IconKind.ToolSoon -> poly(6f to 6f, 18f to 6f, 18f to 18f, 6f to 18f)
            IconKind.Origin -> {
                drawCircle(tint, 6f * u, p(12f, 12f), style = st)
                drawCircle(tint, 1.8f * u, p(12f, 12f))
            }
        }
    }
}
