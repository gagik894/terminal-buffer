package com.gagik.core.model

import com.gagik.core.api.TerminalModeSnapshot
import com.gagik.terminal.protocol.MouseEncodingMode
import com.gagik.terminal.protocol.MouseTrackingMode
import java.util.concurrent.atomic.AtomicLong

/**
 * Atomic storage for durable terminal behavioral modes.
 *
 * The grid, cursor, and history remain single-writer core state. This packed
 * word exists so input/render decisions can take a coherent mode snapshot
 * without reading unrelated mutable core structures.
 */
internal class TerminalModes {

    private val modeBits = AtomicLong(DEFAULT_MODE_BITS)

    /** Mode 4: Insert/Replace Mode (IRM). False = replace (default), true = insert. */
    var isInsertMode: Boolean
        get() = hasFlag(currentBits, INSERT_MODE)
        set(value) = setFlag(INSERT_MODE, value)

    /** Mode 7: Auto-Wrap Mode (DECAWM). True = wrap at right margin (default). */
    var isAutoWrap: Boolean
        get() = hasFlag(currentBits, AUTO_WRAP)
        set(value) = setFlag(AUTO_WRAP, value)

    /** Mode 1: Application Cursor Keys (DECCKM). False = normal cursor keys (default). */
    var isApplicationCursorKeys: Boolean
        get() = hasFlag(currentBits, APPLICATION_CURSOR_KEYS)
        set(value) = setFlag(APPLICATION_CURSOR_KEYS, value)

    /** DECNKM application keypad mode. False = numeric keypad (default). */
    var isApplicationKeypad: Boolean
        get() = hasFlag(currentBits, APPLICATION_KEYPAD)
        set(value) = setFlag(APPLICATION_KEYPAD, value)

    /** Mode 6: Origin Mode (DECOM). False = absolute, true = relative to scroll region. */
    var isOriginMode: Boolean
        get() = hasFlag(currentBits, ORIGIN_MODE)
        set(value) = setFlag(ORIGIN_MODE, value)

    /** Mode 20: New Line Mode (LNM). False = LF only, true = LF also performs CR. */
    var isNewLineMode: Boolean
        get() = hasFlag(currentBits, NEW_LINE_MODE)
        set(value) = setFlag(NEW_LINE_MODE, value)

    /**
     * DEC left/right margin mode (DECLRMM, `CSI ? 69 h`).
     * False = full-width semantics (default).
     */
    var isLeftRightMarginMode: Boolean
        get() = hasFlag(currentBits, LEFT_RIGHT_MARGIN_MODE)
        set(value) = setFlag(LEFT_RIGHT_MARGIN_MODE, value)

    /** Reverse-video presentation flag (DECSCNM). False = normal video (default). */
    var isReverseVideo: Boolean
        get() = hasFlag(currentBits, REVERSE_VIDEO)
        set(value) = setFlag(REVERSE_VIDEO, value)

    /** Cursor visibility presentation flag. True = cursor visible (default). */
    var isCursorVisible: Boolean
        get() = hasFlag(currentBits, CURSOR_VISIBLE)
        set(value) = setFlag(CURSOR_VISIBLE, value)

    /** Cursor blink presentation flag. False = steady cursor (default). */
    var isCursorBlinking: Boolean
        get() = hasFlag(currentBits, CURSOR_BLINKING)
        set(value) = setFlag(CURSOR_BLINKING, value)

    /** Bracketed paste reporting mode. False = disabled (default). */
    var isBracketedPasteEnabled: Boolean
        get() = hasFlag(currentBits, BRACKETED_PASTE)
        set(value) = setFlag(BRACKETED_PASTE, value)

    /** Focus in/out reporting mode. False = disabled (default). */
    var isFocusReportingEnabled: Boolean
        get() = hasFlag(currentBits, FOCUS_REPORTING)
        set(value) = setFlag(FOCUS_REPORTING, value)

    /**
     * Whether ambiguous-width Unicode characters are treated as wide (2-cell).
     * False = treat as narrow (default).
     */
    var treatAmbiguousAsWide: Boolean
        get() = hasFlag(currentBits, AMBIGUOUS_WIDE)
        set(value) = setFlag(AMBIGUOUS_WIDE, value)

    /** Mouse reporting selection. Defaults to [MouseTrackingMode.OFF]. */
    var mouseTrackingMode: MouseTrackingMode
        get() = decodeMouseTracking(currentBits)
        set(value) = setPacked(MOUSE_TRACKING_MASK, MOUSE_TRACKING_SHIFT, value.ordinal)

    /** Mouse encoding selection. Defaults to [MouseEncodingMode.DEFAULT]. */
    var mouseEncodingMode: MouseEncodingMode
        get() = decodeMouseEncoding(currentBits)
        set(value) = setPacked(MOUSE_ENCODING_MASK, MOUSE_ENCODING_SHIFT, value.ordinal)

    /**
     * Modify-other-keys level.
     *
     * `0` means disabled. Values are clamped to the packed 3-bit range until a
     * wider input contract needs more states.
     */
    var modifyOtherKeysMode: Int
        get() = ((currentBits and MODIFY_OTHER_KEYS_MASK) ushr MODIFY_OTHER_KEYS_SHIFT).toInt()
        set(value) = setPacked(MODIFY_OTHER_KEYS_MASK, MODIFY_OTHER_KEYS_SHIFT, value.coerceIn(0, 7))

    private val currentBits: Long
        get() = modeBits.get()

