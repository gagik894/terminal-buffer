package com.gagik.parser.ansi

/**
 * Internal command ids for routed CSI sequences.
 *
 * These ids are parser-dispatch vocabulary, not core API concepts.
 */
internal object CsiCommand {
    const val UNKNOWN: Int = 0

    const val CUU: Int = 1
    const val CUD: Int = 2
    const val CUF: Int = 3
    const val CUB: Int = 4
    const val CNL: Int = 5
    const val CPL: Int = 6
    const val CHA: Int = 7
    const val CUP: Int = 8
    const val VPA: Int = 9

    const val ED: Int = 10
    const val EL: Int = 11
    const val IL: Int = 12
    const val DL: Int = 13
    const val ICH: Int = 14
    const val DCH: Int = 15
    const val ECH: Int = 16
    const val SU: Int = 17
    const val SD: Int = 18

    const val SM_ANSI: Int = 19
    const val RM_ANSI: Int = 20
    const val SM_DEC: Int = 21
    const val RM_DEC: Int = 22

    const val DECSTR: Int = 23
    const val SGR: Int = 24
}
