package com.gagik.core.model

/**
 * Pre-allocated mutable slot for DECSC/DECRC (ESC 7 / ESC 8).
 *
 * Mutated in-place on every save to produce zero heap allocation.
 * [isSaved] is false until the first DECSC is issued.
 */
internal class SavedCursorState {
    var col: Int = 0
    var row: Int = 0
    var attr: Long = 0
    var pendingWrap: Boolean = false
    var isOriginMode: Boolean = false
    var isSaved: Boolean = false

    fun clear() {
        col = 0
        row = 0
        attr = 0
        pendingWrap = false
        isOriginMode = false
        isSaved = false
    }
}
