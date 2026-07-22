package com.jackque.solitaire.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoringAndUndoTest {

    @Test
    fun `scoring constants match the original iPod rules`() {
        assertEquals(-52, Scoring.NEW_DEAL)
        assertEquals(5, Scoring.CARD_TO_FOUNDATION)
        assertEquals(-5, Scoring.CARD_OFF_FOUNDATION)
        assertEquals(208, Scoring.WIN_NET)
    }

    @Test
    fun `foundation moves are worth plus five`() {
        val s = state(waste = pile("AH"))
        val result = assertNotNull(Klondike.apply(s, Move.WasteToFoundation(0)))
        assertEquals(5, result.scoreDelta)

        val t = state(tableau = List(7) { if (it == 0) pile("AS") else emptyList() })
        assertEquals(5, Klondike.apply(t, Move.TableauToFoundation(0, 0))!!.scoreDelta)
    }

    @Test
    fun `non-foundation moves are worth nothing`() {
        val s = state(stock = pile("2s"), drawMode = DrawMode.DRAW_ONE)
        assertEquals(0, Klondike.apply(s, Move.Draw)!!.scoreDelta)
    }

    @Test
    fun `game score is buy-in plus foundation earnings`() {
        val s = state(foundations = listOf(pile("AS 2S"), pile("AH"), emptyList(), emptyList()))
        assertEquals(-52 + 3 * 5, Scoring.gameScore(s))
        assertEquals(-52, Scoring.gameScore(state()))
    }

    @Test
    fun `undo restores state and score exactly`() {
        val game = KlondikeGame.newDeal(7L, DrawMode.DRAW_ONE)
        val initial = game.state

        val result = assertNotNull(game.tryMove(Move.Draw))
        assertEquals(1, result.state.moveCount)
        assertEquals(-0, assertNotNull(game.undo()))
        assertEquals(initial, game.state)
        assertNull(game.undo(), "nothing left to undo")
    }

    @Test
    fun `undo of a foundation move refunds the five dollars`() {
        val game = KlondikeGame.newDeal(7L, DrawMode.DRAW_ONE)
        // Manufacture a state-independent check via a crafted game instead:
        // play until some foundation move becomes available.
        var bank = 0
        var foundationMove = false
        val rng = Random(1)
        repeat(500) {
            if (foundationMove) return@repeat
            val moves = Klondike.legalMoves(game.state)
            if (moves.isEmpty()) return@repeat
            val preferred = moves.firstOrNull { it is Move.WasteToFoundation || it is Move.TableauToFoundation }
            val move = preferred ?: moves[rng.nextInt(moves.size)]
            val result = game.tryMove(move)!!
            bank += result.scoreDelta
            if (result.scoreDelta == 5) {
                val refund = game.undo()!!
                assertEquals(-5, refund, "undoing a foundation move must refund exactly $5")
                bank += refund
                foundationMove = true
            }
        }
        assertTrue(foundationMove, "expected to reach at least one foundation move")
    }

    @Test
    fun `a long random game undone to the start restores everything`() {
        val game = KlondikeGame.newDeal(42L, DrawMode.DRAW_THREE)
        val initial = game.state
        val rng = Random(1234)
        var bankDelta = 0
        var played = 0
        repeat(120) {
            val moves = Klondike.legalMoves(game.state)
            if (moves.isEmpty()) return@repeat
            val result = game.tryMove(moves[rng.nextInt(moves.size)])
            if (result != null) {
                bankDelta += result.scoreDelta
                played++
            }
        }
        assertTrue(played > 0)
        while (game.canUndo) {
            bankDelta += game.undo()!!
        }
        assertEquals(initial, game.state, "undoing everything must restore the deal exactly")
        assertEquals(0, bankDelta, "undo must refund every score change exactly")
        assertEquals(0, game.state.moveCount)
        assertTrue(game.moves.isEmpty())
    }

    @Test
    fun `restart deal reproduces the identical deal for the same seed`() {
        val first = KlondikeGame.newDeal(555L, DrawMode.DRAW_ONE).state
        val second = KlondikeGame.newDeal(555L, DrawMode.DRAW_ONE).state
        assertEquals(first, second)
    }
}
