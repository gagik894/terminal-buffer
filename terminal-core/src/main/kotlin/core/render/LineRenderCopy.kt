package com.gagik.core.render

import com.gagik.core.codec.AttributeCodec
import com.gagik.core.model.Line
import com.gagik.core.model.TerminalConstants
import com.gagik.terminal.render.api.TerminalRenderCellFlags
import com.gagik.terminal.render.api.TerminalRenderClusterSink

internal fun Line.copyToRenderAbi(
    width: Int,
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
    attrTranslator: RenderAttrTranslator,
    clusterScratch: RenderClusterScratch,
    reverseVideo: Boolean,
) {
    var col = 0
    while (col < width) {
        val raw = rawCodepoint(col)
        val primaryAttr = getPackedAttr(col)
        val extendedAttr = getPackedExtendedAttr(col)

        codeWords[codeOffset + col] = 0
        attrWords[attrOffset + col] = attrTranslator.toRenderAttrWord(
            primaryAttr = primaryAttr,
            extendedAttr = extendedAttr,
            reverseVideo = reverseVideo,
        )
        flags[flagOffset + col] = cellFlags(col, raw)

        if (extraAttrWords != null) {
            extraAttrWords[extraAttrOffset + col] = attrTranslator.toRenderExtraAttrWord(extendedAttr)
        }
        if (hyperlinkIds != null) {
            hyperlinkIds[hyperlinkOffset + col] = AttributeCodec.hyperlinkId(extendedAttr)
        }

        when {
            raw == TerminalConstants.EMPTY ||
                raw == TerminalConstants.WIDE_CHAR_SPACER -> {
                codeWords[codeOffset + col] = 0
            }
            raw <= TerminalConstants.CLUSTER_HANDLE_MAX -> {
                if (clusterSink != null) {
                    clusterSink.onCluster(col, clusterScratch.clusterText(this, col))
                }
            }
            else -> {
                codeWords[codeOffset + col] = raw
            }
        }
        col++
    }
}

private fun Line.cellFlags(col: Int, raw: Int): Int = when {
    raw == TerminalConstants.EMPTY -> TerminalRenderCellFlags.EMPTY
    raw == TerminalConstants.WIDE_CHAR_SPACER -> TerminalRenderCellFlags.WIDE_TRAILING
    raw <= TerminalConstants.CLUSTER_HANDLE_MAX -> {
        var flags = TerminalRenderCellFlags.CLUSTER
        if (isWideLeading(col)) {
            flags = flags or TerminalRenderCellFlags.WIDE_LEADING
        }
        flags
    }
    else -> {
        var flags = TerminalRenderCellFlags.CODEPOINT
        if (isWideLeading(col)) {
            flags = flags or TerminalRenderCellFlags.WIDE_LEADING
        }
        flags
    }
}

private fun Line.isWideLeading(col: Int): Boolean =
    col + 1 < width && rawCodepoint(col + 1) == TerminalConstants.WIDE_CHAR_SPACER

internal class RenderClusterScratch {
    private var codepoints = IntArray(16)

    fun clusterText(line: Line, col: Int): String {
        val raw = line.rawCodepoint(col)
        val length = line.store.length(raw)
        if (codepoints.size < length) {
            codepoints = IntArray(length)
        }
        line.readCluster(col, codepoints)
        return String(codepoints, 0, length)
    }
}
