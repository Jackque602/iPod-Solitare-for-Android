package com.jackque.solitaire

import com.jackque.solitaire.engine.DrawMode
import com.jackque.solitaire.engine.KlondikeGame
import com.jackque.solitaire.engine.Move
import com.jackque.solitaire.engine.SavedGame
import com.jackque.solitaire.engine.stats.Statistics
import com.jackque.solitaire.widget.WidgetTexts
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTextsTest {

    @Test
    fun `without a saved game the widget offers a new deal`() {
        val content = WidgetTexts.from(Statistics(bank = -52, wins = 1, losses = 2), null)
        assertEquals("-$52", content.bankLine)
        assertEquals("Ready for a new deal", content.dealLine)
        assertEquals("Wins 1 · Losses 2", content.recordLine)
        assertEquals("Grand Tour 0/24", content.tourLine)
    }

    @Test
    fun `a saved game shows its moves and foundation progress`() {
        val game = KlondikeGame.newDeal(42L, DrawMode.DRAW_ONE)
        checkNotNull(game.tryMove(Move.Draw))
        val content = WidgetTexts.from(Statistics(bank = 208), game.toSavedGame(elapsedSeconds = 5))
        assertEquals("$208", content.bankLine)
        assertEquals("Deal in progress · 1 moves · 0/52 up", content.dealLine)
    }

    @Test
    fun `a corrupt saved game falls back to the ready line`() {
        val bad = SavedGame(1L, DrawMode.DRAW_ONE, listOf(Move.WasteToFoundation(0)))
        assertEquals("Ready for a new deal", WidgetTexts.from(Statistics(), bad).dealLine)
    }
}
