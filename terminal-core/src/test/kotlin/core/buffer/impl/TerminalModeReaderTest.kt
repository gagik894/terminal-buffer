package com.gagik.core.buffer.impl

import com.gagik.core.TerminalBuffers
import com.gagik.terminal.protocol.MouseEncodingMode
import com.gagik.terminal.protocol.MouseTrackingMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TerminalModeReaderTest {

    @Test
    fun `exposes stable snapshot`() {
        val buffer = TerminalBuffers.create(width = 4, height = 3, maxHistory = 2)

        val snapshot = buffer.getModeSnapshot()

        assertAll(
            { assertFalse(snapshot.isInsertMode) },
            { assertTrue(snapshot.isAutoWrap) },
            { assertFalse(snapshot.isApplicationCursorKeys) },
            { assertFalse(snapshot.isApplicationKeypad) },
            { assertFalse(snapshot.isOriginMode) },
            { assertFalse(snapshot.isNewLineMode) },
            { assertFalse(snapshot.isLeftRightMarginMode) },
            { assertFalse(snapshot.isReverseVideo) },
            { assertTrue(snapshot.isCursorVisible) },
            { assertFalse(snapshot.isCursorBlinking) },
            { assertFalse(snapshot.isBracketedPasteEnabled) },
            { assertFalse(snapshot.isFocusReportingEnabled) },
            { assertFalse(snapshot.treatAmbiguousAsWide) },
            { assertEquals(MouseTrackingMode.OFF, snapshot.mouseTrackingMode) },
            { assertEquals(MouseEncodingMode.DEFAULT, snapshot.mouseEncodingMode) },
            { assertEquals(0, snapshot.modifyOtherKeysMode) }
        )
    }

    @Test
    fun `reflects changes without leaking mutability`() {
        val buffer = TerminalBuffers.create(width = 4, height = 3, maxHistory = 2)
        val before = buffer.getModeSnapshot()

        buffer.setNewLineMode(true)
        buffer.setApplicationKeypad(true)
        buffer.setMouseTrackingMode(MouseTrackingMode.ANY_EVENT)
        buffer.setMouseEncodingMode(MouseEncodingMode.URXVT)
        buffer.setBracketedPasteEnabled(true)
        buffer.setFocusReportingEnabled(true)
        buffer.setModifyOtherKeysMode(2)
        buffer.setReverseVideo(true)
        buffer.setCursorVisible(false)
        buffer.setCursorBlinking(true)

        val after = buffer.getModeSnapshot()

        assertAll(
            { assertFalse(before.isNewLineMode) },
            { assertFalse(before.isApplicationKeypad) },
            { assertEquals(MouseTrackingMode.OFF, before.mouseTrackingMode) },
            { assertEquals(MouseEncodingMode.DEFAULT, before.mouseEncodingMode) },
            { assertFalse(before.isBracketedPasteEnabled) },
            { assertFalse(before.isFocusReportingEnabled) },
            { assertEquals(0, before.modifyOtherKeysMode) },
            { assertFalse(before.isReverseVideo) },
            { assertTrue(before.isCursorVisible) },
            { assertFalse(before.isCursorBlinking) },
            { assertTrue(after.isNewLineMode) },
            { assertTrue(after.isApplicationKeypad) },
            { assertEquals(MouseTrackingMode.ANY_EVENT, after.mouseTrackingMode) },
            { assertEquals(MouseEncodingMode.URXVT, after.mouseEncodingMode) },
            { assertTrue(after.isBracketedPasteEnabled) },
            { assertTrue(after.isFocusReportingEnabled) },
            { assertEquals(2, after.modifyOtherKeysMode) },
            { assertTrue(after.isReverseVideo) },
            { assertFalse(after.isCursorVisible) },
            { assertTrue(after.isCursorBlinking) }
        )
    }
}
