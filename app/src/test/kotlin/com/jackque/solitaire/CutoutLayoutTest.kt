package com.jackque.solitaire

import com.jackque.solitaire.ui.CutoutLayout
import com.jackque.solitaire.ui.TopCutout
import org.junit.Assert.assertEquals
import org.junit.Test

class CutoutLayoutTest {

    @Test
    fun `no cutout means no padding and no spacer`() {
        assertEquals(0f to 0f, CutoutLayout.barPadding(null, 480f))
        assertEquals(0f, CutoutLayout.spacerBelowBar(null, 46f))
    }

    @Test
    fun `right-side cameras reserve the end of the bar`() {
        // Razr-style: cameras occupying the right corner of the top strip.
        val cutout = TopCutout(leftDp = 360f, rightDp = 480f, heightDp = 56f)
        val (start, end) = CutoutLayout.barPadding(cutout, screenWidthDp = 480f)
        assertEquals(0f, start)
        assertEquals(120f, end)
    }

    @Test
    fun `left-side cameras reserve the start of the bar`() {
        val cutout = TopCutout(leftDp = 0f, rightDp = 110f, heightDp = 50f)
        val (start, end) = CutoutLayout.barPadding(cutout, screenWidthDp = 480f)
        assertEquals(110f, start)
        assertEquals(0f, end)
    }

    @Test
    fun `the board drops below whatever the bar does not cover`() {
        val cutout = TopCutout(leftDp = 360f, rightDp = 480f, heightDp = 56f)
        assertEquals(10f, CutoutLayout.spacerBelowBar(cutout, barHeightDp = 46f))
        // A bar taller than the cutout needs no extra space.
        assertEquals(0f, CutoutLayout.spacerBelowBar(cutout, barHeightDp = 60f))
    }

    @Test
    fun `degenerate cutouts are ignored`() {
        assertEquals(0f to 0f, CutoutLayout.barPadding(TopCutout(500f, 520f, 40f), 480f))
        assertEquals(0f to 0f, CutoutLayout.barPadding(TopCutout(-40f, 0f, 40f), 480f))
    }
}
