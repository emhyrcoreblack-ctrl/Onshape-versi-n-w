package com.modelo3d.studio.model

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Project(
    val id: String,
    var name: String,
    var modified: Long,
    var ops: List<Op> = emptyList()
)

/** Guarda cada proyecto como un JSON en almacenamiento interno. */
class ProjectStore(private val dir: File) {
    init { dir.mkdirs() }

    fun list(): List<Project> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { parse(it.readText()) }.getOrNull() }
            ?.sortedByDescending { it.modified } ?: emptyList()

    fun create(name: String): Project {
        val p = Project(UUID.randomUUID().toString(), name, System.currentTimeMillis())
        save(p)
        return p
    }

    fun save(p: Project) {
        p.modified = System.currentTimeMillis()
        val arr = JSONArray()
        p.ops.forEach { arr.put(it.toJson()) }
        val json = JSONObject().put("id", p.id).put("name", p.name)
            .put("modified", p.modified).put("ops", arr)
        File(dir, "${p.id}.json").writeText(json.toString())
    }

    fun delete(p: Project) { File(dir, "${p.id}.json").delete() }

    private fun parse(s: String): Project {
        val o = JSONObject(s)
        val arr = o.optJSONArray("ops")
        val ops = if (arr == null) emptyList() else
            (0 until arr.length()).mapNotNull { opFromJson(arr.getJSONObject(it)) }
        return Project(o.getString("id"), o.getString("name"), o.getLong("modified"), ops)
    }
}
