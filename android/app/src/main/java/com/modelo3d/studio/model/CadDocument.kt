package com.modelo3d.studio.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Documento con historial de deshacer/rehacer. */
class CadDocument(initial: List<Op>) {
    var ops by mutableStateOf(initial)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private val undoStack = ArrayDeque<List<Op>>()
    private val redoStack = ArrayDeque<List<Op>>()

    private fun commit(new: List<Op>) {
        undoStack.addLast(ops)
        redoStack.clear()
        ops = new
        refresh()
    }

    fun upsert(op: Op) = commit(ops.upserted(op))

    /** Borra la operación y las extrusiones que dependen de ella. */
    fun delete(id: String) =
        commit(ops.filter { it.id != id && !(it is ExtrudeOp && it.sketchId == id) })

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(ops)
        ops = undoStack.removeLast()
        refresh()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(ops)
        ops = redoStack.removeLast()
        refresh()
    }

    private fun refresh() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }
}
