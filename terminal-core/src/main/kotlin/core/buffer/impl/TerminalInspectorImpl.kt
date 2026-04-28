package com.gagik.core.buffer.impl

import com.gagik.core.api.TerminalInspector
import com.gagik.core.codec.AttributeCodec
import com.gagik.core.model.Attributes
import com.gagik.core.model.Line
import com.gagik.core.state.TerminalState

internal class TerminalInspectorImpl(
	private val state: TerminalState
) : TerminalInspector {

	override fun getAttrAt(col: Int, row: Int): Attributes? {
		if (!state.dimensions.isValidCol(col) || !state.dimensions.isValidRow(row)) return null
		val line = visibleLine(row) ?: return null
		val rawAttr = if (line.width == 0) state.pen.currentAttr else line.getPackedAttr(col)
		val rawExtendedAttr = if (line.width == 0) {
			state.pen.currentExtendedAttr
		} else {
			line.getPackedExtendedAttr(col)
		}
		return AttributeCodec.unpack(rawAttr, rawExtendedAttr)
	}

	override fun getLineAsString(row: Int): String {
		return visibleLine(row)?.toTextTrimmed() ?: ""
	}

	override fun getScreenAsString(): String = buildString {
		for (row in 0 until state.dimensions.height) {
			if (row > 0) append('\n')
			append(getLineAsString(row))
		}
	}

	override fun getAllAsString(): String {
		val sb = StringBuilder()
		for (row in 0 until state.ring.size) {
			if (row > 0) sb.append('\n')
			sb.append(state.ring[row].toTextTrimmed())
		}
		return sb.toString()
	}

	private fun visibleLine(row: Int): Line? {
		if (!state.dimensions.isValidRow(row)) return null
		return state.ring[state.resolveRingIndex(row)]
	}
}
