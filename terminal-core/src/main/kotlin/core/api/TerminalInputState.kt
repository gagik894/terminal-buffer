package com.gagik.core.api

/**
 * Zero-allocation, read-only terminal behavior state required by input encoders.
 */
interface TerminalInputState {

    /**
     * Returns one coherent snapshot of all input-readable terminal mode bits.
     */
    fun getInputModeBits(): Long

    /**
     * Helper methods for decoding packed input mode snapshots.
     */
    companion object {
        /**
         * Returns true when application cursor keys mode is enabled in [bits].
         */
        @JvmStatic
        fun isApplicationCursorKeys(bits: Long): Boolean {
            return TerminalModeBits.hasFlag(bits, TerminalModeBits.APPLICATION_CURSOR_KEYS)
        }

        /**
         * Returns true when application keypad mode is enabled in [bits].
         */
        @JvmStatic
        fun isApplicationKeypad(bits: Long): Boolean {
            return TerminalModeBits.hasFlag(bits, TerminalModeBits.APPLICATION_KEYPAD)
        }

        /**
         * Returns true when new-line mode is enabled in [bits].
         */
        @JvmStatic
        fun isNewLineMode(bits: Long): Boolean {
            return TerminalModeBits.hasFlag(bits, TerminalModeBits.NEW_LINE_MODE)
        }

        /**
         * Returns true when bracketed paste mode is enabled in [bits].
         */
        @JvmStatic
        fun isBracketedPasteEnabled(bits: Long): Boolean {
            return TerminalModeBits.hasFlag(bits, TerminalModeBits.BRACKETED_PASTE)
        }

        /**
         * Returns true when focus reporting mode is enabled in [bits].
         */
        @JvmStatic
        fun isFocusReportingEnabled(bits: Long): Boolean {
            return TerminalModeBits.hasFlag(bits, TerminalModeBits.FOCUS_REPORTING)
        }

        /**
         * Returns the packed mouse tracking mode ordinal from [bits].
         */
        @JvmStatic
        fun mouseTrackingMode(bits: Long): Int {
            return TerminalModeBits.packedValue(
                bits,
                TerminalModeBits.MOUSE_TRACKING_MASK,
                TerminalModeBits.MOUSE_TRACKING_SHIFT,
            )
        }

        /**
         * Returns the packed mouse encoding mode ordinal from [bits].
         */
        @JvmStatic
        fun mouseEncodingMode(bits: Long): Int {
            return TerminalModeBits.packedValue(
                bits,
                TerminalModeBits.MOUSE_ENCODING_MASK,
                TerminalModeBits.MOUSE_ENCODING_SHIFT,
            )
        }

        /**
         * Returns the packed modify-other-keys mode value from [bits].
         */
        @JvmStatic
        fun modifyOtherKeysMode(bits: Long): Int {
            return TerminalModeBits.packedValue(
                bits,
                TerminalModeBits.MODIFY_OTHER_KEYS_MASK,
                TerminalModeBits.MODIFY_OTHER_KEYS_SHIFT,
            )
        }
    }
}
