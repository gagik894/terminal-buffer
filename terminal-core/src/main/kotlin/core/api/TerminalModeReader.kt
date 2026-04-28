package com.gagik.core.api

import com.gagik.terminal.protocol.MouseEncodingMode
import com.gagik.terminal.protocol.MouseTrackingMode

/**
 * Immutable snapshot of the terminal's durable mode state.
 *
 * This is the handoff contract for parser, input, and UI layers that need to
 * read mode flags without mutating the core's internal `TerminalModes`
 * instance.
 */
data class TerminalModeSnapshot(
    val isInsertMode: Boolean,
    val isAutoWrap: Boolean,
    val isApplicationCursorKeys: Boolean,
    val isApplicationKeypad: Boolean,
    val isOriginMode: Boolean,
    val isNewLineMode: Boolean,
    val isLeftRightMarginMode: Boolean,
    val isReverseVideo: Boolean,
    val isCursorVisible: Boolean,
    val isCursorBlinking: Boolean,
    val isBracketedPasteEnabled: Boolean,
    val isFocusReportingEnabled: Boolean,
    val treatAmbiguousAsWide: Boolean,
    val mouseTrackingMode: MouseTrackingMode,
    val mouseEncodingMode: MouseEncodingMode,
    val modifyOtherKeysMode: Int
)

/**
 * Read-only public access to durable terminal mode state.
 *
 * Intended for parser, input, and UI handoff. The returned snapshot is
 * immutable and detached from internal storage, so callers cannot mutate core
 * state accidentally.
 */
interface TerminalModeReader {

    /**
     * Returns one atomic packed snapshot of durable mode state.
     *
     * The current bit layout is owned by core and should be treated as opaque
     * outside optimized input/render handoff code. General callers should
     * prefer [getModeSnapshot].
     */
    fun getModeBitsSnapshot(): Long

    /** Returns an immutable snapshot of the current durable mode flags. */
    fun getModeSnapshot(): TerminalModeSnapshot
}
