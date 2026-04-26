package com.gagik.parser.api

import com.gagik.parser.impl.TerminalParser
import com.gagik.parser.spi.TerminalCommandSink

/**
 * Factory for terminal output parsers.
 */
internal object TerminalParsers {
    @JvmStatic
    fun create(sink: TerminalCommandSink): TerminalOutputParser {
        return TerminalParser(sink)
    }
}
