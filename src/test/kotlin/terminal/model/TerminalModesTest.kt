package com.gagik.terminal.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TerminalModesTest {

    @Test
    fun `defaults reflect current shared terminal state contract`() {
        val modes = TerminalModes()

        assertAll(
            { assertFalse(modes.isInsertMode) },
            { assertTrue(modes.isAutoWrap) },
            { assertFalse(modes.isApplicationCursorKeys) },
            { assertFalse(modes.isApplicationKeypad) },
            { assertFalse(modes.isOriginMode) },
            { assertFalse(modes.isNewLineMode) },
            { assertFalse(modes.isLeftRightMarginMode) },
            { assertFalse(modes.isReverseVideo) },
            { assertTrue(modes.isCursorVisible) },
            { assertFalse(modes.isCursorBlinking) },
            { assertFalse(modes.isBracketedPasteEnabled) },
            { assertFalse(modes.isFocusReportingEnabled) },
            { assertFalse(modes.treatAmbiguousAsWide) },
            { assertEquals(TerminalModes.MouseTrackingMode.OFF, modes.mouseTrackingMode) },
            { assertEquals(TerminalModes.MouseEncodingMode.DEFAULT, modes.mouseEncodingMode) },
            { assertEquals(0, modes.modifyOtherKeysMode) }
        )
    }

    @Test
    fun `reset restores all shared mode defaults`() {
        val modes = TerminalModes()
        modes.isInsertMode = true
        modes.isAutoWrap = false
        modes.isApplicationCursorKeys = true
        modes.isApplicationKeypad = true
        modes.isOriginMode = true
        modes.isNewLineMode = true
        modes.isLeftRightMarginMode = true
        modes.isReverseVideo = true
        modes.isCursorVisible = false
        modes.isCursorBlinking = true
        modes.isBracketedPasteEnabled = true
        modes.isFocusReportingEnabled = true
        modes.treatAmbiguousAsWide = true
        modes.mouseTrackingMode = TerminalModes.MouseTrackingMode.ANY_EVENT
        modes.mouseEncodingMode = TerminalModes.MouseEncodingMode.SGR
        modes.modifyOtherKeysMode = 2

        modes.reset()

        assertAll(
            { assertFalse(modes.isInsertMode) },
            { assertTrue(modes.isAutoWrap) },
            { assertFalse(modes.isApplicationCursorKeys) },
            { assertFalse(modes.isApplicationKeypad) },
            { assertFalse(modes.isOriginMode) },
            { assertFalse(modes.isNewLineMode) },
            { assertFalse(modes.isLeftRightMarginMode) },
            { assertFalse(modes.isReverseVideo) },
            { assertTrue(modes.isCursorVisible) },
            { assertFalse(modes.isCursorBlinking) },
            { assertFalse(modes.isBracketedPasteEnabled) },
            { assertFalse(modes.isFocusReportingEnabled) },
            { assertFalse(modes.treatAmbiguousAsWide) },
            { assertEquals(TerminalModes.MouseTrackingMode.OFF, modes.mouseTrackingMode) },
            { assertEquals(TerminalModes.MouseEncodingMode.DEFAULT, modes.mouseEncodingMode) },
            { assertEquals(0, modes.modifyOtherKeysMode) }
        )
    }
}
