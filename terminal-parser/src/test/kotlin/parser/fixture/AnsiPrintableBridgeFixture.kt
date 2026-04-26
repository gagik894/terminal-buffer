package com.gagik.parser.fixture

import com.gagik.parser.ansi.*
import com.gagik.parser.runtime.ParserState
import com.gagik.parser.text.PrintableProcessor
import com.gagik.parser.text.PrintableProcessorActionSink

internal class AnsiPrintableBridgeFixture(
    val state: ParserState = ParserState(),
    val sink: RecordingTerminalCommandSink = RecordingTerminalCommandSink(),
) {
    private val processor = PrintableProcessor(sink)
    private val engine = ActionEngine(
        sink = sink,
        dispatcher = AnsiCommandDispatcher,
        printableSink = PrintableProcessorActionSink(processor),
    )

    fun acceptAscii(text: String) {
        for (byteValue in text.encodeToByteArray()) {
            acceptByte(byteValue.toInt() and 0xff)
        }
    }

    fun acceptBytes(vararg byteValues: Int) {
        for (byteValue in byteValues) {
            acceptByte(byteValue)
        }
    }

    fun acceptByte(byteValue: Int) {
        val byteClass = ByteClass.classify(byteValue)
        val transition = AnsiStateMachine.transition(state.fsmState, byteClass)
        engine.execute(
            state = state,
            nextState = AnsiStateMachine.nextState(transition),
            action = AnsiStateMachine.action(transition),
            byteValue = byteValue,
        )
    }

    fun endOfInput() {
        processor.flush(state)
    }
}
