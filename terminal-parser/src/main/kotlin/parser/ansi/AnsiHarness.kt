package com.gagik.parser.ansi

import com.gagik.parser.runtime.ParserState

internal class AnsiHarness(
    val state: ParserState = ParserState(),
    val sink: RecordingTerminalCommandSink = RecordingTerminalCommandSink(),
    val dispatcher: RecordingCommandDispatcher = RecordingCommandDispatcher(),
    val printable: RecordingPrintableActionSink = RecordingPrintableActionSink(),
) {
    private val engine = ActionEngine(
        sink = sink,
        dispatcher = dispatcher,
        printableSink = printable,
    )

    fun accept(bytes: ByteArray) {
        var i = 0
        while (i < bytes.size) {
            acceptByte(bytes[i].toInt() and 0xff)
            i++
        }
    }

    fun acceptAscii(text: String) {
        accept(text.encodeToByteArray())
    }

    fun acceptByte(byteValue: Int) {
        require(byteValue in 0..255)

        val byteClass = ByteClass.classify(byteValue)
        val previousState = state.fsmState
        val transition = AnsiStateMachine.transition(previousState, byteClass)
        val nextState = AnsiStateMachine.nextState(transition)
        val action = AnsiStateMachine.action(transition)

        engine.execute(
            state = state,
            previousState = previousState,
            nextState = nextState,
            action = action,
            byteValue = byteValue,
        )
    }
}