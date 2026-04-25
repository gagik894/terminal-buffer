package com.gagik.parser.charset

import com.gagik.parser.runtime.ParserState

/**
 * Parser-owned character set translation.
 *
 * Scope:
 * - Owns G0..G3 designation interpretation for printable ingress.
 * - Applies GL mapping after UTF-8 decoding and before grapheme assembly.
 * - Does not mutate grid state.
 * - Does not participate in DECSC/DECRC core save/restore.
 *
 * Important limitation:
 * - This maps only GL printable codepoints 0x20..0x7e.
 * - Arbitrary Unicode codepoints are not charset-remapped.
 */
internal object CharsetMapper {
    @JvmStatic
    fun map(state: ParserState, codepoint: Int): Int {
        if (codepoint !in 0x20..0x7e) {
            return codepoint
        }

        val slot = activeGlSlot(state)
        val charset = state.charsets[slot]

        return when (charset) {
            ParserState.CHARSET_DEC_SPECIAL_GRAPHICS -> DecSpecialGraphics.map(codepoint)
            else -> codepoint
        }
    }

    @JvmStatic
    fun lockingShiftG0(state: ParserState) {
        state.glSlot = 0
        state.singleShiftSlot = -1
    }

    @JvmStatic
    fun lockingShiftG1(state: ParserState) {
        state.glSlot = 1
        state.singleShiftSlot = -1
    }

    @JvmStatic
    fun singleShiftG2(state: ParserState) {
        state.singleShiftSlot = 2
    }

    @JvmStatic
    fun singleShiftG3(state: ParserState) {
        state.singleShiftSlot = 3
    }

    @JvmStatic
    fun designate(state: ParserState, slot: Int, charset: Int) {
        require(slot in 0..3) { "charset slot out of range: $slot" }
        state.charsets[slot] = charset
    }

    @JvmStatic
    fun designateAscii(state: ParserState, slot: Int) {
        designate(state, slot, ParserState.CHARSET_ASCII)
    }

    @JvmStatic
    fun designateDecSpecialGraphics(state: ParserState, slot: Int) {
        designate(state, slot, ParserState.CHARSET_DEC_SPECIAL_GRAPHICS)
    }

    private fun activeGlSlot(state: ParserState): Int {
        val single = state.singleShiftSlot
        if (single >= 0) {
            state.singleShiftSlot = -1
            return single
        }
        return state.glSlot
    }
}
