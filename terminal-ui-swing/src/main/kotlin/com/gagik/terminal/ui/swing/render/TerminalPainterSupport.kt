package com.gagik.terminal.ui.swing.render

import com.gagik.terminal.render.api.TerminalRenderAttrs
import com.gagik.terminal.render.api.TerminalRenderCellFlags
import java.awt.Font

internal fun terminalFontStyle(attr: Long): Int {
    var style = Font.PLAIN
    if (TerminalRenderAttrs.isBold(attr)) style = style or Font.BOLD
    if (TerminalRenderAttrs.isItalic(attr)) style = style or Font.ITALIC
    return style
}

internal fun hasDrawableText(flags: Int): Boolean {
    return flags and TerminalRenderCellFlags.CODEPOINT != 0 ||
        flags and TerminalRenderCellFlags.CLUSTER != 0
}

internal fun isFastAsciiCell(flags: Int, codeWord: Int): Boolean {
    return flags == TerminalRenderCellFlags.CODEPOINT && codeWord in 0x20..0x7e
}

internal fun cellSpan(flags: Int): Int {
    return if (flags and TerminalRenderCellFlags.WIDE_LEADING != 0) 2 else 1
}

internal fun visualCellRangeStart(flags: Int, column: Int): Int {
    return if (flags and TerminalRenderCellFlags.WIDE_TRAILING != 0) maxOf(0, column - 1) else column
}

internal fun visualCellRangeSpan(flags: Int, column: Int, columns: Int): Int {
    val start = visualCellRangeStart(flags, column)
    val span = when {
        flags and TerminalRenderCellFlags.WIDE_LEADING != 0 -> 2
        flags and TerminalRenderCellFlags.WIDE_TRAILING != 0 && column > 0 -> 2
        else -> 1
    }
    return minOf(span, columns - start)
}
