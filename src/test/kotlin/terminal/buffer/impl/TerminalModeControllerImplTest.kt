package com.gagik.terminal.buffer.impl

import com.gagik.terminal.state.TerminalState
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TerminalModeControllerImplTest {

    @Test
    fun `updates mode flags in the shared state`() {
        val state = TerminalState(4, 3, 2)
        val modeController = TerminalModeControllerImpl(state)

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
        val modeController = TerminalModeControllerImpl(state)

        state.cursor.pendingWrap = true
        state.setScrollRegion(2, 3)
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
}

