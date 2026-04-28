package com.gagik.terminal.input.event

/**
 * Platform-neutral mouse button vocabulary for terminal input reporting.
 */
enum class TerminalMouseButton {
    /** Primary pointer button. */
    LEFT,

    /** Middle pointer button. */
    MIDDLE,

    /** Secondary pointer button. */
    RIGHT,

    /** Wheel step upward. */
    WHEEL_UP,

    /** Wheel step downward. */
    WHEEL_DOWN,

    /** Wheel step leftward. */
    WHEEL_LEFT,

    /** Wheel step rightward. */
    WHEEL_RIGHT,

    /**
     * Used for motion events with no button pressed, especially ANY_EVENT
     * tracking.
     */
    NONE,
}

/**
 * Returns true when this button represents a wheel report.
 */
fun TerminalMouseButton.isWheel(): Boolean {
    return this == TerminalMouseButton.WHEEL_UP ||
        this == TerminalMouseButton.WHEEL_DOWN ||
        this == TerminalMouseButton.WHEEL_LEFT ||
        this == TerminalMouseButton.WHEEL_RIGHT
}
