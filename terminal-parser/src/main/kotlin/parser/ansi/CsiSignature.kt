package com.gagik.parser.ansi

/**
 * Packs CSI sequence structural metadata into a stable dispatch key.
 *
 * Layout:
 * - bits 0..7:   final byte
 * - bits 8..15:  CSI private marker, or 0 when absent
 * - bits 16..47: intermediate bytes packed low-to-high
 * - bits 48..51: intermediate count
 */
internal object CsiSignature {
    @JvmStatic
    fun encode(
        finalByte: Int,
        privateMarker: Int,
        intermediates: Int,
        intermediateCount: Int,
    ): Long {
        return (finalByte.toLong() and 0xffL) or
            ((privateMarker.toLong() and 0xffL) shl 8) or
            ((intermediates.toLong() and 0xffffffffL) shl 16) or
            ((intermediateCount.toLong() and 0x0fL) shl 48)
    }
}
