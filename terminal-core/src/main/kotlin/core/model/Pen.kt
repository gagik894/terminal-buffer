package com.gagik.core.model

import com.gagik.core.codec.AttributeCodec

/**
 * Manages the current writing attributes.
 */
internal class Pen {

    var currentAttr: Long = AttributeCodec.DEFAULT_ATTR
        private set

    var currentExtendedAttr: Long = AttributeCodec.DEFAULT_EXTENDED_ATTR
        private set

    val blankAttr: Long
        get() = AttributeCodec.withProtected(currentAttr, enabled = false)

    val blankExtendedAttr: Long
        get() = currentExtendedAttr

    val isSelectiveEraseProtected: Boolean
        get() = AttributeCodec.isProtected(currentAttr)

    fun setAttributes(
        fg: Int,
        bg: Int,
        bold: Boolean = false,
        faint: Boolean = false,
        italic: Boolean = false,
        underlineStyle: UnderlineStyle = UnderlineStyle.NONE,
        strikethrough: Boolean = false,
        overline: Boolean = false,
        blink: Boolean = false,
        inverse: Boolean = false,
        conceal: Boolean = false,
        underlineColor: Int = 0
    ) {
        val protected = AttributeCodec.isProtected(currentAttr)
        val hyperlinkId = AttributeCodec.hyperlinkId(currentExtendedAttr)
        currentAttr = AttributeCodec.pack(
            fg = fg.coerceIn(0, AttributeCodec.MAX_COLOR),
            bg = bg.coerceIn(0, AttributeCodec.MAX_COLOR),
            bold = bold,
            faint = faint,
            italic = italic,
            blink = blink,
            inverse = inverse,
            protected = protected,
        )
        currentExtendedAttr = AttributeCodec.packExtended(
            underlineColor = underlineColor.coerceIn(0, AttributeCodec.MAX_COLOR),
            underlineStyle = underlineStyle,
            strikethrough = strikethrough,
            overline = overline,
            conceal = conceal,
            hyperlinkId = hyperlinkId,
        )
    }

    fun setColors(
        foreground: AttributeColor,
        background: AttributeColor,
        underlineColor: AttributeColor = AttributeColor.DEFAULT,
        bold: Boolean = false,
        faint: Boolean = false,
        italic: Boolean = false,
        underlineStyle: UnderlineStyle = UnderlineStyle.NONE,
        strikethrough: Boolean = false,
        overline: Boolean = false,
        blink: Boolean = false,
        inverse: Boolean = false,
        conceal: Boolean = false
    ) {
        val protected = AttributeCodec.isProtected(currentAttr)
        val hyperlinkId = AttributeCodec.hyperlinkId(currentExtendedAttr)
        currentAttr = AttributeCodec.packColors(
            foreground = foreground,
            background = background,
            bold = bold,
            faint = faint,
            italic = italic,
            blink = blink,
            inverse = inverse,
            protected = protected,
        )
        currentExtendedAttr = AttributeCodec.packExtendedColors(
            underlineColor = underlineColor,
            underlineStyle = underlineStyle,
            strikethrough = strikethrough,
            overline = overline,
            conceal = conceal,
            hyperlinkId = hyperlinkId,
        )
    }

    fun setSelectiveEraseProtection(enabled: Boolean) {
        currentAttr = AttributeCodec.withProtected(currentAttr, enabled)
    }

    fun setHyperlinkId(hyperlinkId: Int) {
        currentExtendedAttr = AttributeCodec.withHyperlinkId(currentExtendedAttr, hyperlinkId)
    }

    fun restoreAttr(packedAttr: Long, packedExtendedAttr: Long) {
        currentAttr = packedAttr
        currentExtendedAttr = packedExtendedAttr
    }

    fun resetSgr() {
        currentAttr = AttributeCodec.sgrResetPrimary(currentAttr)
        currentExtendedAttr = AttributeCodec.sgrResetExtended(currentExtendedAttr)
    }

    fun reset() {
        currentAttr = AttributeCodec.DEFAULT_ATTR
        currentExtendedAttr = AttributeCodec.DEFAULT_EXTENDED_ATTR
    }
}
