package com.gagik.terminal.input.event

/**
 * Mouse event family accepted by the terminal input encoder.
 */
enum class TerminalMouseEventType {
    /** Concrete button press. */
    PRESS,

    /** Button release. */
    RELEASE,

    /** Pointer motion in a mouse tracking mode. */
    MOTION,

    /** Wheel step. */
    WHEEL,
}
