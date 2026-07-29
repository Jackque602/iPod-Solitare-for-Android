package com.jackque.solitaire

import com.jackque.solitaire.ui.LayoutMode
import com.jackque.solitaire.ui.layoutModeFor
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutModeTest {

    @Test
    fun `portrait phones use the tall layout`() {
        // Minimal Phone MP01 (~400x533dp) and a typical slab phone.
        assertEquals(LayoutMode.TALL, layoutModeFor(400f, 533f))
        assertEquals(LayoutMode.TALL, layoutModeFor(411f, 891f))
    }

    @Test
    fun `razr cover display uses the compact layout`() {
        // Razr+ 2024 cover: 1272x1080 px at ~2.6x density -> ~484x411dp.
        assertEquals(LayoutMode.COMPACT, layoutModeFor(484f, 411f))
        // Razr 2023 cover is nearly exactly square.
        assertEquals(LayoutMode.COMPACT, layoutModeFor(406f, 402f))
    }

    @Test
    fun `landscape phones use the compact layout`() {
        assertEquals(LayoutMode.COMPACT, layoutModeFor(891f, 411f))
    }

    @Test
    fun `squares lean compact until clearly taller than wide`() {
        assertEquals(LayoutMode.COMPACT, layoutModeFor(500f, 500f))
        assertEquals(LayoutMode.COMPACT, layoutModeFor(500f, 570f))
        assertEquals(LayoutMode.TALL, layoutModeFor(500f, 575f))
    }
}
