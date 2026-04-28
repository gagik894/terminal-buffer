package com.gagik.terminal.input.event

/**
 * One platform-neutral mouse event accepted by the terminal input encoder.
 *
 * Coordinates are zero-based grid cell coordinates. The encoder converts them
 * to the terminal protocol's one-based coordinates at the wire boundary.
 *
 * @property column zero-based cell column.
 * @property row zero-based cell row.
 * @property button button or wheel direction associated with this event.
 * @property type mouse event family.
 * @property modifiers active keyboard modifiers using [TerminalModifiers] bits.
 */
data class TerminalMouseEvent(
    val column: Int,
    val row: Int,
    val button: TerminalMouseButton,
    val type: TerminalMouseEventType,
    val modifiers: Int = TerminalModifiers.NONE,
) {
    init {
        require(column >= 0) { "column must be non-negative: $column" }
        require(row >= 0) { "row must be non-negative: $row" }
        require(TerminalModifiers.isValid(modifiers)) {
            "invalid modifier bitmask: $modifiers"
        }

        when (type) {
            TerminalMouseEventType.PRESS -> {
                require(button != TerminalMouseButton.NONE) {
                    "PRESS requires a concrete button"
                }
                require(!button.isWheel()) {
                    "wheel buttons must use WHEEL event type"
                }
            }

            TerminalMouseEventType.RELEASE -> {
                require(!button.isWheel()) {
                    "wheel buttons must use WHEEL event type"
                }
            }

            TerminalMouseEventType.MOTION -> {
                require(!button.isWheel()) {
                    "wheel buttons must use WHEEL event type"
                }
            }

            TerminalMouseEventType.WHEEL -> {
                require(button.isWheel()) {
                    "WHEEL requires a wheel button"
                }
            }
        }
    }
}
