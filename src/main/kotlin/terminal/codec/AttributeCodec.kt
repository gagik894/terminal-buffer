package com.gagik.terminal.codec

/**
 * Encodes terminal cell attributes into a compact Int.
 *
 * Color range:
 * - 0 = Default (terminal's base color)
 * - 1-16 = Standard ANSI colors
 * - 17-31 = Reserved for 256-color extension
 *
 * Layout:
 * bits 0–4   foreground (0–31)
 * bits 5–9   background (0–31)
 * bit 10     bold
 * bit 11     italic
 * bit 12     underline
 */
object AttributeCodec {

    /** Maximum color value for standard ANSI colors (0 = default, 1-16 = ANSI) */
    const val MAX_ANSI_COLOR = 16

    /** Maximum color value supported by the codec (includes reserved range) */
    const val MAX_COLOR = 31

    /**
     * Packs the given attributes into a single Int.
     * @param fg Foreground color (0–31, see class docs for meaning)
     * @param bg Background color (0–31, see class docs for meaning)
     * @param bold Whether the text is bold
     * @param italic Whether the text is italic
     * @param underline Whether the text is underlined
     * @return An Int encoding all the attributes
     * @throws IllegalArgumentException if fg or bg are out of range
     *
     * The method uses bitwise operations to pack the attributes:
     * - The foreground color is stored in bits 0–4.
     * - The background color is stored in bits 5–9.
     * - The bold attribute is stored in bit 10.
     * - The italic attribute is stored in bit 11.
     * - The underline attribute is stored in bit 12.
     */
    fun pack(fg: Int, bg: Int, bold: Boolean, italic: Boolean, underline: Boolean): Int {
        require(fg in 0..MAX_COLOR) { "fg must be in 0..$MAX_COLOR, was $fg" }
        require(bg in 0..MAX_COLOR) { "bg must be in 0..$MAX_COLOR, was $bg" }

        var v = fg
        v = v or (bg shl 5)
        if (bold) v = v or (1 shl 10)
        if (italic) v = v or (1 shl 11)
        if (underline) v = v or (1 shl 12)
        return v
    }

    /**
     * Extracts the foreground color from the packed attribute Int.
     * @param v The packed attribute Int
     * @return The foreground color (0–31)
     */
    fun foreground(v: Int) = v and 0b11111

    /**
     * Extracts the background color from the packed attribute Int.
     * @param v The packed attribute Int
     * @return The background color (0–31)
     */
    fun background(v: Int) = (v shr 5) and 0b11111

    /**
     * Checks if the bold attribute is set in the packed attribute Int.
     * @param v The packed attribute Int
     * @return True if bold is set, false otherwise
     */
    fun isBold(v: Int) = v and (1 shl 10) != 0

    /**
     * Checks if the italic attribute is set in the packed attribute Int.
     * @param v The packed attribute Int
     * @return True if italic is set, false otherwise
     */
    fun isItalic(v: Int) = v and (1 shl 11) != 0

    /**
     * Checks if the underline attribute is set in the packed attribute Int.
     * @param v The packed attribute Int
     * @return True if underline is set, false otherwise
     */
    fun isUnderline(v: Int) = v and (1 shl 12) != 0
}