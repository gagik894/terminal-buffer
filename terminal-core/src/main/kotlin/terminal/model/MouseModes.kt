package com.gagik.terminal.model

/** Mouse tracking selection toggled by DECSET private modes. */
enum class MouseTrackingMode {
    OFF,
    X10,
    NORMAL,
    BUTTON_EVENT,
    ANY_EVENT
}

/** Mouse report encoding selected by xterm private modes. */
enum class MouseEncodingMode {
    DEFAULT,
    UTF8,
    SGR,
    URXVT
}
