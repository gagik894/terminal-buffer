package com.gagik.terminal.protocol

/**
 * Xterm modifyOtherKeys mode values stored in core's packed input-mode word.
 */
object ModifyOtherKeysMode {
    /** Do not use modifyOtherKeys encoding. */
    const val DISABLED: Int = 0

    /** Encode ordinary modified keys whose legacy representation is ambiguous or missing. */
    const val MODE_1: Int = 1

    /** Encode ordinary modified keys plus xterm's Tab/Enter control-equivalent exceptions. */
    const val MODE_2: Int = 2
}
