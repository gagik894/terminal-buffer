package com.gagik.terminal.buffer.impl

import com.gagik.terminal.api.TerminalModeReader
import com.gagik.terminal.api.TerminalModeSnapshot
import com.gagik.terminal.state.TerminalState

internal class TerminalModeReaderImpl(
    private val state: TerminalState
) : TerminalModeReader {

    override fun getModeSnapshot(): TerminalModeSnapshot {
        val modes = state.modes
        return TerminalModeSnapshot(
            isInsertMode = modes.isInsertMode,
            isAutoWrap = modes.isAutoWrap,
            isApplicationCursorKeys = modes.isApplicationCursorKeys,
            isApplicationKeypad = modes.isApplicationKeypad,
            isOriginMode = modes.isOriginMode,
            isNewLineMode = modes.isNewLineMode,
            isLeftRightMarginMode = modes.isLeftRightMarginMode,
            isReverseVideo = modes.isReverseVideo,
            isCursorVisible = modes.isCursorVisible,
            isCursorBlinking = modes.isCursorBlinking,
            isBracketedPasteEnabled = modes.isBracketedPasteEnabled,
            isFocusReportingEnabled = modes.isFocusReportingEnabled,
            treatAmbiguousAsWide = modes.treatAmbiguousAsWide,
            mouseTrackingMode = modes.mouseTrackingMode,
            mouseEncodingMode = modes.mouseEncodingMode,
            modifyOtherKeysMode = modes.modifyOtherKeysMode
        )
    }
}
