package com.gagik.terminal.protocol

/**
 * Common DEC private modes used in terminal emulation.
 *
 * Toggled via CSI ? Pn h (DECSET) and CSI ? Pn l (DECRST).
 */
object DecPrivateMode {
    const val APPLICATION_CURSOR_KEYS: Int = 1
    const val DECCOLM: Int = 3
    const val REVERSE_VIDEO: Int = 5
    const val ORIGIN: Int = 6
    const val AUTO_WRAP: Int = 7
    const val CURSOR_VISIBLE: Int = 25
    const val APPLICATION_KEYPAD: Int = 66
    const val LEFT_RIGHT_MARGIN: Int = 69
    const val ALT_SCREEN: Int = 47
    const val ALT_SCREEN_BUFFER: Int = 1047
    const val ALT_SCREEN_SAVE_CURSOR: Int = 1049

    const val MOUSE_X10: Int = 9
    const val MOUSE_NORMAL: Int = 1000
    const val MOUSE_BUTTON_EVENT: Int = 1002
    const val MOUSE_ANY_EVENT: Int = 1003
    const val FOCUS_REPORTING: Int = 1004
    const val MOUSE_SGR: Int = 1006

    const val BRACKETED_PASTE: Int = 2004
}
