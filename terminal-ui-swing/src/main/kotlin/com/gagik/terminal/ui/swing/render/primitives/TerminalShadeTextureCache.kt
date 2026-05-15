package com.gagik.terminal.ui.swing.render.primitives

import com.gagik.terminal.ui.swing.render.primitives.TerminalBlockElementGlyphs.SHADE_DARK
import com.gagik.terminal.ui.swing.render.primitives.TerminalBlockElementGlyphs.SHADE_LIGHT
import com.gagik.terminal.ui.swing.render.primitives.TerminalBlockElementGlyphs.SHADE_MEDIUM
import java.awt.TexturePaint
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage

/**
 * Caches foreground-colored dither textures for shade block glyphs.
 */
internal class TerminalShadeTextureCache {
    private val keys = IntArray(CACHE_SIZE)
    private val textures = arrayOfNulls<TexturePaint>(CACHE_SIZE)

    fun texture(kind: Int, argb: Int): TexturePaint {
        val key = key(kind, argb)
        val slot = slot(key)
        val cached = textures[slot]
        if (cached != null && keys[slot] == key) return cached

        val texture = createTexture(kind, argb)
        keys[slot] = key
        textures[slot] = texture
        return texture
    }

    private fun createTexture(kind: Int, argb: Int): TexturePaint {
        val textureWidth = textureWidth(kind)
        val textureHeight = textureHeight(kind)
        val image = BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_ARGB)

        var row = 0
        while (row < textureHeight) {
            var column = 0
            while (column < textureWidth) {
                if (isPainted(kind, column, row)) {
                    image.setRGB(column, row, argb)
                }
                column++
            }
            row++
        }

        return TexturePaint(image, Rectangle2D.Float(0f, 0f, textureWidth.toFloat(), textureHeight.toFloat()))
    }

    private fun textureWidth(kind: Int): Int {
        return if (kind == SHADE_MEDIUM) 2 else 4
    }

    private fun textureHeight(kind: Int): Int {
        return if (kind == SHADE_MEDIUM) 2 else 4
    }

    private fun isPainted(kind: Int, column: Int, row: Int): Boolean {
        return when (kind) {
            SHADE_LIGHT -> column == (row and 1) * 2
            SHADE_MEDIUM -> column and 1 == row and 1
            SHADE_DARK -> (row + column) and 0x3 != 0
            else -> false
        }
    }

    private fun key(kind: Int, argb: Int): Int {
        return argb xor (kind * KIND_KEY_FACTOR)
    }

    private fun slot(key: Int): Int {
        return key * HASH_FACTOR ushr HASH_SHIFT
    }

    private companion object {
        private const val CACHE_SIZE = 64
        private const val HASH_SHIFT = 26
        private const val HASH_FACTOR = -0x61C88647
        private const val KIND_KEY_FACTOR = 0x01010101
    }
}
