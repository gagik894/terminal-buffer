package com.gagik.core.api

/**
 * Terminal-to-host response channel.
 *
 * Host applications should drain these bytes and write them to the PTY/process
 * input side. Parser/integration code should use the request methods; core owns
 * response contents that depend on core state such as cursor position.
 */
interface TerminalResponseChannel : TerminalHostResponseReader {
    val pendingResponseBytes: Int

    override fun readResponseBytes(
        dst: ByteArray,
        offset: Int,
        length: Int,
    ): Int

    fun clearResponseBytes()

    fun requestDeviceStatusReport(mode: Int, decPrivate: Boolean)

    fun requestDeviceAttributes(kind: Int, parameter: Int)

    fun setWindowSizePixels(width: Int, height: Int)

    fun requestWindowReport(mode: Int)

    companion object {
        const val DEVICE_ATTRIBUTES_PRIMARY: Int = 0
        const val DEVICE_ATTRIBUTES_SECONDARY: Int = 1
        const val DEVICE_ATTRIBUTES_TERTIARY: Int = 2

        const val WINDOW_REPORT_PIXELS: Int = 14
        const val WINDOW_REPORT_GRID_CELLS: Int = 18
    }
}
