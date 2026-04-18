package com.gagik.terminal.buffer

import com.gagik.terminal.api.*
import com.gagik.terminal.buffer.impl.*
import com.gagik.terminal.engine.CursorEngine
import com.gagik.terminal.engine.MutationEngine
import com.gagik.terminal.engine.TerminalResizer
import com.gagik.terminal.state.TerminalState

internal class TerminalBuffer private constructor(
    private val components: Components
) : TerminalBufferApi,
    TerminalReader by TerminalReaderImpl(components.state),
    TerminalWriter by TerminalWriterImpl(components.state, components.mutationEngine, components.cursorEngine),
    TerminalCursor by TerminalCursorImpl(components.state, components.cursorEngine),
    TerminalModeController by TerminalModeControllerImpl(components.state, components.cursorEngine),
    TerminalInspector by TerminalInspectorImpl(components.state) {

    private val state: TerminalState get() = components.state

    constructor(initialWidth: Int, initialHeight: Int, maxHistory: Int = 1000) : this(
        createComponents(initialWidth, initialHeight, maxHistory)
    )

    override fun resize(newWidth: Int, newHeight: Int) {
        require(newWidth > 0) { "newWidth must be > 0, was $newWidth" }
        require(newHeight > 0) { "newHeight must be > 0, was $newHeight" }

        val oldWidth = state.dimensions.width
        val oldHeight = state.dimensions.height

        if (newWidth == oldWidth && newHeight == oldHeight) return

        // 1. Reflow the primary screen and build a new ClusterStore
        TerminalResizer.resizeBuffer(state.primaryBuffer, oldWidth, oldHeight, newWidth, newHeight)

        // 2. Wipe and resize the alt screen
        state.altBuffer.replaceStorage(newWidth, newHeight, state.pen.currentAttr)

        // 3. Update global state dimensions and margins
        state.dimensions.width = newWidth
        state.dimensions.height = newHeight
        state.tabStops.resize(newWidth)

        // 4. Update the active margin bounds safely
        if (state.activeBuffer.scrollBottom >= newHeight) {
            state.activeBuffer.resetScrollRegion(newHeight)
        }
        state.activeBuffer.cursor.pendingWrap = false
    }

    override fun reset() {
        if (state.isAltScreenActive) {
            this.exitAltBuffer()
        }
        clearAll()
        state.activeBuffer.resetScrollRegion(state.dimensions.height)
        state.modes.reset()
        state.tabStops.resetToDefault()
    }

    private data class Components(
        val state: TerminalState,
        val mutationEngine: MutationEngine,
        val cursorEngine: CursorEngine
    )

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