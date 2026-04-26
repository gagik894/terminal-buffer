package com.gagik.parser.ansi.sgr

import com.gagik.parser.runtime.ParserState
import com.gagik.parser.spi.TerminalCommandSink

internal object SgrDispatcher {
    fun dispatch(
        sink: TerminalCommandSink,
        state: ParserState,
    ) {
        if (state.paramCount == 0) {
            sink.resetAttributes()
            return
        }

        var i = 0
        while (i < state.paramCount) {
            val param = state.params[i]

            i = when {
                param < 0 -> {
                    sink.resetAttributes()
                    i + 1
                }

                param == 0 -> {
                    sink.resetAttributes()
                    i + 1
                }

                param == 1 -> {
                    sink.setBold(true)
                    i + 1
                }

                param == 2 -> {
                    sink.setFaint(true)
                    i + 1
                }

                param == 3 -> {
                    sink.setItalic(true)
                    i + 1
                }

                param == 4 -> {
                    sink.setUnderlineStyle(SgrUnderlineStyle.SINGLE)
                    i + 1
                }

                param == 5 || param == 6 -> {
                    sink.setBlink(true)
                    i + 1
                }

                param == 7 -> {
                    sink.setInverse(true)
                    i + 1
                }

                param == 8 -> {
                    sink.setConceal(true)
                    i + 1
                }

                param == 9 -> {
                    sink.setStrikethrough(true)
                    i + 1
                }

                param == 21 -> {
                    sink.setUnderlineStyle(SgrUnderlineStyle.DOUBLE)
                    i + 1
                }

                param == 22 -> {
                    sink.setBold(false)
                    sink.setFaint(false)
                    i + 1
                }

                param == 23 -> {
                    sink.setItalic(false)
                    i + 1
                }

                param == 24 -> {
                    sink.setUnderlineStyle(SgrUnderlineStyle.NONE)
                    i + 1
                }

                param == 25 -> {
                    sink.setBlink(false)
                    i + 1
                }

                param == 27 -> {
                    sink.setInverse(false)
                    i + 1
                }

                param == 28 -> {
                    sink.setConceal(false)
                    i + 1
                }

                param == 29 -> {
                    sink.setStrikethrough(false)
                    i + 1
                }

                param in 30..37 -> {
                    sink.setForegroundIndexed(param - 30)
                    i + 1
                }

                param == 38 -> dispatchExtendedColor(
                    sink = sink,
                    state = state,
                    startIndex = i,
                    foreground = true,
                )

                param == 39 -> {
                    sink.setForegroundDefault()
                    i + 1
                }

                param in 40..47 -> {
                    sink.setBackgroundIndexed(param - 40)
                    i + 1
                }

                param == 48 -> dispatchExtendedColor(
                    sink = sink,
                    state = state,
                    startIndex = i,
                    foreground = false,
                )

                param == 49 -> {
                    sink.setBackgroundDefault()
                    i + 1
                }

                param in 90..97 -> {
                    sink.setForegroundIndexed(8 + (param - 90))
                    i + 1
                }

                param in 100..107 -> {
                    sink.setBackgroundIndexed(8 + (param - 100))
                    i + 1
                }

                else -> i + 1
            }
        }
    }

    private fun dispatchExtendedColor(
        sink: TerminalCommandSink,
        state: ParserState,
        startIndex: Int,
        foreground: Boolean,
    ): Int {
        val modeIndex = startIndex + 1
        val mode = paramOrMissing(state, modeIndex)
        if (mode < 0) {
            return startIndex + 1
        }

        return when (mode) {
            5 -> dispatchIndexedColor(
                sink = sink,
                state = state,
                startIndex = startIndex,
                foreground = foreground,
            )

            2 -> dispatchRgbColor(
                sink = sink,
                state = state,
                startIndex = startIndex,
                foreground = foreground,
            )

            else -> startIndex + 2
        }
    }

    private fun dispatchIndexedColor(
        sink: TerminalCommandSink,
        state: ParserState,
        startIndex: Int,
        foreground: Boolean,
    ): Int {
        val colorIndex = startIndex + 2
        val color = paramOrMissing(state, colorIndex)
        if (color !in 0..255) {
            return colorIndex + 1
        }

        if (foreground) {
            sink.setForegroundIndexed(color)
        } else {
            sink.setBackgroundIndexed(color)
        }

        return colorIndex + 1
    }

    private fun dispatchRgbColor(
        sink: TerminalCommandSink,
        state: ParserState,
        startIndex: Int,
        foreground: Boolean,
    ): Int {
        var redIndex = startIndex + 2

        if (
            redIndex < state.paramCount &&
            isColonOpened(state, redIndex) &&
            state.params[redIndex] < 0
        ) {
            redIndex++
        }

        val red = paramOrMissing(state, redIndex)
        val green = paramOrMissing(state, redIndex + 1)
        val blue = paramOrMissing(state, redIndex + 2)

        if (red !in 0..255 || green !in 0..255 || blue !in 0..255) {
            return redIndex + 3
        }

        if (foreground) {
            sink.setForegroundRgb(red, green, blue)
        } else {
            sink.setBackgroundRgb(red, green, blue)
        }

        return redIndex + 3
    }

    private fun paramOrMissing(state: ParserState, index: Int): Int {
        return if (index in 0 until state.paramCount) {
            state.params[index]
        } else {
            -1
        }
    }

    private fun isColonOpened(state: ParserState, index: Int): Boolean {
        return index in 0..31 && ((state.subParameterMask ushr index) and 1) != 0
    }
}
