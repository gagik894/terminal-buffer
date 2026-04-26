package com.gagik.parser.unicode

import com.gagik.parser.runtime.ParserState

internal object GraphemeSegmenter {
    @JvmStatic
    fun continuesCurrentCluster(
        state: ParserState,
        codepoint: Int,
    ): Boolean {
        val currentClass = UnicodeClass.graphemeBreakClass(codepoint)
        val previousClass = state.prevGraphemeBreakClass

        return currentClass == UnicodeClass.GRAPHEME_EXTEND ||
                currentClass == UnicodeClass.GRAPHEME_ZWJ ||
                currentClass == UnicodeClass.GRAPHEME_SPACING_MARK ||
                isHangulContinuation(previousClass, currentClass) ||
                isRegionalIndicatorContinuation(state, currentClass) ||
                isExtendedPictographicZwjContinuation(state, codepoint)
    }

    @JvmStatic
    fun updateContext(
        state: ParserState,
        codepoint: Int,
    ) {
        val graphemeClass = UnicodeClass.graphemeBreakClass(codepoint)

        if (graphemeClass == UnicodeClass.GRAPHEME_ZWJ) {
            state.prevWasZwj = true
        } else {
            state.prevWasZwj = false
            state.zwjBeforeExtendedPictographic = false
        }

        if (graphemeClass == UnicodeClass.GRAPHEME_REGIONAL_INDICATOR) {
            state.regionalIndicatorParity = state.regionalIndicatorParity xor 1
        } else {
            state.regionalIndicatorParity = 0
        }

        if (graphemeClass == UnicodeClass.GRAPHEME_ZWJ) {
            state.zwjBeforeExtendedPictographic = state.lastNonExtendWasExtendedPictographic
        } else if (graphemeClass != UnicodeClass.GRAPHEME_EXTEND) {
            state.lastNonExtendWasExtendedPictographic = UnicodeClass.isExtendedPictographic(codepoint)
        }

        state.prevGraphemeBreakClass = graphemeClass
    }

    private fun isHangulContinuation(
        previousClass: Int,
        currentClass: Int,
    ): Boolean {
        return when (previousClass) {
            UnicodeClass.GRAPHEME_L -> currentClass == UnicodeClass.GRAPHEME_L ||
                    currentClass == UnicodeClass.GRAPHEME_V ||
                    currentClass == UnicodeClass.GRAPHEME_LV ||
                    currentClass == UnicodeClass.GRAPHEME_LVT

            UnicodeClass.GRAPHEME_LV,
            UnicodeClass.GRAPHEME_V -> currentClass == UnicodeClass.GRAPHEME_V ||
                    currentClass == UnicodeClass.GRAPHEME_T

            UnicodeClass.GRAPHEME_LVT,
            UnicodeClass.GRAPHEME_T -> currentClass == UnicodeClass.GRAPHEME_T

            else -> false
        }
    }

    private fun isRegionalIndicatorContinuation(
        state: ParserState,
        currentClass: Int,
    ): Boolean {
        return currentClass == UnicodeClass.GRAPHEME_REGIONAL_INDICATOR &&
                state.regionalIndicatorParity == 1
    }

    private fun isExtendedPictographicZwjContinuation(
        state: ParserState,
        codepoint: Int,
    ): Boolean {
        return state.prevWasZwj &&
                state.zwjBeforeExtendedPictographic &&
                UnicodeClass.isExtendedPictographic(codepoint)
    }
}
