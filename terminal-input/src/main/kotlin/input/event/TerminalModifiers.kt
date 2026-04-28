package com.gagik.terminal.input.event

/**
 * Bit mask vocabulary for UI keyboard modifiers accepted by terminal input.
 *
 * These are internal encoder bits, not CSI modifier parameter values. CSI
 * encoding applies the `1 + modifiers` translation only at the wire boundary.
 */
object TerminalModifiers {
    /** No keyboard modifiers are active. */
    const val NONE: Int = 0

    /** Shift is active. */
    const val SHIFT: Int = 1 shl 0

    /** Alt is active. */
    const val ALT: Int = 1 shl 1

    /** Control is active. */
    const val CTRL: Int = 1 shl 2

    /** Meta or command is active. */
    const val META: Int = 1 shl 3

    /** Mask containing every supported modifier bit. */
    const val VALID_MASK: Int = SHIFT or ALT or CTRL or META

    /**
     * Returns true when Shift is present in [modifiers].
     */
    fun hasShift(modifiers: Int): Boolean = (modifiers and SHIFT) != 0

    /**
     * Returns true when Alt is present in [modifiers].
     */
    fun hasAlt(modifiers: Int): Boolean = (modifiers and ALT) != 0

    /**
     * Returns true when Control is present in [modifiers].
     */
    fun hasCtrl(modifiers: Int): Boolean = (modifiers and CTRL) != 0

    /**
     * Returns true when Meta is present in [modifiers].
     */
    fun hasMeta(modifiers: Int): Boolean = (modifiers and META) != 0

    /**
     * Returns true when [modifiers] contains only supported modifier bits.
     */
    fun isValid(modifiers: Int): Boolean {
        return (modifiers and VALID_MASK.inv()) == 0
    }

    /**
     * Converts internal modifier bits to an xterm-style CSI modifier parameter.
     *
     * @throws IllegalArgumentException when [modifiers] contains unsupported
     * bits.
     */
    fun toCsiModifierParam(modifiers: Int): Int {
        require(isValid(modifiers)) { "invalid modifier bitmask: $modifiers" }
        return 1 + modifiers
    }
}
