package com.jackque.solitaire.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedGameTest {

    private fun playedGame(seed: Long = 42L, moves: Int = 60): KlondikeGame {
        val game = KlondikeGame.newDeal(seed, DrawMode.DRAW_THREE)
        val rng = Random(seed)
        repeat(moves) {
            val legal = Klondike.legalMoves(game.state)
            if (legal.isNotEmpty()) game.tryMove(legal[rng.nextInt(legal.size)])
        }
        return game
    }

    @Test
    fun `save and restore reproduce the exact position`() {
        val game = playedGame()
        val saved = game.toSavedGame(elapsedSeconds = 321)
        val restored = assertNotNull(KlondikeGame.restore(saved))
        assertEquals(game.state, restored.state)
        assertEquals(game.moves, restored.moves)
        assertEquals(321, saved.elapsedSeconds)
    }

    @Test
    fun `restored games keep their undo history`() {
        val game = playedGame()
        val restored = assertNotNull(KlondikeGame.restore(game.toSavedGame(0)))
        var undone = 0
        while (restored.canUndo) {
            restored.undo()
            undone++
        }
        assertEquals(game.moves.size, undone, "every replayed move must be undoable")
        assertEquals(Dealer.deal(42L, DrawMode.DRAW_THREE), restored.state)
    }

    @Test
    fun `codec roundtrips through json`() {
        val saved = playedGame().toSavedGame(elapsedSeconds = 77)
        val decoded = assertNotNull(SavedGameCodec.decode(SavedGameCodec.encode(saved)))
        assertEquals(saved, decoded)
    }

    @Test
    fun `corrupt saves decode to null instead of crashing`() {
        assertNull(SavedGameCodec.decode(""))
        assertNull(SavedGameCodec.decode("not json"))
        assertNull(SavedGameCodec.decode("""{"seed": "oops"}"""))
        assertNull(SavedGameCodec.decode("""{"seed":1,"drawMode":"DRAW_ONE","moves":[{"t":"nope"}]}"""))
    }

    @Test
    fun `a save with illegal moves fails restore cleanly`() {
        val saved = SavedGame(
            seed = 1L,
            drawMode = DrawMode.DRAW_ONE,
            moves = listOf(Move.WasteToFoundation(0)),
        )
        assertNull(KlondikeGame.restore(saved))
    }

    @Test
    fun `restore is deterministic across many random games`() {
        for (seed in 1L..10L) {
            val game = playedGame(seed = seed, moves = 40)
            val restored = assertNotNull(KlondikeGame.restore(game.toSavedGame(0)))
            assertEquals(game.state, restored.state, "seed $seed replay diverged")
        }
    }

    @Test
    fun `saves are compact`() {
        val text = SavedGameCodec.encode(playedGame(moves = 100).toSavedGame(0))
        assertTrue(text.length < 8_000, "move-log saves should stay small, was ${text.length}")
    }
}
