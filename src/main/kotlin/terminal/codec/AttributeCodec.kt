package com.gagik.terminal.codec

import com.gagik.terminal.model.Attributes

/**
 * Encodes terminal cell attributes into a compact Int.
 *
 * Color range:
 * - `0` = Default (terminal base color)
 * - `1..16` = Standard ANSI colors
 * - `17..31` = Reserved for future color expansion
 *
 * Layout:
 * - bits `0..4`   foreground (`0..31`)
 * - bits `5..9`   background (`0..31`)
 * - bit `10`      bold
 * - bit `11`      italic
 * - bit `12`      underline
 * - bit `13`      selective-erase protection (DECSCA), reserved for protection - do not reuse
 *
 * Bit 13 is permanently reserved for protected-cell semantics. Future SGR or
 * color expansion must preserve that position so protected cells cannot be
 * silently reinterpreted.
 */
internal object AttributeCodec {

    /** Maximum color value for standard ANSI colors (0 = default, 1-16 = ANSI). */
    const val MAX_ANSI_COLOR = 16

    /** Maximum color value supported by the codec (includes the reserved range). */
    const val MAX_COLOR = 31

    /**
     * Packs the given attributes into a single Int.
     *
     * The method uses bitwise operations to pack the attributes:
     * - foreground in bits `0..4`
     * - background in bits `5..9`
     * - bold in bit `10`
     * - italic in bit `11`
     * - underline in bit `12`
     * - selective-erase protection in bit `13`
     *
     * @throws IllegalArgumentException if `fg` or `bg` are out of range
     */
    fun pack(
        fg: Int,
        bg: Int,
        bold: Boolean,
        italic: Boolean,
        underline: Boolean,
        protected: Boolean = false
    ): Int {
        require(fg in 0..MAX_COLOR) { "fg must be in 0..$MAX_COLOR, was $fg" }
        require(bg in 0..MAX_COLOR) { "bg must be in 0..$MAX_COLOR, was $bg" }

        var v = fg
        v = v or (bg shl 5)
        if (bold) v = v or (1 shl 10)
        if (italic) v = v or (1 shl 11)
        if (underline) v = v or (1 shl 12)
        if (protected) v = v or (1 shl 13)
        return v
    }

    /** Extracts the foreground color from the packed attribute Int. */
    fun foreground(v: Int) = v and 0b11111

    /** Extracts the background color from the packed attribute Int. */
    fun background(v: Int) = (v shr 5) and 0b11111

    /** Returns true when bold is set. */
    fun isBold(v: Int) = v and (1 shl 10) != 0

    /** Returns true when italic is set. */
    fun isItalic(v: Int) = v and (1 shl 11) != 0

    /** Returns true when underline is set. */
    fun isUnderline(v: Int) = v and (1 shl 12) != 0

    /** Returns true when selective-erase protection is set. */
    fun isProtected(v: Int) = v and (1 shl 13) != 0

    /** Returns [v] with only the selective-erase protection bit changed. */
    fun withProtected(v: Int, enabled: Boolean): Int {
        return if (enabled) {
            v or (1 shl 13)
        } else {
            v and (1 shl 13).inv()
        }
    }

    /** Unpacks a packed attribute Int into a structured [Attributes] instance. */
    fun unpack(v: Int): Attributes {
        return Attributes(
            fg = foreground(v),
            bg = background(v),
            bold = isBold(v),
            italic = isItalic(v),
            underline = isUnderline(v),
            selectiveEraseProtected = isProtected(v)
        )
    }
}
