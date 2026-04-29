package com.gagik.integration

/**
 * Host-facing events emitted while parser commands are mapped to core state.
 *
 * This sink is intentionally metadata-only. Grid mutation, terminal modes, and
 * terminal-to-host byte responses remain owned by [CoreTerminalCommandSink] and
 * the public core APIs.
 */
interface TerminalHostEventSink {
    /**
     * Called when the parser emits BEL.
     */
    fun bell()

    /**
     * Called after the OSC icon title metadata changes.
     *
     * @param title new icon title.
     */
    fun iconTitleChanged(title: String)

    /**
     * Called after the OSC window title metadata changes.
     *
     * @param title new window title.
     */
    fun windowTitleChanged(title: String)

    companion object {
        /**
         * Event sink used when the host does not need metadata callbacks.
         */
        @JvmField
        val NONE: TerminalHostEventSink = object : TerminalHostEventSink {
            override fun bell() = Unit
            override fun iconTitleChanged(title: String) = Unit
            override fun windowTitleChanged(title: String) = Unit
        }
    }
}
