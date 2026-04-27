package com.gagik.core.codec

import com.gagik.core.model.AttributeColor
import com.gagik.core.model.AttributeColorKind
import com.gagik.core.model.Attributes

/**
 * Encodes terminal cell attributes into a compact Long.
 *
 * Color range:
 * - `0` = default terminal color
 * - `1..256` = indexed palette colors, stored as palette indexes `0..255`
 * - RGB colors store the full `0xRRGGBB` value in the color slot
 *
 * Layout:
 * - bits `0..25` foreground tagged color
 * - bits `26..51` background tagged color
 * - bit `52` selective-erase protection (DECSCA)
 * - bit `53` bold
 * - bit `54` italic
 * - bit `55` underline
 * - bit `56` inverse/reverse-video
 */
internal object AttributeCodec {

    /** Maximum color value for standard ANSI colors (0 = default, 1-16 = ANSI). */
    const val MAX_ANSI_COLOR = 16

    /** Maximum indexed color code value (0 = default, 1-256 = indexed palette colors). */
    const val MAX_COLOR = 256

    /** Maximum xterm-style indexed palette value. */
    const val MAX_INDEXED_COLOR = 255

    private const val COLOR_VALUE_MASK = 0xFF_FF_FF
    private const val COLOR_KIND_SHIFT = 24
    private const val COLOR_KIND_DEFAULT = 0
    private const val COLOR_KIND_INDEXED = 1
    private const val COLOR_KIND_RGB = 2

    private const val BG_SHIFT = 26

    private const val PROTECTED_BIT = 52
    private const val BOLD_BIT = 53
    private const val ITALIC_BIT = 54
    private const val UNDERLINE_BIT = 55
    private const val INVERSE_BIT = 56
    private const val COLOR_SLOT_MASK = (1L shl 26) - 1L

    /**
     * Packs indexed color codes into a single Long.
     *
     * @throws IllegalArgumentException if `fg` or `bg` are out of range
     */
    fun pack(
        fg: Int,
        bg: Int,
        bold: Boolean,
        italic: Boolean,
        underline: Boolean,
        inverse: Boolean = false,
        protected: Boolean = false
    ): Long {
        require(fg in 0..MAX_COLOR) { "fg must be in 0..$MAX_COLOR, was $fg" }
        require(bg in 0..MAX_COLOR) { "bg must be in 0..$MAX_COLOR, was $bg" }

        var v = encodeColorCode(fg).toLong()
        v = v or (encodeColorCode(bg).toLong() shl BG_SHIFT)
        if (protected) v = v or (1L shl PROTECTED_BIT)
        if (bold) v = v or (1L shl BOLD_BIT)
        if (italic) v = v or (1L shl ITALIC_BIT)
        if (underline) v = v or (1L shl UNDERLINE_BIT)
        if (inverse) v = v or (1L shl INVERSE_BIT)
        return v
    }

    /** Packs structured color attributes into a single Long. */
    fun packColors(
        foreground: AttributeColor,
        background: AttributeColor,
        bold: Boolean,
        italic: Boolean,
        underline: Boolean,
        inverse: Boolean = false,
        protected: Boolean = false
    ): Long {
        var v = encodeColor(foreground).toLong()
        v = v or (encodeColor(background).toLong() shl BG_SHIFT)
        if (protected) v = v or (1L shl PROTECTED_BIT)
        if (bold) v = v or (1L shl BOLD_BIT)
        if (italic) v = v or (1L shl ITALIC_BIT)
        if (underline) v = v or (1L shl UNDERLINE_BIT)
        if (inverse) v = v or (1L shl INVERSE_BIT)
        return v
    }

    /** Extracts the indexed foreground color code from the packed attribute Long. */
    fun foreground(v: Long): Int = colorCode((v and COLOR_SLOT_MASK).toInt())

    /** Extracts the indexed background color code from the packed attribute Long. */
    fun background(v: Long): Int = colorCode(((v ushr BG_SHIFT) and COLOR_SLOT_MASK).toInt())

    /** Extracts the full foreground color descriptor. */
    fun foregroundColor(v: Long): AttributeColor = decodeColor((v and COLOR_SLOT_MASK).toInt())

    /** Extracts the full background color descriptor. */
    fun backgroundColor(v: Long): AttributeColor = decodeColor(((v ushr BG_SHIFT) and COLOR_SLOT_MASK).toInt())

    /** Returns true when bold is set. */
    fun isBold(v: Long): Boolean = v and (1L shl BOLD_BIT) != 0L

    /** Returns true when italic is set. */
    fun isItalic(v: Long): Boolean = v and (1L shl ITALIC_BIT) != 0L

    /** Returns true when underline is set. */
    fun isUnderline(v: Long): Boolean = v and (1L shl UNDERLINE_BIT) != 0L

    /** Returns true when inverse/reverse-video is set. */
    fun isInverse(v: Long): Boolean = v and (1L shl INVERSE_BIT) != 0L

    /** Returns true when selective-erase protection is set. */
    fun isProtected(v: Long): Boolean = v and (1L shl PROTECTED_BIT) != 0L

    /** Returns [v] with only the selective-erase protection bit changed. */
    fun withProtected(v: Long, enabled: Boolean): Long {
        return if (enabled) {
            v or (1L shl PROTECTED_BIT)
        } else {
            v and (1L shl PROTECTED_BIT).inv()
        }
    }

    /** Unpacks a packed attribute Long into a structured [Attributes] instance. */
    fun unpack(v: Long): Attributes {
        val foreground = foregroundColor(v)
        val background = backgroundColor(v)
        return Attributes(
            foreground = foreground,
            background = background,
            bold = isBold(v),
            italic = isItalic(v),
            underline = isUnderline(v),
            selectiveEraseProtected = isProtected(v),
            inverse = isInverse(v)
        )
    }

    private fun encodeColorCode(code: Int): Int {
        return if (code == 0) {
            COLOR_KIND_DEFAULT shl COLOR_KIND_SHIFT
        } else {
            (COLOR_KIND_INDEXED shl COLOR_KIND_SHIFT) or (code - 1)
        }
    }

    private fun encodeColor(color: AttributeColor): Int {
        val kind = when (color.kind) {
            AttributeColorKind.DEFAULT -> COLOR_KIND_DEFAULT
            AttributeColorKind.INDEXED -> COLOR_KIND_INDEXED
            AttributeColorKind.RGB -> COLOR_KIND_RGB
        }
        return (kind shl COLOR_KIND_SHIFT) or color.value
    }

    private fun decodeColor(encoded: Int): AttributeColor {
        val kind = (encoded ushr COLOR_KIND_SHIFT) and 0b11
        val value = encoded and COLOR_VALUE_MASK
        return when (kind) {
            COLOR_KIND_INDEXED -> AttributeColor.indexed(value and 0xFF)
            COLOR_KIND_RGB -> AttributeColor.rgb(value)
            else -> AttributeColor.DEFAULT
        }
    }

    private fun colorCode(encoded: Int): Int {
        val kind = (encoded ushr COLOR_KIND_SHIFT) and 0b11
        val value = encoded and COLOR_VALUE_MASK
        return if (kind == COLOR_KIND_INDEXED) (value and 0xFF) + 1 else 0
    }
}
