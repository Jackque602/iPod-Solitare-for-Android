package com.jackque.solitaire.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AutoCompleteWinTest {

    @Test
    fun `auto-complete requires empty stock and waste and no hidden cards`() {
        assertTrue(Klondike.isAutoCompleteAvailable(allFaceUpFullDeckState()))

        val withStock = allFaceUpFullDeckState().copy(stock = pile("2s"))
        assertFalse(Klondike.isAutoCompleteAvailable(withStock))

        val withWaste = allFaceUpFullDeckState().copy(waste = pile("2S"))
        assertFalse(Klondike.isAutoCompleteAvailable(withWaste))

        val hidden = allFaceUpFullDeckState().let { s ->
            s.copy(tableau = s.tableau.mapIndexed { i, cards ->
                if (i == 0) listOf(cards.first().copy(faceUp = false)) + cards.drop(1) else cards
            })
        }
        assertFalse(Klondike.isAutoCompleteAvailable(hidden))
    }

    @Test
    fun `auto-complete plays the whole game and a completed game nets +208`() {
        var current = allFaceUpFullDeckState()
        var earned = 0
        var steps = 0
        while (!current.isWon) {
            val move = assertNotNull(Klondike.nextAutoCompleteMove(current), "auto-complete must always find a move")
            val result = assertNotNull(Klondike.apply(current, move))
            earned += result.scoreDelta
            current = result.state
            steps++
            assertTrue(steps <= 52, "auto-complete must finish in 52 moves")
        }
        assertEquals(52, steps)
        assertEquals(52 * Scoring.CARD_TO_FOUNDATION, earned)
        assertEquals(Scoring.WIN_NET, Scoring.NEW_DEAL + earned, "a completed game always nets +208")
        assertTrue(current.isWon)
        assertTrue(current.foundations.all { it.size == 13 })
    }

    @Test
    fun `auto-complete plays low cards before high ones`() {
        val move = assertNotNull(Klondike.nextAutoCompleteMove(allFaceUpFullDeckState()))
        val state = allFaceUpFullDeckState()
        val card = state.tableau[(move as Move.TableauToFoundation).column].last()
        assertEquals(Rank.ACE, card.rank)
    }

    @Test
    fun `finished game offers no auto-complete`() {
        var current = allFaceUpFullDeckState()
        while (!current.isWon) {
            current = Klondike.apply(current, Klondike.nextAutoCompleteMove(current)!!)!!.state
        }
        assertFalse(Klondike.isAutoCompleteAvailable(current))
        assertEquals(null, Klondike.nextAutoCompleteMove(current))
    }
}
