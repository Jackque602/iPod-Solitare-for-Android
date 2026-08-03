package com.jackque.solitaire

import com.jackque.solitaire.engine.DrawMode
import com.jackque.solitaire.engine.SolitaireSession
import com.jackque.solitaire.engine.input.Selection
import com.jackque.solitaire.engine.input.Zone
import com.jackque.solitaire.engine.stats.Statistics
import com.jackque.solitaire.widget.WidgetPlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Uses the seed-1 deal pinned by the engine's GoldenDealTest:
 * column 1 is [9s AD] (ace of diamonds face up on top) and column 0 is
 * a lone face-up ten of diamonds.
 */
class WidgetPlayTest {

    private val now = 1_700_000_000_000L

    private fun session() = SolitaireSession.start(Statistics(), null, 1L, DrawMode.DRAW_ONE, now)

    @Test
    fun `token parsing accepts every board zone and rejects junk`() {
        assertEquals(Zone.STOCK, WidgetPlay.cursorFor("stock")!!.zone)
        assertEquals(Zone.WASTE, WidgetPlay.cursorFor("waste")!!.zone)
        assertEquals(3, WidgetPlay.cursorFor("f3")!!.index)
        assertEquals(6, WidgetPlay.cursorFor("t6")!!.index)
        assertNull(WidgetPlay.cursorFor("f4"))
        assertNull(WidgetPlay.cursorFor("t7"))
        assertNull(WidgetPlay.cursorFor("bogus"))
        assertEquals(13, WidgetPlay.ZONE_TOKENS.size)
    }

    @Test
    fun `tapping the stock draws a card`() {
        val s = session()
        val selection = WidgetPlay.tap(s, null, "stock", now)
        assertNull(selection)
        assertEquals(1, s.state.moveCount)
    }

    @Test
    fun `select a tableau card then drop it on a foundation`() {
        val s = session()
        val picked = WidgetPlay.tap(s, null, "t1", now)
        assertEquals(Selection(Zone.TABLEAU, 1, 1), picked)

        val after = WidgetPlay.tap(s, picked, "f0", now)
        assertNull(after)
        assertEquals(1, s.state.foundationCardCount)
        assertEquals("-52 buy-in + 5 for the ace", -47L, s.bank)
    }

    @Test
    fun `tapping the selected single-card column clears the selection`() {
        val s = session()
        val picked = WidgetPlay.tap(s, null, "t0", now)
        assertEquals(Selection(Zone.TABLEAU, 0, 0), picked)
        assertNull(WidgetPlay.tap(s, picked, "t0", now))
        assertEquals(0, s.state.moveCount)
    }

    @Test
    fun `selection encoding roundtrips and rejects junk`() {
        val selection = Selection(Zone.TABLEAU, 4, 2)
        assertEquals(selection, WidgetPlay.decodeSelection(WidgetPlay.encodeSelection(selection)))
        assertNull(WidgetPlay.decodeSelection(""))
        assertNull(WidgetPlay.decodeSelection("TABLEAU:x:2"))
        assertNull(WidgetPlay.decodeSelection("SOFA:1:2"))
    }

    @Test
    fun `stale selections are dropped after the position changed`() {
        val s = session()
        WidgetPlay.tap(s, WidgetPlay.tap(s, null, "t1", now), "f0", now)
        // Column 1 now only holds the flipped former face-down card.
        val stale = Selection(Zone.TABLEAU, 1, 1)
        assertNull(WidgetPlay.sanitize(s.state, stale))
        assertNull(WidgetPlay.sanitize(s.state, Selection(Zone.WASTE, 0, 0)))
        assertNull(WidgetPlay.sanitize(s.state, Selection(Zone.FOUNDATION, 1, 0)))
        assertEquals(
            Selection(Zone.FOUNDATION, 0, 0),
            WidgetPlay.sanitize(s.state, Selection(Zone.FOUNDATION, 0, 0)),
        )
    }
}
