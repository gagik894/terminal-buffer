package com.gagik.parser.fixture

internal object ParserEvents {
    fun writeCodepoint(codepoint: Int): String = "writeCodepoint:$codepoint"

    fun writeCluster(charWidth: Int, vararg codepoints: Int): String {
        return "writeCluster:${codepoints.size}:$charWidth:${codepoints.joinToString(":")}"
    }
}
