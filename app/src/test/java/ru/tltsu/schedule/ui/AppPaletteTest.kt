package ru.tltsu.schedule.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class AppPaletteTest {
    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val x = ((color ushr shift) and 255) / 255.0
            return if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        }
        return channel(16) * 0.2126 + channel(8) * 0.7152 + channel(0) * 0.0722
    }
    private fun contrast(a: Int, b: Int): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
    @Test fun readableText() {
        for (p in AppPalette.legacyValues()) {
            for (background in listOf(p.bg, p.surface, p.highlight)) {
                for (foreground in listOf(p.text, p.muted, p.accent, p.lab, p.practice)) {
                    assertTrue(p.title, contrast(foreground, background) >= 4.5)
                }
            }
            assertTrue(p.title, contrast(p.onSelected, p.selected) >= 4.5)
            assertTrue(p.minContrast() >= 4.5)
            org.junit.Assert.assertEquals(p, AppPalette.decode(p.encode()))
        }
    }
    @Test fun opacityBlendsAgainstBackground() {
        org.junit.Assert.assertEquals(AppPalette.BLACK, AppPalette.blend(AppPalette.WHITE, AppPalette.BLACK, 0))
        org.junit.Assert.assertEquals(AppPalette.WHITE, AppPalette.blend(AppPalette.WHITE, AppPalette.BLACK, 100))
        org.junit.Assert.assertEquals(0xFF808080.toInt(), AppPalette.blend(AppPalette.WHITE, AppPalette.BLACK, 50))
    }
    @Test fun unreadablePaletteRejected() {
        val p = AppPalette.defaults().copy(text = AppPalette.defaults().bg)
        assertTrue(p.minContrast() < 4.5)
    }
}
