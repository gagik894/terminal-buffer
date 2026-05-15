package com.gagik.terminal.ui.swing.render.font

import java.awt.Font
import java.awt.GraphicsEnvironment
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Loads installed system fonts away from the Swing event-dispatch thread.
 */
internal object TerminalSystemFallbackFonts {
    private val started = AtomicBoolean(false)
    private val loadedFonts = AtomicReference<List<Font>?>(null)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "terminal-ui-system-font-loader").apply {
            isDaemon = true
        }
    }

    /**
     * Returns loaded fonts, or starts a background load and returns empty.
     */
    fun fontsOrStartLoading(): List<Font> {
        val loaded = loadedFonts.get()
        if (loaded != null) return loaded

        if (started.compareAndSet(false, true)) {
            executor.execute {
                loadedFonts.compareAndSet(null, loadSystemFonts())
            }
        }
        return emptyList()
    }

    private fun loadSystemFonts(): List<Font> {
        return try {
            GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .allFonts
                .distinctBy { it.family }
        } catch (_: RuntimeException) {
            emptyList()
        }
    }
}