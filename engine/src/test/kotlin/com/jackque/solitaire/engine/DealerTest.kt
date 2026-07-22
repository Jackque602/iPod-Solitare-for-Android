package com.jackque.solitaire.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DealerTest {

    @Test
    fun `same seed always deals the same game`() {
        for (seed in listOf(0L, 1L, 42L, -7L, Long.MAX_VALUE)) {
            assertEquals(Dealer.deal(seed, DrawMode.DRAW_ONE), Dealer.deal(seed, DrawMode.DRAW_ONE))
            assertEquals(Dealer.shuffledDeck(seed), Dealer.shuffledDeck(seed))
        }
    }

    @Test
    fun `different seeds deal different games`() {
        assertNotEquals(Dealer.deal(1L, DrawMode.DRAW_ONE), Dealer.deal(2L, DrawMode.DRAW_ONE))
    }

    @Test
    fun `deal layout follows klondike rules`() {
        val state = Dealer.deal(42L, DrawMode.DRAW_THREE)
        assertEquals(7, state.tableau.size)
        state.tableau.forEachIndexed { col, cards ->
            assertEquals(col + 1, cards.size)
            cards.dropLast(1).forEach { assertTrue(!it.faceUp, "buried cards must be face down") }
            assertTrue(cards.last().faceUp, "top of each column must be face up")
        }
        assertEquals(24, state.stock.size)
        assertTrue(state.stock.all { !it.faceUp })
        assertTrue(state.waste.isEmpty())
        assertTrue(state.foundations.all { it.isEmpty() })
        assertEquals(0, state.moveCount)
        assertEquals(DrawMode.DRAW_THREE, state.drawMode)
    }

    @Test
    fun `every dealt game contains all 52 cards exactly once`() {
        val state = Dealer.deal(99L, DrawMode.DRAW_ONE)
        val all = state.stock + state.waste + state.foundations.flatten() + state.tableau.flatten()
        assertEquals(52, all.size)
        assertEquals(52, all.map { it.rank to it.suit }.toSet().size)
    }

    @Test
    fun `seeded rng is stable across instances`() {
        val a = SeededRng(123)
        val b = SeededRng(123)
        repeat(100) { assertEquals(a.nextInt(52), b.nextInt(52)) }
    }
}
