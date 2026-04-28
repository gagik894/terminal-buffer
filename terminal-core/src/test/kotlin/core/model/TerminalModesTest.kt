package com.gagik.core.model

import com.gagik.terminal.protocol.MouseEncodingMode
import com.gagik.terminal.protocol.MouseTrackingMode
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
            { assertEquals(MouseTrackingMode.OFF, modes.mouseTrackingMode) },
            { assertEquals(MouseEncodingMode.DEFAULT, modes.mouseEncodingMode) },
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
        modes.mouseTrackingMode = MouseTrackingMode.ANY_EVENT
        modes.mouseEncodingMode = MouseEncodingMode.SGR
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
            { assertEquals(MouseTrackingMode.OFF, modes.mouseTrackingMode) },
            { assertEquals(MouseEncodingMode.DEFAULT, modes.mouseEncodingMode) },
            { assertEquals(0, modes.modifyOtherKeysMode) }
        )
    }

    @Test
    fun `typed snapshot is decoded from current packed mode word`() {
        val modes = TerminalModes()

        modes.isApplicationCursorKeys = true
        modes.isApplicationKeypad = true
        modes.isFocusReportingEnabled = true
        modes.isBracketedPasteEnabled = true
        modes.mouseTrackingMode = MouseTrackingMode.BUTTON_EVENT
        modes.mouseEncodingMode = MouseEncodingMode.SGR
        modes.modifyOtherKeysMode = 2

        val bits = modes.getModeBitsSnapshot()
        val snapshot = modes.getModeSnapshot()

        assertAll(
            { assertTrue(bits != 0L) },
            { assertTrue(snapshot.isApplicationCursorKeys) },
            { assertTrue(snapshot.isApplicationKeypad) },
            { assertTrue(snapshot.isFocusReportingEnabled) },
            { assertTrue(snapshot.isBracketedPasteEnabled) },
            { assertEquals(MouseTrackingMode.BUTTON_EVENT, snapshot.mouseTrackingMode) },
            { assertEquals(MouseEncodingMode.SGR, snapshot.mouseEncodingMode) },
            { assertEquals(2, snapshot.modifyOtherKeysMode) }
        )
    }

    @Test
    fun `soft reset applies DECSTR defaults and preserves width policy`() {
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
        modes.mouseTrackingMode = MouseTrackingMode.ANY_EVENT
        modes.mouseEncodingMode = MouseEncodingMode.URXVT
        modes.modifyOtherKeysMode = 2

        modes.softReset()

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
            { assertTrue(modes.treatAmbiguousAsWide) },
            { assertEquals(MouseTrackingMode.OFF, modes.mouseTrackingMode) },
            { assertEquals(MouseEncodingMode.DEFAULT, modes.mouseEncodingMode) },
            { assertEquals(0, modes.modifyOtherKeysMode) }
        )
    }
}
