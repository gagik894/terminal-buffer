package com.gagik.parser.ansi

import com.gagik.parser.TerminalCommandSink
import com.gagik.parser.runtime.ParserState

/**
 * Executes parser-internal FSM actions against [ParserState].
 *
 * Boundaries:
 * - Owns parser-state mutation.
 * - Flushes pending printable output before structural commands/dispatch.
 * - Delegates semantic control-sequence translation to [CommandDispatcher].
 * - Does not own UTF-8 decoding internals beyond forwarding raw non-ASCII bytes.
 *
 * Important:
 * - The caller must pass both [previousState] and [nextState].
 * - This is required to interpret ESC \\ as ST termination when transitioning out of
 *   string states such as OSC/DCS.
 */
internal class ActionEngine(
    private val sink: TerminalCommandSink,
    private val dispatcher: CommandDispatcher,
    private val printableSink: PrintableActionSink,
) {

    /**
     * Executes one FSM action.
     *
     * @param state parser runtime state
     * @param previousState state before applying the transition
     * @param nextState state after applying the transition
     * @param action parser-internal action id from [FsmAction]
     * @param byteValue raw input byte in range 0..255
     */
    fun execute(
        state: ParserState,
        previousState: Int,
        nextState: Int,
        action: Int,
        byteValue: Int,
    ) {
        require(byteValue in 0..255) { "byteValue out of range: $byteValue" }

        when (action) {
            FsmAction.IGNORE -> {
                state.fsmState = nextState
            }

            FsmAction.EXECUTE -> {
                executeControl(state, previousState, nextState, byteValue, clearAfter = false)
            }

            FsmAction.EXECUTE_AND_CLEAR -> {
                executeControl(state, previousState, nextState, byteValue, clearAfter = true)
            }

            FsmAction.CLEAR_SEQUENCE -> {
                flushPrintable(state)
                state.clearSequenceState()
                state.fsmState = nextState
            }

            FsmAction.PRINT_ASCII -> {
                state.fsmState = nextState
                printableSink.onAsciiByte(state, byteValue)
            }

            FsmAction.PRINT_UTF8 -> {
                state.fsmState = nextState
                printableSink.onUtf8Byte(state, byteValue)
            }

            FsmAction.COLLECT_INTERMEDIATE -> {
                flushPrintable(state)
                collectIntermediate(state, byteValue)
                state.fsmState = nextState
            }

            FsmAction.PARAM_DIGIT -> {
                flushPrintable(state)
                appendParamDigit(state, byteValue)
                state.fsmState = nextState
            }

            FsmAction.PARAM_SEPARATOR -> {
                flushPrintable(state)
                appendParamSeparator(state)
                state.fsmState = nextState
            }

            FsmAction.PARAM_COLON -> {
                flushPrintable(state)
                appendParamColon(state)
                state.fsmState = nextState
            }

            FsmAction.SET_PRIVATE_MARKER -> {
                flushPrintable(state)
                setPrivateMarker(state, byteValue)
                state.fsmState = nextState
            }

            FsmAction.ESC_DISPATCH -> {
                flushPrintable(state)

                // Special case: ESC \ terminates OSC/DCS/SOS/PM/APC-like string states.
                if (byteValue == '\\'.code && terminatesStringWithSt(previousState)) {
                    terminateString(state, previousState, aborted = false)
                    state.fsmState = AnsiState.GROUND
                    return
                }

                dispatcher.dispatchEsc(
                    sink = sink,
                    state = state,
                    finalByte = byteValue,
                )
                state.clearSequenceState()
                state.fsmState = nextState
            }

            FsmAction.CSI_DISPATCH -> {
                flushPrintable(state)
                dispatcher.dispatchCsi(
                    sink = sink,
                    state = state,
                    finalByte = byteValue,
                )
                state.clearSequenceState()
                state.fsmState = nextState
            }

            FsmAction.OSC_START -> {
                flushPrintable(state)
                state.clearSequenceState()
                state.clearPayloadState()

                // OSC command code is not fully parsed yet.
                // Milestone A only accumulates bounded payload bytes.
                state.payloadCode = -1
                state.fsmState = nextState
            }

            FsmAction.OSC_PUT_ASCII -> {
                putPayloadByte(state, byteValue)
                state.fsmState = nextState
            }

            FsmAction.OSC_PUT_UTF8 -> {
                putPayloadByte(state, byteValue)
                state.fsmState = nextState
            }

            FsmAction.DCS_IGNORE_START -> {
                flushPrintable(state)
                state.clearPayloadState()
                putPayloadByte(state, byteValue)
                state.fsmState = nextState
            }

            FsmAction.DCS_PUT_ASCII -> {
                putPayloadByte(state, byteValue)
                state.fsmState = nextState
            }

            FsmAction.DCS_PUT_UTF8 -> {
                putPayloadByte(state, byteValue)
                state.fsmState = nextState
            }

            else -> error("Unknown FsmAction: $action")
        }
    }

    private fun executeControl(
        state: ParserState,
        previousState: Int,
        nextState: Int,
        byteValue: Int,
        clearAfter: Boolean,
    ) {
        // BEL terminates OSC by protocol.
        if (byteValue == 0x07 && previousState == AnsiState.OSC_STRING) {
            terminateString(state, previousState, aborted = false)
            if (clearAfter) {
                state.clearSequenceState()
            }
            state.fsmState = AnsiState.GROUND
            return
        }

        // In all other cases, controls are structural.
        flushPrintable(state)
        dispatcher.executeControl(
            sink = sink,
            state = state,
            controlByte = byteValue,
        )

        if (clearAfter) {
            state.clearSequenceState()
        }
        state.fsmState = nextState
    }

    private fun terminatesStringWithSt(previousState: Int): Boolean {
        return previousState == AnsiState.OSC_STRING ||
                previousState == AnsiState.DCS_PASSTHROUGH ||
                previousState == AnsiState.SOS_PM_APC_STRING ||
                previousState == AnsiState.IGNORE_UNTIL_ST
    }

    private fun terminateString(
        state: ParserState,
        previousState: Int,
        aborted: Boolean,
    ) {
        when (previousState) {
            AnsiState.OSC_STRING -> {
                sink.onOsc(
                    commandCode = state.payloadCode,
                    payload = state.payloadBuffer,
                    length = state.payloadLength,
                    overflowed = state.payloadOverflowed,
                )
            }

            AnsiState.DCS_PASSTHROUGH -> {
                // Milestone A intentionally does not expose DCS metadata dispatch.
                // Payload is dropped after bounded accumulation.
            }

            AnsiState.SOS_PM_APC_STRING,
            AnsiState.IGNORE_UNTIL_ST -> {
                // Ignored by design.
            }
        }

        state.clearPayloadState()
        state.clearSequenceState()
    }

    private fun flushPrintable(state: ParserState) {
        printableSink.flush(state)
    }

    private fun collectIntermediate(state: ParserState, byteValue: Int) {
        if (state.intermediateCount >= 4) {
            return
        }
        state.intermediates = state.intermediates or (byteValue shl (state.intermediateCount * 8))
        state.intermediateCount++
    }

    private fun appendParamDigit(state: ParserState, byteValue: Int) {
        val digit = byteValue - '0'.code
        require(digit in 0..9) { "Expected decimal digit byte, got: $byteValue" }

        if (!ensureParamSlot(state)) {
            return
        }

        val index = state.paramCount - 1
        val current = state.params[index]
        state.params[index] = if (current < 0) {
            digit
        } else {
            saturatingAppendDecimal(current, digit)
        }
        state.currentParamStarted = true
    }

    private fun appendParamSeparator(state: ParserState) {
        if (!state.currentParamStarted) {
            if (!ensureParamSlot(state)) {
                return
            }
            state.params[state.paramCount - 1] = -1
        }

        if (state.paramCount >= state.params.size) {
            state.currentParamStarted = false
            return
        }

        state.paramCount++
        state.currentParamStarted = false
    }

    private fun appendParamColon(state: ParserState) {
        if (!state.currentParamStarted) {
            if (!ensureParamSlot(state)) {
                return
            }
            state.params[state.paramCount - 1] = -1
        }

        val nextIndex = state.paramCount
        if (nextIndex < 32) {
            state.subParameterMask = state.subParameterMask or (1 shl nextIndex)
        }

        if (state.paramCount >= state.params.size) {
            state.currentParamStarted = false
            return
        }

        state.paramCount++
        state.currentParamStarted = false
    }

    private fun setPrivateMarker(state: ParserState, byteValue: Int) {
        if (state.privateMarker == 0) {
            state.privateMarker = byteValue
        }
    }

    private fun ensureParamSlot(state: ParserState): Boolean {
        if (state.currentParamStarted) {
            return true
        }

        if (state.paramCount >= state.params.size) {
            return false
        }

        state.params[state.paramCount] = -1
        state.paramCount++
        state.currentParamStarted = true
        return true
    }

    private fun putPayloadByte(state: ParserState, byteValue: Int) {
        if (state.payloadOverflowed) {
            return
        }

        if (state.payloadLength >= state.payloadBuffer.size) {
            state.payloadOverflowed = true
            return
        }

        state.payloadBuffer[state.payloadLength] = byteValue.toByte()
        state.payloadLength++
    }

    private fun saturatingAppendDecimal(value: Int, digit: Int): Int {
        return if (value > 214_748_363) {
            Int.MAX_VALUE
        } else {
            value * 10 + digit
        }
    }
}

/**
 * Narrow bridge for printable ingress and grapheme buffering.
 *
 * This keeps ActionEngine from knowing UTF-8 decoder details or grapheme storage details.
 */
internal interface PrintableActionSink {
    fun onAsciiByte(state: ParserState, byteValue: Int)
    fun onUtf8Byte(state: ParserState, byteValue: Int)
    fun flush(state: ParserState)
}

/**
 * Semantic dispatcher boundary used by ActionEngine.
 *
 * ESC/CSI/control meaning lives here, not in the matrix and not in ActionEngine.
 */
internal interface CommandDispatcher {
    fun executeControl(
        sink: TerminalCommandSink,
        state: ParserState,
        controlByte: Int,
    )

    fun dispatchEsc(
        sink: TerminalCommandSink,
        state: ParserState,
        finalByte: Int,
    )

    fun dispatchCsi(
        sink: TerminalCommandSink,
        state: ParserState,
        finalByte: Int,
    )
}