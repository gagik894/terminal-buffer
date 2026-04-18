package com.gagik.terminal.buffer.impl

import com.gagik.terminal.engine.CursorEngine
import com.gagik.terminal.state.TerminalState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TerminalModeControllerImplTest {

    @Test
    fun `updates mode flags in the shared state`() {
        val state = TerminalState(4, 3, 2)
        val modeController = TerminalModeControllerImpl(state, CursorEngine(state))

        modeController.setInsertMode(true)
        modeController.setApplicationCursorKeys(true)
        modeController.setTreatAmbiguousAsWide(true)

        assertAll(
            { assertEquals(true, state.modes.isInsertMode) },
            { assertEquals(true, state.modes.isApplicationCursorKeys) },
            { assertEquals(true, state.modes.treatAmbiguousAsWide) }
        )
    }

    @Test
    fun `auto wrap off clears pending wrap and origin mode homes to the scroll region`() {
        val state = TerminalState(5, 4, 2)
        val modeController = TerminalModeControllerImpl(state, CursorEngine(state))

        state.cursor.pendingWrap = true
        state.activeBuffer.setScrollRegion(
            top = 2,
            bottom = 3,
            isOriginMode = false,
            viewportHeight = state.dimensions.height
        )
        modeController.setAutoWrap(false)
        modeController.setOriginMode(true)

        assertAll(
            { assertEquals(false, state.modes.isAutoWrap) },
            { assertEquals(false, state.cursor.pendingWrap) },
            { assertEquals(1, state.cursor.row) },
            { assertEquals(0, state.cursor.col) },
            { assertEquals(true, state.modes.isOriginMode) }
        )
    }

    @Test
    fun `enterAltBuffer switches to alt screen and exit restores cursor and pen but NOT global modes`() {
        val state = TerminalState(6, 4, 2)
        val modeController = TerminalModeControllerImpl(state, CursorEngine(state))

        // 1. Setup Primary State
        state.cursor.col = 5
        state.cursor.row = 2
        state.cursor.pendingWrap = true
        state.pen.setAttributes(3, 7, bold = true, italic = true, underline = false)
        val originalAttr = state.pen.currentAttr // Save to verify DECRC later

        state.modes.isInsertMode = true
        state.modes.isAutoWrap = false
        state.primaryBuffer.ring[state.resolveRingIndex(0)].setCell(0, 'P'.code, state.pen.currentAttr)

        // 2. Enter Alt Screen
        modeController.enterAltBuffer()

        assertAll(
            { assertTrue(state.isAltScreenActive) },
            { assertEquals(0, state.cursor.col) },
            { assertEquals(0, state.cursor.row) },
            { assertFalse(state.cursor.pendingWrap) },
            { assertEquals("", state.altBuffer.ring[state.resolveRingIndex(0)].toTextTrimmed()) },
            { assertEquals("P", state.primaryBuffer.ring[0].toTextTrimmed()) }
        )

        // 3. Mutate state while inside Alt Screen
        state.cursor.col = 1
        state.cursor.row = 1
        state.pen.reset()

        // Mutate GLOBAL modes
        state.modes.isInsertMode = false
        state.modes.isAutoWrap = true

        // 4. Exit Alt Screen
        modeController.exitAltBuffer()

        // 5. Verify the strict VT500 DECRC restoration
        assertAll(
            { assertFalse(state.isAltScreenActive) },
            // Cursor geometry MUST be restored
            { assertEquals(5, state.cursor.col) },
            { assertEquals(2, state.cursor.row) },
            { assertTrue(state.cursor.pendingWrap) },
            // Pen MUST be restored
            { assertEquals(originalAttr, state.pen.currentAttr) },
            // Global hardware modes MUST NOT be restored (they keep the mutations from step 3)
            { assertFalse(state.modes.isInsertMode, "IRM is global and should not revert") },
            { assertTrue(state.modes.isAutoWrap, "DECAWM is global and should not revert") },

            { assertEquals('P'.code, state.primaryBuffer.ring[state.resolveRingIndex(0)].getCodepoint(0)) }
        )
    }

    @Test
    fun `enterAltBuffer and exitAltBuffer are no-op when already in target buffer`() {
        val state = TerminalState(5, 3, 1)
        val modeController = TerminalModeControllerImpl(state, CursorEngine(state))

        modeController.exitAltBuffer()
        assertFalse(state.isAltScreenActive)

        modeController.enterAltBuffer()
        state.altBuffer.ring[state.resolveRingIndex(0)].setCell(0, 'A'.code, state.pen.currentAttr)
        modeController.enterAltBuffer()

        assertAll(
            { assertTrue(state.isAltScreenActive) },
            { assertEquals('A'.code, state.altBuffer.ring[state.resolveRingIndex(0)].getCodepoint(0)) }
        )
    }
}

