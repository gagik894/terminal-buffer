package com.gagik.integration

import com.gagik.core.api.TerminalBufferApi
import com.gagik.core.model.AttributeColor
import com.gagik.core.model.UnderlineStyle
import com.gagik.parser.spi.TerminalCommandSink
import com.gagik.terminal.protocol.AnsiMode
import com.gagik.terminal.protocol.DecPrivateMode
import com.gagik.terminal.protocol.MouseEncodingMode
import com.gagik.terminal.protocol.MouseTrackingMode

/**
 * Production bridge from parser semantic commands to the terminal core.
 *
 * The parser owns byte/protocol decoding. The core owns grid mutation, mode state,
 * cursor physics, and width policy. This adapter is the narrow place where ANSI/DEC
 * mode ids become concrete core API calls.
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

    private val windowTitleStack = ArrayDeque<String>()
    private val iconTitleStack = ArrayDeque<String>()

    private var foreground: AttributeColor = AttributeColor.DEFAULT
    private var background: AttributeColor = AttributeColor.DEFAULT
    private var underlineColor: AttributeColor = AttributeColor.DEFAULT
    private var bold: Boolean = false
    private var faint: Boolean = false
    private var italic: Boolean = false
    private var underlineStyle: UnderlineStyle = UnderlineStyle.NONE
    private var strikethrough: Boolean = false
    private var overline: Boolean = false
    private var blink: Boolean = false
    private var inverse: Boolean = false
    private var conceal: Boolean = false
    private var activeHyperlinkNumericId: Int = 0
    private var nextHyperlinkNumericId: Int = 1
    private val hyperlinkIds = HashMap<String, Int>()

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
        activeHyperlinkUri = null
        activeHyperlinkId = null
        activeHyperlinkNumericId = 0
        hyperlinkIds.clear()
        nextHyperlinkNumericId = 1
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

    override fun requestDeviceStatusReport(mode: Int, decPrivate: Boolean) {
        terminal.requestDeviceStatusReport(mode, decPrivate)
    }

    override fun requestDeviceAttributes(kind: Int, parameter: Int) {
        terminal.requestDeviceAttributes(kind, parameter)
    }

    override fun requestWindowReport(mode: Int) {
        terminal.requestWindowReport(mode)
    }

    override fun pushTitleStack(scope: Int) {
        when (scope) {
            0 -> {
                pushTitle(windowTitleStack, windowTitle)
                pushTitle(iconTitleStack, iconTitle)
            }
            1 -> pushTitle(iconTitleStack, iconTitle)
            2 -> pushTitle(windowTitleStack, windowTitle)
        }
    }

    override fun popTitleStack(scope: Int) {
        when (scope) {
            0 -> {
                popTitle(windowTitleStack)?.let { windowTitle = it }
                popTitle(iconTitleStack)?.let { iconTitle = it }
            }
            1 -> popTitle(iconTitleStack)?.let { iconTitle = it }
            2 -> popTitle(windowTitleStack)?.let { windowTitle = it }
        }
    }

    override fun resetAttributes() {
        resetPenMirror()
        terminal.resetPen()
    }

    private fun resetPenMirror() {
        foreground = AttributeColor.DEFAULT
        background = AttributeColor.DEFAULT
        underlineColor = AttributeColor.DEFAULT
        bold = false
        faint = false
        italic = false
        underlineStyle = UnderlineStyle.NONE
        strikethrough = false
        overline = false
        blink = false
        inverse = false
        conceal = false
    }

    override fun setBold(enabled: Boolean) {
        bold = enabled
        applyPen()
    }

    override fun setFaint(enabled: Boolean) {
        faint = enabled
        applyPen()
    }

    override fun setItalic(enabled: Boolean) {
        italic = enabled
        applyPen()
    }

    override fun setUnderlineStyle(style: Int) {
        underlineStyle = UnderlineStyle.fromSgrCode(style) ?: return
        applyPen()
    }

    override fun setBlink(enabled: Boolean) {
        blink = enabled
        applyPen()
    }

    override fun setInverse(enabled: Boolean) {
        inverse = enabled
        applyPen()
    }

    override fun setConceal(enabled: Boolean) {
        conceal = enabled
        applyPen()
    }

    override fun setStrikethrough(enabled: Boolean) {
        strikethrough = enabled
        applyPen()
    }

    override fun setOverline(enabled: Boolean) {
        overline = enabled
        applyPen()
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

    override fun setUnderlineColorDefault() {
        underlineColor = AttributeColor.DEFAULT
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

    override fun setUnderlineColorIndexed(index: Int) {
        if (index !in 0..255) return
        underlineColor = AttributeColor.indexed(index)
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

    override fun setUnderlineColorRgb(red: Int, green: Int, blue: Int) {
        underlineColor = AttributeColor.rgb(red, green, blue)
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
        activeHyperlinkNumericId = hyperlinkIdFor(uri, id)
        terminal.setHyperlinkId(activeHyperlinkNumericId)
    }

    override fun endHyperlink() {
        activeHyperlinkUri = null
        activeHyperlinkId = null
        activeHyperlinkNumericId = 0
        terminal.setHyperlinkId(0)
    }

    private fun setMouseTrackingMode(
        enabled: Boolean,
        mode: MouseTrackingMode,
    ) {
        terminal.setMouseTrackingMode(if (enabled) mode else MouseTrackingMode.OFF)
    }

    private fun pushTitle(stack: ArrayDeque<String>, title: String) {
        if (stack.size == MAX_TITLE_STACK_DEPTH) {
            stack.removeFirst()
        }
        stack.addLast(title)
    }

    private fun popTitle(stack: ArrayDeque<String>): String? {
        return if (stack.isEmpty()) null else stack.removeLast()
    }

    private fun hyperlinkIdFor(uri: String, id: String?): Int {
        val key = "${id ?: ""}\u0000$uri"
        return hyperlinkIds.getOrPut(key) { nextHyperlinkNumericId++ }
    }

    private fun applyPen() {
        terminal.setPenColors(
            foreground = foreground,
            background = background,
            underlineColor = underlineColor,
            bold = bold,
            faint = faint,
            italic = italic,
            underlineStyle = underlineStyle,
            strikethrough = strikethrough,
            overline = overline,
            blink = blink,
            inverse = inverse,
            conceal = conceal,
        )
    }

    private companion object {
        const val MAX_TITLE_STACK_DEPTH: Int = 16
    }
}
