package com.gagik.terminal.input.impl

import com.gagik.core.api.TerminalInputState
import com.gagik.terminal.input.event.TerminalKey
import com.gagik.terminal.input.event.TerminalKeyEvent
import com.gagik.terminal.input.event.TerminalModifiers
import com.gagik.terminal.protocol.ControlCode
import com.gagik.terminal.protocol.host.TerminalHostOutput

internal class KeyboardEncoder(
    private val output: TerminalHostOutput,
    private val scratch: InputScratchBuffer,
) {
    fun encode(event: TerminalKeyEvent, modeBits: Long) {
        val key = event.key
        if (key != null) {
            encodeSpecialKey(
                key = key,
                modifiers = event.modifiers,
                modeBits = modeBits,
            )
        } else {
            encodeCodepoint(
                codepoint = event.codepoint,
                modifiers = event.modifiers,
            )
        }
    }

    private fun encodeCodepoint(codepoint: Int, modifiers: Int) {
        val ctrlCode = if (TerminalModifiers.hasCtrl(modifiers)) {
            controlCodeFor(codepoint)
        } else {
            -1
        }

        if (TerminalModifiers.hasAlt(modifiers)) {
            output.writeByte(ControlCode.ESC)
        }

        if (ctrlCode >= 0) {
            output.writeByte(ctrlCode)
            return
        }

        writeUtf8Codepoint(codepoint)
    }

    private fun encodeSpecialKey(
        key: TerminalKey,
        modifiers: Int,
        modeBits: Long,
    ) {
        when (key) {
            TerminalKey.ENTER -> encodeEnter(modeBits)
            TerminalKey.NUMPAD_ENTER -> encodeKeypad(key, modeBits)

            TerminalKey.TAB -> encodeTab(modifiers)

            TerminalKey.BACKSPACE -> output.writeByte(ControlCode.DEL)

            TerminalKey.ESCAPE -> output.writeByte(ControlCode.ESC)

            TerminalKey.UP -> encodeArrow(
                modifiers = modifiers,
                normalFinal = 'A'.code,
                applicationFinal = 'A'.code,
                modeBits = modeBits,
            )

            TerminalKey.DOWN -> encodeArrow(
                modifiers = modifiers,
                normalFinal = 'B'.code,
                applicationFinal = 'B'.code,
                modeBits = modeBits,
            )

            TerminalKey.RIGHT -> encodeArrow(
                modifiers = modifiers,
                normalFinal = 'C'.code,
                applicationFinal = 'C'.code,
                modeBits = modeBits,
            )

            TerminalKey.LEFT -> encodeArrow(
                modifiers = modifiers,
                normalFinal = 'D'.code,
                applicationFinal = 'D'.code,
                modeBits = modeBits,
            )

            TerminalKey.HOME -> encodeHomeEnd(
                modifiers = modifiers,
                normalFinal = 'H'.code,
                applicationFinal = 'H'.code,
                modeBits = modeBits,
            )

            TerminalKey.END -> encodeHomeEnd(
                modifiers = modifiers,
                normalFinal = 'F'.code,
                applicationFinal = 'F'.code,
                modeBits = modeBits,
            )

            TerminalKey.INSERT -> encodeTildeKey(2, modifiers)
            TerminalKey.DELETE -> encodeTildeKey(3, modifiers)
            TerminalKey.PAGE_UP -> encodeTildeKey(5, modifiers)
            TerminalKey.PAGE_DOWN -> encodeTildeKey(6, modifiers)

            TerminalKey.F1 -> encodeFunctionSs3OrModified('P'.code, modifiers)
            TerminalKey.F2 -> encodeFunctionSs3OrModified('Q'.code, modifiers)
            TerminalKey.F3 -> encodeFunctionSs3OrModified('R'.code, modifiers)
            TerminalKey.F4 -> encodeFunctionSs3OrModified('S'.code, modifiers)

            TerminalKey.F5 -> encodeTildeKey(15, modifiers)
            TerminalKey.F6 -> encodeTildeKey(17, modifiers)
            TerminalKey.F7 -> encodeTildeKey(18, modifiers)
            TerminalKey.F8 -> encodeTildeKey(19, modifiers)
            TerminalKey.F9 -> encodeTildeKey(20, modifiers)
            TerminalKey.F10 -> encodeTildeKey(21, modifiers)
            TerminalKey.F11 -> encodeTildeKey(23, modifiers)
            TerminalKey.F12 -> encodeTildeKey(24, modifiers)

            TerminalKey.NUMPAD_DIVIDE,
            TerminalKey.NUMPAD_MULTIPLY,
            TerminalKey.NUMPAD_SUBTRACT,
            TerminalKey.NUMPAD_ADD,
            TerminalKey.NUMPAD_DECIMAL,
            TerminalKey.NUMPAD_0,
            TerminalKey.NUMPAD_1,
            TerminalKey.NUMPAD_2,
            TerminalKey.NUMPAD_3,
            TerminalKey.NUMPAD_4,
            TerminalKey.NUMPAD_5,
            TerminalKey.NUMPAD_6,
            TerminalKey.NUMPAD_7,
            TerminalKey.NUMPAD_8,
            TerminalKey.NUMPAD_9 -> encodeKeypad(key, modeBits)
        }
    }

    private fun encodeEnter(modeBits: Long) {
        if (TerminalInputState.isNewLineMode(modeBits)) {
            output.writeByte(ControlCode.CR)
            output.writeByte(ControlCode.LF)
        } else {
            output.writeByte(ControlCode.CR)
        }
    }

    private fun encodeTab(modifiers: Int) {
        if (modifiers == TerminalModifiers.NONE) {
            output.writeByte(ControlCode.HT)
            return
        }

        if (modifiers == TerminalModifiers.SHIFT) {
            writeStatic(TerminalSequences.BACK_TAB)
            return
        }

        encodeCsiModifierFinal(
            prefixNumber = 1,
            modifiers = modifiers,
            finalByte = 'Z'.code,
        )
    }

    private fun encodeArrow(
        modifiers: Int,
        normalFinal: Int,
        applicationFinal: Int,
        modeBits: Long,
    ) {
        if (modifiers != TerminalModifiers.NONE) {
            encodeCsiModifierFinal(
                prefixNumber = 1,
                modifiers = modifiers,
                finalByte = normalFinal,
            )
            return
        }

        if (TerminalInputState.isApplicationCursorKeys(modeBits)) {
            writeSs3(applicationFinal)
        } else {
            writeCsiFinal(normalFinal)
        }
    }

    private fun encodeHomeEnd(
        modifiers: Int,
        normalFinal: Int,
        applicationFinal: Int,
        modeBits: Long,
    ) {
        if (modifiers != TerminalModifiers.NONE) {
            encodeCsiModifierFinal(
                prefixNumber = 1,
                modifiers = modifiers,
                finalByte = normalFinal,
            )
            return
        }

        if (TerminalInputState.isApplicationCursorKeys(modeBits)) {
            writeSs3(applicationFinal)
        } else {
            writeCsiFinal(normalFinal)
        }
    }

    private fun encodeFunctionSs3OrModified(
        unmodifiedFinal: Int,
        modifiers: Int,
    ) {
        if (modifiers == TerminalModifiers.NONE) {
            writeSs3(unmodifiedFinal)
            return
        }

        encodeCsiModifierFinal(
            prefixNumber = 1,
            modifiers = modifiers,
            finalByte = unmodifiedFinal,
        )
    }

    private fun encodeTildeKey(number: Int, modifiers: Int) {
        scratch.clear()
        scratch.appendByte(ControlCode.ESC)
        scratch.appendByte('['.code)
        scratch.appendDecimal(number)

        if (modifiers != TerminalModifiers.NONE) {
            scratch.appendByte(';'.code)
            scratch.appendDecimal(TerminalModifiers.toCsiModifierParam(modifiers))
        }

        scratch.appendByte('~'.code)
        scratch.writeTo(output)
    }

    private fun encodeCsiModifierFinal(
        prefixNumber: Int,
        modifiers: Int,
        finalByte: Int,
    ) {
        scratch.clear()
        scratch.appendByte(ControlCode.ESC)
        scratch.appendByte('['.code)
        scratch.appendDecimal(prefixNumber)
        scratch.appendByte(';'.code)
        scratch.appendDecimal(TerminalModifiers.toCsiModifierParam(modifiers))
        scratch.appendByte(finalByte)
        scratch.writeTo(output)
    }

    private fun encodeKeypad(key: TerminalKey, modeBits: Long) {
        if (TerminalInputState.isApplicationKeypad(modeBits)) {
            val final = applicationKeypadFinal(key)
            if (final >= 0) {
                writeSs3(final)
            }
            return
        }

        if (key == TerminalKey.NUMPAD_ENTER) {
            encodeEnter(modeBits)
            return
        }

        val ascii = normalKeypadAscii(key)
        if (ascii >= 0) {
            output.writeByte(ascii)
        }
    }

    private fun applicationKeypadFinal(key: TerminalKey): Int {
        return when (key) {
            TerminalKey.NUMPAD_ENTER -> 'M'.code
            TerminalKey.NUMPAD_0 -> 'p'.code
            TerminalKey.NUMPAD_1 -> 'q'.code
            TerminalKey.NUMPAD_2 -> 'r'.code
            TerminalKey.NUMPAD_3 -> 's'.code
            TerminalKey.NUMPAD_4 -> 't'.code
            TerminalKey.NUMPAD_5 -> 'u'.code
            TerminalKey.NUMPAD_6 -> 'v'.code
            TerminalKey.NUMPAD_7 -> 'w'.code
            TerminalKey.NUMPAD_8 -> 'x'.code
            TerminalKey.NUMPAD_9 -> 'y'.code
            TerminalKey.NUMPAD_DECIMAL -> 'n'.code
            TerminalKey.NUMPAD_DIVIDE -> 'o'.code
            TerminalKey.NUMPAD_MULTIPLY -> 'j'.code
            TerminalKey.NUMPAD_SUBTRACT -> 'm'.code
            TerminalKey.NUMPAD_ADD -> 'k'.code
            else -> -1
        }
    }

    private fun normalKeypadAscii(key: TerminalKey): Int {
        return when (key) {
            TerminalKey.NUMPAD_0 -> '0'.code
            TerminalKey.NUMPAD_1 -> '1'.code
            TerminalKey.NUMPAD_2 -> '2'.code
            TerminalKey.NUMPAD_3 -> '3'.code
            TerminalKey.NUMPAD_4 -> '4'.code
            TerminalKey.NUMPAD_5 -> '5'.code
            TerminalKey.NUMPAD_6 -> '6'.code
            TerminalKey.NUMPAD_7 -> '7'.code
            TerminalKey.NUMPAD_8 -> '8'.code
            TerminalKey.NUMPAD_9 -> '9'.code
            TerminalKey.NUMPAD_DECIMAL -> '.'.code
            TerminalKey.NUMPAD_DIVIDE -> '/'.code
            TerminalKey.NUMPAD_MULTIPLY -> '*'.code
            TerminalKey.NUMPAD_SUBTRACT -> '-'.code
            TerminalKey.NUMPAD_ADD -> '+'.code
            else -> -1
        }
    }

    private fun controlCodeFor(codepoint: Int): Int {
        val lower = when (codepoint) {
            in 'A'.code..'Z'.code -> codepoint + 32
            else -> codepoint
        }

        return when (lower) {
            in 'a'.code..'z'.code -> lower - 'a'.code + 1
            '@'.code, ' '.code -> 0x00
            '['.code -> 0x1b
            '\\'.code -> 0x1c
            ']'.code -> 0x1d
            '^'.code -> 0x1e
            '_'.code -> 0x1f
            '?'.code -> 0x7f
            else -> -1
        }
    }

    private fun writeCsiFinal(finalByte: Int) {
        scratch.clear()
        scratch.appendByte(ControlCode.ESC)
        scratch.appendByte('['.code)
        scratch.appendByte(finalByte)
        scratch.writeTo(output)
    }

    private fun writeSs3(finalByte: Int) {
        scratch.clear()
        scratch.appendByte(ControlCode.ESC)
        scratch.appendByte('O'.code)
        scratch.appendByte(finalByte)
        scratch.writeTo(output)
    }

    private fun writeUtf8Codepoint(codepoint: Int) {
        when {
            codepoint <= 0x7f -> output.writeByte(codepoint)

            codepoint <= 0x7ff -> {
                scratch.clear()
                scratch.appendByte(0xc0 or (codepoint shr 6))
                scratch.appendByte(0x80 or (codepoint and 0x3f))
                scratch.writeTo(output)
            }

            codepoint <= 0xffff -> {
                scratch.clear()
                scratch.appendByte(0xe0 or (codepoint shr 12))
                scratch.appendByte(0x80 or ((codepoint shr 6) and 0x3f))
                scratch.appendByte(0x80 or (codepoint and 0x3f))
                scratch.writeTo(output)
            }

            else -> {
                scratch.clear()
                scratch.appendByte(0xf0 or (codepoint shr 18))
                scratch.appendByte(0x80 or ((codepoint shr 12) and 0x3f))
                scratch.appendByte(0x80 or ((codepoint shr 6) and 0x3f))
                scratch.appendByte(0x80 or (codepoint and 0x3f))
                scratch.writeTo(output)
            }
        }
    }

    private fun writeStatic(bytes: ByteArray) {
        output.writeBytes(bytes, 0, bytes.size)
    }
}
