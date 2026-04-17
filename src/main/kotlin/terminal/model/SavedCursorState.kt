package com.gagik.terminal.model

/**
 * Pre-allocated mutable slot for DECSC/DECRC (ESC 7 / ESC 8).
 *
 * Mutated in-place on every save to produce zero heap allocation.
 * [isSaved] is false until the first DECSC is issued.
 *
 * TODO: When alt screen is implemented, a second independent instance is needed —
 *       one for the normal screen and one for the alt screen.
 */
internal class SavedCursorState {
    var col: Int = 0
    var row: Int = 0
    var attr: Int = 0
    var isSaved: Boolean = false

    fun clear() {
        col = 0
        row = 0
        attr = 0
        isSaved = false
    }
}