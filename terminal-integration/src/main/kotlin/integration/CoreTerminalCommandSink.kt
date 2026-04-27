package com.gagik.integration

import com.gagik.core.api.TerminalBufferApi
import com.gagik.core.model.AttributeColor
import com.gagik.core.model.MouseEncodingMode
import com.gagik.core.model.MouseTrackingMode
import com.gagik.parser.ansi.mode.AnsiMode
import com.gagik.parser.ansi.mode.DecPrivateMode
import com.gagik.parser.spi.TerminalCommandSink

/**
 * Production bridge from parser semantic commands to the terminal core.
 *
 * The parser owns byte/protocol decoding. The core owns grid mutation, mode state,
 * cursor physics, and width policy. This adapter is the narrow place where ANSI/DEC
 * mode ids become concrete core API calls.
 *
 * TODO(core-gap): Core pen attributes do not yet model faint, blink, conceal, or
 * strikethrough. These SGR attributes are intentionally not faked here.
 */
class CoreTerminalCommandSink(
    private val terminal: TerminalBufferApi,
) : TerminalCommandSink {
    var windowTitle: String = ""
        private set
    var iconTitle: String = ""
        private set
    var activeHyperlinkUri: String? = null
        private set
    var activeHyperlinkId: String? = null
        private set

    private var foreground: AttributeColor = AttributeColor.DEFAULT
    private var background: AttributeColor = AttributeColor.DEFAULT
    private var bold: Boolean = false
    private var italic: Boolean = false
    private var underline: Boolean = false
    private var inverse: Boolean = false

    override fun writeCodepoint(codepoint: Int) {
        terminal.writeCodepoint(codepoint)
    }

    override fun writeCluster(codepoints: IntArray, length: Int) {
        terminal.writeCluster(codepoints, length)
    }

    override fun bell() {
        // TODO(core-gap): Add a core/UI bell hook. Do not fake this by mutating grid state.
    }

    override fun backspace() {
        terminal.cursorLeft()
    }

    override fun tab() {
        terminal.horizontalTab()
    }

    override fun lineFeed() {
        terminal.newLine()
        if (terminal.getModeSnapshot().isNewLineMode) {
            terminal.carriageReturn()
        }
    }

    override fun carriageReturn() {
        terminal.carriageReturn()
    }

    override fun reverseIndex() {
        terminal.reverseLineFeed()
    }

    override fun nextLine() {
        terminal.newLine()
        terminal.carriageReturn()
    }

    override fun softReset() {
        // TODO(core-gap): Add a DECSTR soft-reset API to core. Full RIS reset exists, but DECSTR
        // is a softer reset and should not be mapped blindly to TerminalBufferApi.reset().
        resetAttributes()
    }

    override fun resetTerminal() {
        terminal.reset()
        resetPenMirror()
    }

    override fun saveCursor() {
        terminal.saveCursor()
    }

    override fun restoreCursor() {
        terminal.restoreCursor()
    }

    override fun cursorUp(n: Int) {
        terminal.cursorUp(n)
    }

    override fun cursorDown(n: Int) {
        terminal.cursorDown(n)
    }

    override fun cursorForward(n: Int) {
        terminal.cursorRight(n)
    }

    override fun cursorBackward(n: Int) {
        terminal.cursorLeft(n)
    }

    override fun cursorNextLine(n: Int) {
        terminal.cursorDown(n)
        terminal.carriageReturn()
    }

    override fun cursorPreviousLine(n: Int) {
        terminal.cursorUp(n)
        terminal.carriageReturn()
    }

    override fun cursorForwardTabs(n: Int) {
        terminal.cursorForwardTab(n)
    }

    override fun cursorBackwardTabs(n: Int) {
        terminal.cursorBackwardTab(n)
    }

    override fun setCursorColumn(col: Int) {
        terminal.positionCursor(col = col, row = terminal.cursorRow)
    }

    override fun setCursorRow(row: Int) {
        terminal.positionCursor(col = terminal.cursorCol, row = row)
    }

    override fun setCursorAbsolute(row: Int, col: Int) {
        terminal.positionCursor(col = col, row = row)
    }

    override fun setScrollRegion(top: Int, bottom: Int) {
        // Parser SPI passes zero-based inclusive margins; core TerminalWriter keeps DECSTBM's
        // one-based inclusive API. This conversion is intentional.
        terminal.setScrollRegion(
            top = top + 1,
            bottom = if (bottom < 0) terminal.height else bottom + 1,
        )
    }

    override fun setLeftRightMargins(left: Int, right: Int) {
        // Parser SPI passes zero-based inclusive margins; core TerminalWriter keeps DECSLRM's
        // one-based inclusive API. This conversion is intentional.
        terminal.setLeftRightMargins(
            left = left + 1,
            right = if (right < 0) terminal.width else right + 1,
        )
    }

    override fun eraseInDisplay(mode: Int, selective: Boolean) {
        when {
            selective && mode == 0 -> terminal.selectiveEraseScreenToEnd()
            selective && mode == 1 -> terminal.selectiveEraseScreenToCursor()
            selective && mode == 2 -> terminal.selectiveEraseEntireScreen()
            !selective && mode == 0 -> terminal.eraseScreenToEnd()
            !selective && mode == 1 -> terminal.eraseScreenToCursor()
            !selective && mode == 2 -> terminal.eraseEntireScreen()
            !selective && mode == 3 -> terminal.eraseScreenAndHistory()
        }
    }

    override fun eraseInLine(mode: Int, selective: Boolean) {
        when {
            selective && mode == 0 -> terminal.selectiveEraseLineToEnd()
            selective && mode == 1 -> terminal.selectiveEraseLineToCursor()
            selective && mode == 2 -> terminal.selectiveEraseCurrentLine()
            !selective && mode == 0 -> terminal.eraseLineToEnd()
            !selective && mode == 1 -> terminal.eraseLineToCursor()
            !selective && mode == 2 -> terminal.eraseCurrentLine()
        }
    }

    override fun insertLines(n: Int) {
        terminal.insertLines(n)
    }

    override fun deleteLines(n: Int) {
        terminal.deleteLines(n)
    }

    override fun insertCharacters(n: Int) {
        terminal.insertBlankCharacters(n)
    }

    override fun deleteCharacters(n: Int) {
        terminal.deleteCharacters(n)
    }

    override fun eraseCharacters(n: Int) {
        terminal.eraseCharacters(n)
    }

    override fun scrollUp(n: Int) {
        repeat(n.coerceAtLeast(0)) {
            terminal.scrollUp()
        }
    }

    override fun scrollDown(n: Int) {
        repeat(n.coerceAtLeast(0)) {
            terminal.scrollDown()
        }
    }

    override fun setTabStop() {
        terminal.setTabStop()
    }

    override fun clearTabStop() {
        terminal.clearTabStop()
    }

    override fun clearAllTabStops() {
        terminal.clearAllTabStops()
    }

    override fun setAnsiMode(mode: Int, enable: Boolean) {
        when (mode) {
            AnsiMode.INSERT -> terminal.setInsertMode(enable)
            AnsiMode.NEW_LINE -> terminal.setNewLineMode(enable)
        }
    }

    override fun setDecMode(mode: Int, enable: Boolean) {
        when (mode) {
            DecPrivateMode.APPLICATION_CURSOR_KEYS -> terminal.setApplicationCursorKeys(enable)
            DecPrivateMode.DECCOLM -> terminal.executeDeccolm(if (enable) 132 else 80)
            DecPrivateMode.REVERSE_VIDEO -> terminal.setReverseVideo(enable)
            DecPrivateMode.ORIGIN -> terminal.setOriginMode(enable)
            DecPrivateMode.AUTO_WRAP -> terminal.setAutoWrap(enable)
            DecPrivateMode.CURSOR_VISIBLE -> terminal.setCursorVisible(enable)
            DecPrivateMode.APPLICATION_KEYPAD -> terminal.setApplicationKeypad(enable)
            DecPrivateMode.LEFT_RIGHT_MARGIN -> terminal.setLeftRightMarginMode(enable)
            DecPrivateMode.MOUSE_X10 -> setMouseTrackingMode(enable, MouseTrackingMode.X10)
            DecPrivateMode.MOUSE_NORMAL -> setMouseTrackingMode(enable, MouseTrackingMode.NORMAL)
            DecPrivateMode.MOUSE_BUTTON_EVENT -> setMouseTrackingMode(enable, MouseTrackingMode.BUTTON_EVENT)
            DecPrivateMode.MOUSE_ANY_EVENT -> setMouseTrackingMode(enable, MouseTrackingMode.ANY_EVENT)
            DecPrivateMode.FOCUS_REPORTING -> terminal.setFocusReportingEnabled(enable)
            DecPrivateMode.MOUSE_SGR -> terminal.setMouseEncodingMode(
                if (enable) MouseEncodingMode.SGR else MouseEncodingMode.DEFAULT
            )
            DecPrivateMode.ALT_SCREEN,
            DecPrivateMode.ALT_SCREEN_BUFFER -> {
                // TODO(core-gap): Core exposes only 1049-style alt-buffer save/restore semantics.
                // Do not fake DECSET 47/1047 by mapping them to 1049.
            }
            DecPrivateMode.ALT_SCREEN_SAVE_CURSOR -> {
                if (enable) terminal.enterAltBuffer() else terminal.exitAltBuffer()
            }
            DecPrivateMode.BRACKETED_PASTE -> terminal.setBracketedPasteEnabled(enable)
        }
    }

    override fun resetAttributes() {
        resetPenMirror()
        terminal.resetPen()
    }

    private fun resetPenMirror() {
        foreground = AttributeColor.DEFAULT
        background = AttributeColor.DEFAULT
        bold = false
        italic = false
        underline = false
        inverse = false
    }

    override fun setBold(enabled: Boolean) {
        bold = enabled
        applyPen()
    }

    override fun setFaint(enabled: Boolean) {
        // TODO(core-gap): Add faint/dim intensity to core Attributes/Pen before wiring SGR 2/22.
    }

    override fun setItalic(enabled: Boolean) {
        italic = enabled
        applyPen()
    }

    override fun setUnderlineStyle(style: Int) {
        underline = style != 0
        applyPen()
    }

    override fun setBlink(enabled: Boolean) {
        // TODO(core-gap): Add blink presentation state to core Attributes/Pen before wiring SGR 5/6/25.
    }

    override fun setInverse(enabled: Boolean) {
        inverse = enabled
        applyPen()
    }

    override fun setConceal(enabled: Boolean) {
        // TODO(core-gap): Add conceal/hidden cell attribute before wiring SGR 8/28.
    }

    override fun setStrikethrough(enabled: Boolean) {
        // TODO(core-gap): Add strikethrough cell attribute before wiring SGR 9/29.
    }

    override fun setSelectiveEraseProtection(enabled: Boolean) {
        terminal.setSelectiveEraseProtection(enabled)
    }

    override fun setForegroundDefault() {
        foreground = AttributeColor.DEFAULT
        applyPen()
    }

    override fun setBackgroundDefault() {
        background = AttributeColor.DEFAULT
        applyPen()
    }

    override fun setForegroundIndexed(index: Int) {
        if (index !in 0..255) return
        foreground = AttributeColor.indexed(index)
        applyPen()
    }

    override fun setBackgroundIndexed(index: Int) {
        if (index !in 0..255) return
        background = AttributeColor.indexed(index)
        applyPen()
    }

    override fun setForegroundRgb(red: Int, green: Int, blue: Int) {
        foreground = AttributeColor.rgb(red, green, blue)
        applyPen()
    }

    override fun setBackgroundRgb(red: Int, green: Int, blue: Int) {
        background = AttributeColor.rgb(red, green, blue)
        applyPen()
    }

    override fun setWindowTitle(title: String) {
        windowTitle = title
    }

    override fun setIconTitle(title: String) {
        iconTitle = title
    }

    override fun setIconAndWindowTitle(title: String) {
        iconTitle = title
        windowTitle = title
    }

    override fun startHyperlink(uri: String, id: String?) {
        activeHyperlinkUri = uri
        activeHyperlinkId = id
    }

    override fun endHyperlink() {
        activeHyperlinkUri = null
        activeHyperlinkId = null
    }

    private fun setMouseTrackingMode(
        enabled: Boolean,
        mode: MouseTrackingMode,
    ) {
        terminal.setMouseTrackingMode(if (enabled) mode else MouseTrackingMode.OFF)
    }

    private fun applyPen() {
        terminal.setPenColors(
            foreground = foreground,
            background = background,
            bold = bold,
            italic = italic,
            underline = underline,
            inverse = inverse,
        )
    }
}
