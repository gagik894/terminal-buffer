package com.gagik.parser.ansi

/**
 * CSI signature routing table.
 *
 * Production rule:
 * - This shape is generator-compatible.
 * - Arrays are primitive and already sorted.
 * - Do not sort one array independently.
 * - Do not allocate Pair/List registry objects at runtime.
 *
 * The init block is only a cheap integrity guard.
 */
internal object GeneratedCsiDispatchTable {
    private val SIGNATURES: LongArray = longArrayOf(
        64L,                 // CSI @  ICH
        65L,                 // CSI A  CUU
        66L,                 // CSI B  CUD
        67L,                 // CSI C  CUF
        68L,                 // CSI D  CUB
        69L,                 // CSI E  CNL
        70L,                 // CSI F  CPL
        71L,                 // CSI G  CHA
        72L,                 // CSI H  CUP
        73L,                 // CSI I  CHT
        74L,                 // CSI J  ED
        75L,                 // CSI K  EL
        76L,                 // CSI L  IL
        77L,                 // CSI M  DL
        80L,                 // CSI P  DCH
        83L,                 // CSI S  SU
        84L,                 // CSI T  SD
        88L,                 // CSI X  ECH
        90L,                 // CSI Z  CBT
        100L,                // CSI d  VPA
        102L,                // CSI f  HVP -> CUP
        103L,                // CSI g  TBC
        104L,                // CSI h  SM ANSI
        108L,                // CSI l  RM ANSI
        109L,                // CSI m  SGR
        114L,                // CSI r  DECSTBM
        16232L,              // CSI ? h  DECSET
        16236L,              // CSI ? l  DECRST
        281474978873456L,    // CSI ! p  DECSTR
    )

    private val COMMANDS: IntArray = intArrayOf(
        CsiCommand.ICH,
        CsiCommand.CUU,
        CsiCommand.CUD,
        CsiCommand.CUF,
        CsiCommand.CUB,
        CsiCommand.CNL,
        CsiCommand.CPL,
        CsiCommand.CHA,
        CsiCommand.CUP,
        CsiCommand.CHT,
        CsiCommand.ED,
        CsiCommand.EL,
        CsiCommand.IL,
        CsiCommand.DL,
        CsiCommand.DCH,
        CsiCommand.SU,
        CsiCommand.SD,
        CsiCommand.ECH,
        CsiCommand.CBT,
        CsiCommand.VPA,
        CsiCommand.CUP,
        CsiCommand.TBC,
        CsiCommand.SM_ANSI,
        CsiCommand.RM_ANSI,
        CsiCommand.SGR,
        CsiCommand.DECSTBM,
        CsiCommand.SM_DEC,
        CsiCommand.RM_DEC,
        CsiCommand.DECSTR,
    )

    init {
        check(SIGNATURES.size == COMMANDS.size) {
            "CSI signature and command tables must have equal length"
        }

        var i = 1
        while (i < SIGNATURES.size) {
            check(SIGNATURES[i - 1] < SIGNATURES[i]) {
                "CSI signatures must be strictly sorted at index $i"
            }
            i++
        }
    }

    @JvmStatic
    fun lookup(signature: Long): Int {
        var low = 0
        var high = SIGNATURES.size - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val value = SIGNATURES[mid]
            if (value < signature) {
                low = mid + 1
            } else if (value > signature) {
                high = mid - 1
            } else {
                return COMMANDS[mid]
            }
        }

        return CsiCommand.UNKNOWN
    }
}
