package com.gagik.core.buffer.impl

import com.gagik.core.api.TerminalResponseChannel
import com.gagik.core.state.TerminalState

internal class TerminalResponseChannelImpl(
    private val state: TerminalState,
) : TerminalResponseChannel {
    override val pendingResponseBytes: Int
        get() = state.hostResponses.pendingByteCount

    override fun readResponseBytes(
        destination: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        return state.hostResponses.read(destination, offset, length)
    }

    override fun clearResponseBytes() {
        state.hostResponses.clear()
    }

    override fun requestDeviceStatusReport(mode: Int, decPrivate: Boolean) {
        when {
            !decPrivate && mode == 5 -> state.hostResponses.enqueueAscii("\u001B[0n")
            mode == 6 -> enqueueCursorPositionReport(decPrivate)
        }
    }

    override fun requestDeviceAttributes(kind: Int, parameter: Int) {
        if (parameter != 0) return

        when (kind) {
            TerminalResponseChannel.DEVICE_ATTRIBUTES_PRIMARY -> {
                // Conservative VT100-with-advanced-video identity. Avoid overclaiming xterm.
                state.hostResponses.enqueueAscii("\u001B[?1;2c")
            }
            TerminalResponseChannel.DEVICE_ATTRIBUTES_SECONDARY -> {
                // Generic versionless secondary DA. Avoid leaking product/version identity.
                state.hostResponses.enqueueAscii("\u001B[>0;0;0c")
            }
            TerminalResponseChannel.DEVICE_ATTRIBUTES_TERTIARY -> {
                // DA3 can expose a stable terminal unit id. Keep it silent until policy exists.
            }
        }
    }

    override fun setWindowSizePixels(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            state.windowPixelWidth = 0
            state.windowPixelHeight = 0
            return
        }

        state.windowPixelWidth = width
        state.windowPixelHeight = height
    }

    override fun requestWindowReport(mode: Int) {
        when (mode) {
            TerminalResponseChannel.WINDOW_REPORT_PIXELS -> {
                if (state.windowPixelWidth > 0 && state.windowPixelHeight > 0) {
                    enqueueWindowReport(
                        reportType = 4,
                        height = state.windowPixelHeight,
                        width = state.windowPixelWidth,
                    )
                }
            }
            TerminalResponseChannel.WINDOW_REPORT_GRID_CELLS -> enqueueWindowReport(
                reportType = 8,
                height = state.dimensions.height,
                width = state.dimensions.width,
            )
        }
    }

    private fun enqueueCursorPositionReport(decPrivate: Boolean) {
        state.hostResponses.enqueueByte(0x1B)
        state.hostResponses.enqueueByte('['.code)
        if (decPrivate) {
            state.hostResponses.enqueueByte('?'.code)
        }
        state.hostResponses.enqueuePositiveDecimal(state.cursor.row + 1)
        state.hostResponses.enqueueByte(';'.code)
        state.hostResponses.enqueuePositiveDecimal(state.cursor.col + 1)
        state.hostResponses.enqueueByte('R'.code)
    }

    private fun enqueueWindowReport(
        reportType: Int,
        height: Int,
        width: Int,
    ) {
        state.hostResponses.enqueueByte(0x1B)
        state.hostResponses.enqueueByte('['.code)
        state.hostResponses.enqueuePositiveDecimal(reportType)
        state.hostResponses.enqueueByte(';'.code)
        state.hostResponses.enqueuePositiveDecimal(height)
        state.hostResponses.enqueueByte(';'.code)
        state.hostResponses.enqueuePositiveDecimal(width)
        state.hostResponses.enqueueByte('t'.code)
    }
}
