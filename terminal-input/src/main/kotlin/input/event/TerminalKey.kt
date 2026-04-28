package com.gagik.terminal.input.event

/**
 * Non-printable terminal keys accepted by the input encoder.
 *
 * Printable text is represented as a Unicode scalar on [TerminalKeyEvent],
 * never as a `TerminalKey` enum value.
 */
enum class TerminalKey {
    /** Cursor-up key. */
    UP,

    /** Cursor-down key. */
    DOWN,

    /** Cursor-left key. */
    LEFT,

    /** Cursor-right key. */
    RIGHT,

    /** Home key. */
    HOME,

    /** End key. */
    END,

    /** Page-up key. */
    PAGE_UP,

    /** Page-down key. */
    PAGE_DOWN,

    /** Insert key. */
    INSERT,

    /** Delete key. */
    DELETE,

    /** Backspace key. */
    BACKSPACE,

    /** Main keyboard enter key. */
    ENTER,

    /** Tab key. */
    TAB,

    /** Escape key. */
    ESCAPE,

    /** Function key F1. */
    F1,

    /** Function key F2. */
    F2,

    /** Function key F3. */
    F3,

    /** Function key F4. */
    F4,

    /** Function key F5. */
    F5,

    /** Function key F6. */
    F6,

    /** Function key F7. */
    F7,

    /** Function key F8. */
    F8,

    /** Function key F9. */
    F9,

    /** Function key F10. */
    F10,

    /** Function key F11. */
    F11,

    /** Function key F12. */
    F12,

    /** Numeric keypad enter key. */
    NUMPAD_ENTER,

    /** Numeric keypad divide key. */
    NUMPAD_DIVIDE,

    /** Numeric keypad multiply key. */
    NUMPAD_MULTIPLY,

    /** Numeric keypad subtract key. */
    NUMPAD_SUBTRACT,

    /** Numeric keypad add key. */
    NUMPAD_ADD,

    /** Numeric keypad decimal key. */
    NUMPAD_DECIMAL,

    /** Numeric keypad 0 key. */
    NUMPAD_0,

    /** Numeric keypad 1 key. */
    NUMPAD_1,

    /** Numeric keypad 2 key. */
    NUMPAD_2,

    /** Numeric keypad 3 key. */
    NUMPAD_3,

    /** Numeric keypad 4 key. */
    NUMPAD_4,

    /** Numeric keypad 5 key. */
    NUMPAD_5,

    /** Numeric keypad 6 key. */
    NUMPAD_6,

    /** Numeric keypad 7 key. */
    NUMPAD_7,

    /** Numeric keypad 8 key. */
    NUMPAD_8,

    /** Numeric keypad 9 key. */
    NUMPAD_9,
}
