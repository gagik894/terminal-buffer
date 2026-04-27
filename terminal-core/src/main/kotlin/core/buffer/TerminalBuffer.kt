package com.gagik.core.buffer

import com.gagik.core.api.*
import com.gagik.core.buffer.impl.*
import com.gagik.core.engine.CursorEngine
import com.gagik.core.engine.MutationEngine
import com.gagik.core.engine.TerminalResizer
import com.gagik.core.model.SavedCursorState
import com.gagik.core.state.TerminalState

/**
 * Concrete facade for the terminal-buffer core.
 *
 * The facade stays intentionally thin: focused adapters implement the public
 * roles while the hot mutation and cursor logic remain in dedicated engines.
 *
 * Cross-cutting responsibilities owned here:
 * - resize orchestration across both screen buffers
 * - full terminal reset (RIS)
 */
internal class TerminalBuffer private constructor(
    private val components: Components
) : TerminalBufferApi,
    TerminalReader by TerminalReaderImpl(components.state),
    TerminalWriter by TerminalWriterImpl(components.state, components.mutationEngine, components.cursorEngine),
    TerminalCursor by TerminalCursorImpl(components.state, components.cursorEngine),
    TerminalModeController by TerminalModeControllerImpl(components.state, components.cursorEngine),
    TerminalModeReader by TerminalModeReaderImpl(components.state),
    TerminalInspector by TerminalInspectorImpl(components.state) {

    private val state: TerminalState
        get() = components.state

    constructor(initialWidth: Int, initialHeight: Int, maxHistory: Int = 1000) : this(
        createComponents(initialWidth, initialHeight, maxHistory)
    )

    /**
     * Reflows the primary screen, recreates the alternate screen, updates global
     * dimensions and tab stops, then restores invariants that must hold even for
     * the currently inactive buffer.
     */
    override fun resize(newWidth: Int, newHeight: Int) {
        require(newWidth > 0) { "newWidth must be > 0, was $newWidth" }
        require(newHeight > 0) { "newHeight must be > 0, was $newHeight" }

        val oldWidth = state.dimensions.width
        val oldHeight = state.dimensions.height

        if (newWidth == oldWidth && newHeight == oldHeight) return

        TerminalResizer.resizeBuffer(state.primaryBuffer, oldWidth, oldHeight, newWidth, newHeight)
        state.altBuffer.replaceStorage(newWidth, newHeight, state.pen.blankAttr)

        state.dimensions.width = newWidth
        state.dimensions.height = newHeight
        state.tabStops.resize(newWidth)

        state.primaryBuffer.resetScrollRegion(newHeight)
        state.altBuffer.resetScrollRegion(newHeight)
        state.primaryBuffer.resetLeftRightMargins(newWidth)
        state.altBuffer.resetLeftRightMargins(newWidth)
        state.primaryBuffer.clampSavedCursorToBounds(newWidth, newHeight)
        state.altBuffer.clampSavedCursorToBounds(newWidth, newHeight)
        state.cancelPendingWrap()
    }

    override fun reset() {
        if (state.isAltScreenActive) {
            this.exitAltBuffer()
        }
        clearAll()
        state.activeBuffer.resetScrollRegion(state.dimensions.height)
        state.primaryBuffer.resetLeftRightMargins(state.dimensions.width)
        state.altBuffer.resetLeftRightMargins(state.dimensions.width)
        state.modes.reset()
        state.tabStops.resetToDefault()
    }

    override fun executeDeccolm(newWidth: Int) {
        if (newWidth != 80 && newWidth != 132) return

        val primarySaved = SavedCursorSnapshot.from(state.primaryBuffer.savedCursor)
        val altSaved = SavedCursorSnapshot.from(state.altBuffer.savedCursor)

        resize(newWidth, state.dimensions.height)
        components.mutationEngine.deccolmReset(newWidth)

        primarySaved.restoreInto(state.primaryBuffer.savedCursor)
        altSaved.restoreInto(state.altBuffer.savedCursor)
    }

    private data class Components(
        val state: TerminalState,
        val mutationEngine: MutationEngine,
        val cursorEngine: CursorEngine
    )

    private data class SavedCursorSnapshot(
        val col: Int,
        val row: Int,
        val attr: Long,
        val pendingWrap: Boolean,
        val isOriginMode: Boolean,
        val isSaved: Boolean
    ) {
        fun restoreInto(target: SavedCursorState) {
            target.col = col
            target.row = row
            target.attr = attr
            target.pendingWrap = pendingWrap
            target.isOriginMode = isOriginMode
            target.isSaved = isSaved
        }

        companion object {
            fun from(source: SavedCursorState): SavedCursorSnapshot {
                return SavedCursorSnapshot(
                    col = source.col,
                    row = source.row,
                    attr = source.attr,
                    pendingWrap = source.pendingWrap,
                    isOriginMode = source.isOriginMode,
                    isSaved = source.isSaved
                )
            }
        }
    }

    private companion object {
        fun createComponents(initialWidth: Int, initialHeight: Int, maxHistory: Int): Components {
            val state = TerminalState(initialWidth, initialHeight, maxHistory)
            return Components(
                state = state,
                mutationEngine = MutationEngine(state),
                cursorEngine = CursorEngine(state)
            )
        }
    }
}
