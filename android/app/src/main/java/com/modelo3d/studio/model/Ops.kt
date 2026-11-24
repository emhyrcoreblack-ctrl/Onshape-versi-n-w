package com.modelo3d.studio.model

import org.json.JSONArray
import org.json.JSONObject

/** Operación del árbol paramétrico (como el panel "Operaciones" de Onshape). */
sealed interface Op {
    val id: String
    val name: String
}

data class SketchOp(
    override val id: String,
    override val name: String,
    val plane: String,
    val shapes: List<Shape>
) : Op

enum class ExtrudeKind { Solid, Surface, Thin }
enum class BoolOp { New, Add, Cut, Intersect }
enum class EndType { Blind, ToNext, ToPart, ToFace, ToVertex }

data class ExtrudeOp(
    override val id: String,
    override val name: String,
    val sketchId: String,
    val depth: Float,
    val reverse: Boolean,
    val kind: ExtrudeKind = ExtrudeKind.Solid,
    val op: BoolOp = BoolOp.New,
    val symmetric: Boolean = false,
    val offOn: Boolean = false,
    val startOffset: Float = 1f,
    val dfOn: Boolean = false,
    val draftDeg: Float = 5f,
    val secOn: Boolean = false,
    val second: Float = 2f,
    val thickness: Float = 1f,
    val thinOut: Boolean = false,
    val regions: List<Int>? = null,
    val endType: EndType = EndType.Blind,
    val endRef: String? = null,
    val dirAxis: Int = 0
) : Op

fun List<Op>.upserted(op: Op): List<Op> {
    val i = indexOfFirst { it.id == op.id }
    return if (i < 0) this + op else toMutableList().also { it[i] = op }
}

private fun Pt.toJ(): JSONArray = JSONArray().put(u.toDouble()).put(v.toDouble())
private fun jPt(a: JSONArray) = Pt(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())

private fun Shape.toJson(): JSONObject = when (this) {
    is LineS -> JSONObject().put("t", "line").put("a", a.toJ()).put("b", b.toJ()).put("fx", fixed)
    is RectS -> JSONObject().put("t", "rect").put("x", x.toDouble()).put("y", y.toDouble())
        .put("w", w.toDouble()).put("h", h.toDouble()).put("fw", fixW).put("fh", fixH)
    is CircleS -> JSONObject().put("t", "circle").put("c", c.toJ()).put("r", r.toDouble()).put("fx", fixed)
    is ArcS -> JSONObject().put("t", "arc").put("a", a.toJ()).put("m", m.toJ()).put("c", c.toJ())
}

private fun shapeFromJson(o: JSONObject): Shape? = when (o.optString("t")) {
    "line" -> LineS(jPt(o.getJSONArray("a")), jPt(o.getJSONArray("b")), o.optBoolean("fx", false))
    "rect" -> RectS(
        o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
        o.getDouble("w").toFloat(), o.getDouble("h").toFloat(),
        o.optBoolean("fw", false), o.optBoolean("fh", false)
    )
    "circle" -> CircleS(jPt(o.getJSONArray("c")), o.getDouble("r").toFloat(), o.optBoolean("fx", false))
    "arc" -> ArcS(jPt(o.getJSONArray("a")), jPt(o.getJSONArray("m")), jPt(o.getJSONArray("c")))
    else -> null
}

fun Op.toJson(): JSONObject = when (this) {
    is SketchOp -> {
        val arr = JSONArray()
        shapes.forEach { arr.put(it.toJson()) }
        JSONObject().put("type", "sketch").put("id", id).put("name", name)
            .put("plane", plane).put("shapes", arr)
    }
    is ExtrudeOp -> {
        val o = JSONObject().put("type", "extrude").put("id", id).put("name", name)
            .put("sketchId", sketchId).put("depth", depth.toDouble()).put("reverse", reverse)
            .put("kind", kind.ordinal).put("op", op.ordinal).put("sym", symmetric)
            .put("offOn", offOn).put("off", startOffset.toDouble())
            .put("dfOn", dfOn).put("df", draftDeg.toDouble())
            .put("secOn", secOn).put("sec", second.toDouble())
            .put("thick", thickness.toDouble()).put("thinOut", thinOut)
            .put("endType", endType.ordinal).put("dirAxis", dirAxis)
        endRef?.let { o.put("endRef", it) }
        val r = regions
        if (r != null) {
            val arr = JSONArray()
            r.forEach { arr.put(it) }
            o.put("regions", arr)
        }
        o
    }
}

fun opFromJson(o: JSONObject): Op? = when (o.optString("type")) {
    "sketch" -> {
        val arr = o.optJSONArray("shapes")
        val shapes: List<Shape> = if (arr != null) {
            (0 until arr.length()).mapNotNull { shapeFromJson(arr.getJSONObject(it)) }
        } else if (o.has("w")) {
            // Formato antiguo (v0.2): un solo rectángulo
            listOf(
                RectS(
                    o.getDouble("x0").toFloat(), o.getDouble("y0").toFloat(),
                    o.getDouble("w").toFloat(), o.getDouble("h").toFloat()
                )
            )
        } else emptyList()
        SketchOp(o.getString("id"), o.getString("name"), o.getString("plane"), shapes)
    }
    "extrude" -> {
        val ra = o.optJSONArray("regions")
        ExtrudeOp(
            o.getString("id"), o.getString("name"), o.getString("sketchId"),
            o.getDouble("depth").toFloat(), o.getBoolean("reverse"),
            ExtrudeKind.values().getOrElse(o.optInt("kind", 0)) { ExtrudeKind.Solid },
            BoolOp.values().getOrElse(o.optInt("op", 0)) { BoolOp.New },
            o.optBoolean("sym", false),
            o.optBoolean("offOn", false), o.optDouble("off", 1.0).toFloat(),
            o.optBoolean("dfOn", false), o.optDouble("df", 5.0).toFloat(),
            o.optBoolean("secOn", false), o.optDouble("sec", 2.0).toFloat(),
            o.optDouble("thick", 1.0).toFloat(), o.optBoolean("thinOut", false),
            if (ra == null) null else (0 until ra.length()).map { ra.getInt(it) },
            EndType.values().getOrElse(o.optInt("endType", 0)) { EndType.Blind },
            if (o.has("endRef")) o.getString("endRef") else null,
            o.optInt("dirAxis", 0)
        )
    }
    else -> null
}
