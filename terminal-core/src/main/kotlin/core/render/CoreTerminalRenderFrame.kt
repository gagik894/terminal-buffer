package com.gagik.core.render

import com.gagik.core.state.TerminalState
import com.gagik.terminal.render.api.*

/**
 * Adapter from core state to the stable public render frame ABI.
 */
internal class CoreTerminalRenderFrame(
    private val state: TerminalState,
) : TerminalRenderFrame {
    private val attrTranslator = RenderAttrTranslator()
    private val clusterScratch = RenderClusterScratch()

    override val columns: Int
        get() = state.dimensions.width

    override val rows: Int
        get() = state.dimensions.height

    override val frameGeneration: Long
        get() = state.frameGeneration

    override val structureGeneration: Long
        get() = state.structureGeneration

    override val activeBuffer: TerminalRenderBufferKind
        get() = if (state.isAltScreenActive) {
            TerminalRenderBufferKind.ALTERNATE
        } else {
            TerminalRenderBufferKind.PRIMARY
        }

    override val cursor: TerminalRenderCursor
        get() = TerminalRenderCursor(
            column = state.cursor.col,
            row = state.cursor.row,
            visible = state.modes.isCursorVisible,
            blinking = state.modes.isCursorBlinking,
            shape = TerminalRenderCursorShape.BLOCK,
            generation = state.cursorGeneration,
        )

    override fun lineGeneration(row: Int): Long {
        checkRow(row)
        return visibleLineAt(row).renderGeneration
    }

    override fun lineWrapped(row: Int): Boolean {
        checkRow(row)
        return visibleLineAt(row).wrapped
    }

    override fun copyLine(
        row: Int,
        codeWords: IntArray,
        codeOffset: Int,
        attrWords: LongArray,
        attrOffset: Int,
        flags: IntArray,
        flagOffset: Int,
        extraAttrWords: LongArray?,
        extraAttrOffset: Int,
        hyperlinkIds: IntArray?,
        hyperlinkOffset: Int,
        clusterSink: TerminalRenderClusterSink?,
    ) {
        checkRow(row)
        checkCapacity(codeWords, codeOffset, columns, "codeWords")
        checkCapacity(attrWords, attrOffset, columns, "attrWords")
        checkCapacity(flags, flagOffset, columns, "flags")
        if (extraAttrWords != null) {
            checkCapacity(extraAttrWords, extraAttrOffset, columns, "extraAttrWords")
        }
        if (hyperlinkIds != null) {
            checkCapacity(hyperlinkIds, hyperlinkOffset, columns, "hyperlinkIds")
        }

        visibleLineAt(row).copyToRenderAbi(
            width = columns,
            codeWords = codeWords,
            codeOffset = codeOffset,
            attrWords = attrWords,
            attrOffset = attrOffset,
            flags = flags,
            flagOffset = flagOffset,
            extraAttrWords = extraAttrWords,
            extraAttrOffset = extraAttrOffset,
            hyperlinkIds = hyperlinkIds,
            hyperlinkOffset = hyperlinkOffset,
            clusterSink = clusterSink,
            attrTranslator = attrTranslator,
            clusterScratch = clusterScratch,
            reverseVideo = state.modes.isReverseVideo,
        )
    }

    private fun visibleLineAt(row: Int) =
        state.ring[state.resolveRingIndex(row)]

    private fun checkRow(row: Int) {
        require(row in 0 until rows) {
            "row out of bounds: $row, rows=$rows"
        }
    }

    private fun checkCapacity(array: IntArray, offset: Int, length: Int, name: String) {
        require(offset >= 0 && array.size - offset >= length) {
            "$name has insufficient capacity: size=${array.size}, offset=$offset, required=$length"
        }
    }

    private fun checkCapacity(array: LongArray, offset: Int, length: Int, name: String) {
        require(offset >= 0 && array.size - offset >= length) {
            "$name has insufficient capacity: size=${array.size}, offset=$offset, required=$length"
        }
    }
}
