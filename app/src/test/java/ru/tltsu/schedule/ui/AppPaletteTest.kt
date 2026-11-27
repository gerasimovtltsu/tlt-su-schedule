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
        for (p in AppPalette.values()) {
            for (background in listOf(p.bg, p.surface, p.highlight)) {
                for (foreground in listOf(p.text, p.muted, p.accent, p.lab, p.practice)) {
                    assertTrue(p.title, contrast(foreground, background) >= 4.5)
                }
            }
            assertTrue(p.title, contrast(p.onSelected, p.selected) >= 4.5)
        }
    }
}
