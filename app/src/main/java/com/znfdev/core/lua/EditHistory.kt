package com.znfdev.core.lua

/**
 * Undo/redo for the code editor. Rapid single-character typing is merged into one
 * step so a word (or a burst of typing) undoes at once.
 */
class EditHistory(private val limit: Int = 200, private val mergeWindowMs: Long = 800) {

    private val undoStack = ArrayList<Edit>()
    private val redoStack = ArrayList<Edit>()
    private var lastAt = 0L
    private var lastWasTyping = false

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    fun record(before: Edit, after: Edit, nowMs: Long) {
        if (before.text == after.text) return
        val typing = after.text.length == before.text.length + 1
        val merge = typing && lastWasTyping && nowMs - lastAt < mergeWindowMs && undoStack.isNotEmpty()
        if (!merge) {
            undoStack.add(before)
            if (undoStack.size > limit) undoStack.removeAt(0)
        }
        redoStack.clear()
        lastAt = nowMs
        lastWasTyping = typing
    }

    fun undo(current: Edit): Edit? {
        if (undoStack.isEmpty()) return null
        val prev = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(current)
        lastWasTyping = false
        return prev
    }

    fun redo(current: Edit): Edit? {
        if (redoStack.isEmpty()) return null
        val next = redoStack.removeAt(redoStack.size - 1)
        undoStack.add(current)
        lastWasTyping = false
        return next
    }
}
