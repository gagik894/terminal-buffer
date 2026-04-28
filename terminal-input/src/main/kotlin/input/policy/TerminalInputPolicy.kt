package com.gagik.terminal.input.policy

/**
 * Runtime policy for keyboard encodings that do not have one unambiguous
 * terminal byte representation.
 *
 * The production encoder suppresses unsupported combinations by default rather
 * than throwing, because UI toolkits may report platform-specific key states.
 *
 * @property backspacePolicy byte sent for the Backspace key.
 * @property metaKeyPolicy handling for Meta-only printable and legacy key
 * encodings.
 * @property unsupportedModifiedKeyPolicy handling for valid key events whose
 * modifier combination has no supported encoding in the active protocol set.
 * @property altSendsEscapePrefix when true, Alt prefixes applicable legacy
 * encodings with ESC.
 * @property mouseCoordinateLimitPolicy handling for legacy mouse coordinates
 * outside the bounded `ESC [ M` byte range.
 */
data class TerminalInputPolicy(
    val backspacePolicy: BackspacePolicy = BackspacePolicy.DELETE,
    val metaKeyPolicy: MetaKeyPolicy = MetaKeyPolicy.ESC_PREFIX,
    val unsupportedModifiedKeyPolicy: UnsupportedModifiedKeyPolicy =
        UnsupportedModifiedKeyPolicy.SUPPRESS,
    val altSendsEscapePrefix: Boolean = true,
    val mouseCoordinateLimitPolicy: MouseCoordinateLimitPolicy =
        MouseCoordinateLimitPolicy.SUPPRESS_OUT_OF_RANGE,
)

/**
 * Backspace byte selection.
 */
enum class BackspacePolicy {
    /** Send DEL, 0x7F. */
    DELETE,

    /** Send BS, 0x08. */
    BACKSPACE,
}

/**
 * Meta key handling for legacy printable/control encodings.
 *
 * Special keys with xterm CSI modifier encodings may still encode Meta as
 * modifier parameter 9.
 */
enum class MetaKeyPolicy {
    /** Prefix the encoded key with ESC. */
    ESC_PREFIX,

    /** Ignore Meta and encode the event as if Meta were not present. */
    IGNORE_META,

    /** Suppress the event when Meta is the governing modifier. */
    SUPPRESS_EVENT,
}

/**
 * Handling for valid modified key events without a supported encoding.
 */
enum class UnsupportedModifiedKeyPolicy {
    /** Emit no bytes. */
    SUPPRESS,

    /** Drop the unsupported modifier and emit the unmodified key encoding. */
    EMIT_UNMODIFIED,
}

/**
 * Policy for legacy mouse coordinates beyond the byte-safe range.
 */
enum class MouseCoordinateLimitPolicy {
    /** Suppress events whose one-based coordinate is greater than 223. */
    SUPPRESS_OUT_OF_RANGE,

    /** Clamp events whose one-based coordinate is greater than 223 to 223. */
    CLAMP_TO_MAX,
}
