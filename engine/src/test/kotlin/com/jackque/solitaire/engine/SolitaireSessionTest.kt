package com.jackque.solitaire.engine

import com.jackque.solitaire.engine.stats.Statistics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SolitaireSessionTest {

    private val now = 1_700_000_000_000L

    private fun freshSession(seed: Long = 1L) =
        SolitaireSession.start(
            stats = Statistics(),
            savedGame = null,
            newSeed = seed,
            drawMode = DrawMode.DRAW_ONE,
            timestampMillis = now,
        )

    @Test
    fun `starting a session with no save charges the buy-in`() {
        val session = freshSession()
        assertEquals(-52, session.bank)
        assertEquals(1, session.stats.gamesStarted)
    }

    @Test
    fun `restoring a save does not charge another buy-in`() {
        val original = freshSession()
        original.tryMove(Move.Draw, now)
        original.tick()
        val statsAtSave = original.stats
        val saved = original.toSavedGame()

        val restored = SolitaireSession.start(
            stats = statsAtSave,
            savedGame = saved,
            newSeed = 999L,
            drawMode = DrawMode.DRAW_ONE,
            timestampMillis = now + 10_000,
        )
        assertEquals(-52, restored.bank, "restore must not deduct another \$52")
        assertEquals(1, restored.stats.gamesStarted)
        assertEquals(original.state, restored.state)
        assertEquals(1, restored.elapsedSeconds)
        assertTrue(restored.canUndo, "undo history survives restore")
    }

    @Test
    fun `a corrupt save falls back to a fresh deal with buy-in`() {
        val bad = SavedGame(seed = 1L, drawMode = DrawMode.DRAW_ONE, moves = listOf(Move.WasteToFoundation(0)))
        val session = SolitaireSession.start(Statistics(), bad, 5L, DrawMode.DRAW_THREE, now)
        assertEquals(-52, session.bank)
        assertEquals(5L, session.state.seed)
    }

    @Test
    fun `new deal mid-game records a loss and charges again`() {
        val session = freshSession()
        session.tryMove(Move.Draw, now)
        session.newDeal(2L, DrawMode.DRAW_ONE, now)
        assertEquals(-104, session.bank)
        assertEquals(1, session.stats.losses)
        assertEquals(2, session.stats.gamesStarted)
        assertEquals(2L, session.state.seed)
    }

    @Test
    fun `abandoning without a single move is not a loss but still costs`() {
        val session = freshSession()
        session.newDeal(2L, DrawMode.DRAW_ONE, now)
        assertEquals(-104, session.bank)
        assertEquals(0, session.stats.losses)
    }

    @Test
    fun `restart deal reuses the seed and re-charges`() {
        val session = freshSession(seed = 7L)
        val initial = session.state
        session.tryMove(Move.Draw, now)
        session.restartDeal(now)
        assertEquals(initial, session.state, "restart must reproduce the identical deal")
        assertEquals(-104, session.bank, "restarting counts as a new deal")
        assertEquals(1, session.stats.losses)
    }

    @Test
    fun `undo refunds the score exactly`() {
        val session = freshSession()
        // Find and play a foundation move via hints/legal moves.
        var attempts = 0
        while (session.stats.totalFoundationCards == 0L && attempts < 400) {
            val foundationMove = Klondike.legalMoves(session.state)
                .firstOrNull { it is Move.WasteToFoundation || it is Move.TableauToFoundation }
            session.tryMove(foundationMove ?: Move.Draw, now)
                ?: session.tryMove(Move.Recycle, now)
            attempts++
        }
        assertTrue(session.stats.totalFoundationCards > 0, "never found a foundation move")
        val bankAfterFoundation = session.bank
        assertTrue(session.undo())
        assertEquals(bankAfterFoundation - 5, session.bank)
        assertEquals(0, session.stats.totalFoundationCards)
    }

    @Test
    fun `winning from an all-face-up position nets exactly +208 and is recorded once`() {
        val session = SolitaireSession.start(Statistics(), null, 1L, DrawMode.DRAW_ONE, now)
        forceState(session, allFaceUpFullDeckState())
        assertTrue(session.isAutoCompleteAvailable)

        val played = session.autoCompleteAll(now)
        assertEquals(52, played)
        assertTrue(session.isWon)
        assertEquals(Scoring.WIN_NET.toLong(), session.bank, "-52 buy-in + 52*\$5 = +\$208")
        assertEquals(1, session.stats.wins)
        assertEquals(1, session.stats.currentWinStreak)
        assertEquals(1, session.stats.history.size)
        assertEquals(Scoring.WIN_NET, session.stats.history.first().gameScore)

        // Starting the next deal must NOT record a loss for the won game.
        session.newDeal(2L, DrawMode.DRAW_ONE, now)
        assertEquals(0, session.stats.losses)
        assertEquals(1, session.stats.wins)
    }

    @Test
    fun `moves after the win are rejected`() {
        val session = freshSession()
        forceState(session, allFaceUpFullDeckState())
        session.autoCompleteAll(now)
        assertNull(session.tryMove(Move.Draw, now))
        assertFalse(session.undo(), "a settled win cannot be undone away")
    }

    @Test
    fun `hints cycle through the productive moves`() {
        val session = freshSession()
        val first = session.nextHint()
        assertNotNull(first, "a fresh deal always has at least a draw hint")
        val seen = mutableSetOf(first)
        repeat(10) { session.nextHint()?.let { seen.add(it) } }
        // Cycling never returns an illegal move.
        seen.forEach { assertTrue(Klondike.isLegal(session.state, it)) }
    }

    @Test
    fun `tick counts seconds only while playing`() {
        val session = freshSession()
        session.tick()
        session.tick()
        assertEquals(2, session.elapsedSeconds)
        forceState(session, allFaceUpFullDeckState())
        session.autoCompleteAll(now)
        val atWin = session.elapsedSeconds
        session.tick()
        assertEquals(atWin, session.elapsedSeconds, "timer freezes after the win")
    }

    @Test
    fun `statistics import replaces bank and history`() {
        val session = freshSession()
        val imported = Statistics(bank = 416, wins = 2, highestBank = 500)
        session.importStatistics(imported)
        assertEquals(416, session.bank)
        assertEquals(2, session.stats.wins)
    }

    /**
     * Test hook: replay-free way to put a session into a crafted position.
     * Uses newDeal bookkeeping so the buy-in stays consistent, then swaps
     * the underlying game state via reflection-free reconstruction.
     */
    private fun forceState(session: SolitaireSession, target: GameState) {
        // Rebuild the crafted position by applying it as if dealt: the
        // session API is intentionally closed, so tests reach the position
        // through the game's own move replay when possible. For the crafted
        // all-face-up state no legal move sequence exists from a real deal,
        // so tests use the internal test seam below.
        session.forceStateForTest(target)
    }
}