    /** Returns a coherent packed mode word for future input-side fast paths. */
    fun getModeBitsSnapshot(): Long = modeBits.get()

    /** Returns an immutable typed snapshot decoded from one atomic read. */
    fun getModeSnapshot(): TerminalModeSnapshot {
        val bits = modeBits.get()
        return TerminalModeSnapshot(
            isInsertMode = hasFlag(bits, INSERT_MODE),
            isAutoWrap = hasFlag(bits, AUTO_WRAP),
            isApplicationCursorKeys = hasFlag(bits, APPLICATION_CURSOR_KEYS),
            isApplicationKeypad = hasFlag(bits, APPLICATION_KEYPAD),
            isOriginMode = hasFlag(bits, ORIGIN_MODE),
            isNewLineMode = hasFlag(bits, NEW_LINE_MODE),
            isLeftRightMarginMode = hasFlag(bits, LEFT_RIGHT_MARGIN_MODE),
            isReverseVideo = hasFlag(bits, REVERSE_VIDEO),
            isCursorVisible = hasFlag(bits, CURSOR_VISIBLE),
            isCursorBlinking = hasFlag(bits, CURSOR_BLINKING),
            isBracketedPasteEnabled = hasFlag(bits, BRACKETED_PASTE),
            isFocusReportingEnabled = hasFlag(bits, FOCUS_REPORTING),
            treatAmbiguousAsWide = hasFlag(bits, AMBIGUOUS_WIDE),
            mouseTrackingMode = decodeMouseTracking(bits),
            mouseEncodingMode = decodeMouseEncoding(bits),
            modifyOtherKeysMode = ((bits and MODIFY_OTHER_KEYS_MASK) ushr MODIFY_OTHER_KEYS_SHIFT).toInt()
        )
    }

    /**
     * Resets all modes to their VT/xterm-style defaults.
     * Called on hard reset.
     */
    fun reset() {
        modeBits.set(DEFAULT_MODE_BITS)
    }

    /**
     * Applies DECSTR soft-reset mode defaults.
     *
     * DECSTR is not a full terminal reset. It resets host-controlled soft modes
     * and input-facing protocol flags while preserving the core width policy.
     */
    fun softReset() {
        val preserved = modeBits.get() and SOFT_RESET_PRESERVE_MASK
        modeBits.set(preserved or SOFT_RESET_MODE_BITS)
    }

    private fun setFlag(flag: Long, enabled: Boolean) {
        while (true) {
            val old = modeBits.get()
            val new = if (enabled) old or flag else old and flag.inv()
            if (old == new || modeBits.compareAndSet(old, new)) return
        }
    }

    private fun setPacked(mask: Long, shift: Int, value: Int) {
        val packed = value.toLong() shl shift
        while (true) {
            val old = modeBits.get()
            val new = (old and mask.inv()) or packed
            if (old == new || modeBits.compareAndSet(old, new)) return
        }
    }

    private companion object {
        private const val INSERT_MODE: Long = 1L shl 0
        private const val AUTO_WRAP: Long = 1L shl 1
        private const val APPLICATION_CURSOR_KEYS: Long = 1L shl 2
        private const val APPLICATION_KEYPAD: Long = 1L shl 3
        private const val ORIGIN_MODE: Long = 1L shl 4
        private const val NEW_LINE_MODE: Long = 1L shl 5
        private const val LEFT_RIGHT_MARGIN_MODE: Long = 1L shl 6
        private const val REVERSE_VIDEO: Long = 1L shl 7
        private const val CURSOR_VISIBLE: Long = 1L shl 8
        private const val CURSOR_BLINKING: Long = 1L shl 9
        private const val BRACKETED_PASTE: Long = 1L shl 10
        private const val FOCUS_REPORTING: Long = 1L shl 11
        private const val AMBIGUOUS_WIDE: Long = 1L shl 12

        private const val MODIFY_OTHER_KEYS_SHIFT: Int = 16
        private const val MODIFY_OTHER_KEYS_MASK: Long = 0b111L shl MODIFY_OTHER_KEYS_SHIFT

        private const val MOUSE_TRACKING_SHIFT: Int = 20
        private const val MOUSE_TRACKING_MASK: Long = 0b1111L shl MOUSE_TRACKING_SHIFT

        private const val MOUSE_ENCODING_SHIFT: Int = 24
        private const val MOUSE_ENCODING_MASK: Long = 0b1111L shl MOUSE_ENCODING_SHIFT

        private const val DEFAULT_MODE_BITS: Long = AUTO_WRAP or CURSOR_VISIBLE
        private const val SOFT_RESET_PRESERVE_MASK: Long = AMBIGUOUS_WIDE
        private const val SOFT_RESET_MODE_BITS: Long = DEFAULT_MODE_BITS

        private fun hasFlag(bits: Long, flag: Long): Boolean = (bits and flag) != 0L

        private fun decodeMouseTracking(bits: Long): MouseTrackingMode {
            val ordinal = ((bits and MOUSE_TRACKING_MASK) ushr MOUSE_TRACKING_SHIFT).toInt()
            return MouseTrackingMode.entries.getOrElse(ordinal) { MouseTrackingMode.OFF }
        }

        private fun decodeMouseEncoding(bits: Long): MouseEncodingMode {
            val ordinal = ((bits and MOUSE_ENCODING_MASK) ushr MOUSE_ENCODING_SHIFT).toInt()
            return MouseEncodingMode.entries.getOrElse(ordinal) { MouseEncodingMode.DEFAULT }
        }
    }
}
