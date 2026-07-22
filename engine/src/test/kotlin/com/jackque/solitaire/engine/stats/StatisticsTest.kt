package com.jackque.solitaire.engine.stats

import com.jackque.solitaire.engine.DrawMode
import com.jackque.solitaire.engine.Scoring
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StatisticsTest {

    private fun completed(
        won: Boolean,
        gameScore: Int = if (won) Scoring.WIN_NET else -52,
        moves: Int = 100,
        seconds: Long = 300,
        bankAfter: Long = 0,
    ) = CompletedGame(
        timestampMillis = 1_700_000_000_000,
        seed = 1L,
        drawMode = DrawMode.DRAW_ONE,
        won = won,
        moves = moves,
        durationSeconds = seconds,
        gameScore = gameScore,
        bankAfter = bankAfter,
    )

    @Test
    fun `bank tracks deltas with high and low water marks`() {
        var stats = Statistics()
        stats = stats.dealStarted().applyScoreDelta(Scoring.NEW_DEAL)
        assertEquals(-52, stats.bank)
        assertEquals(-52, stats.lowestBank)
        assertEquals(0, stats.highestBank)
        assertEquals(1, stats.gamesStarted)

        repeat(52) { stats = stats.applyScoreDelta(Scoring.CARD_TO_FOUNDATION, foundationCardsDelta = 1) }
        assertEquals(Scoring.WIN_NET.toLong(), stats.bank)
        assertEquals(208, stats.highestBank)
        assertEquals(-52, stats.lowestBank)
        assertEquals(52, stats.totalFoundationCards)
    }

    @Test
    fun `undoing a foundation move takes the card count back`() {
        var stats = Statistics().applyScoreDelta(5, foundationCardsDelta = 1)
        stats = stats.applyScoreDelta(-5, foundationCardsDelta = -1)
        assertEquals(0, stats.totalFoundationCards)
        assertEquals(0, stats.bank)
        assertEquals(5, stats.highestBank, "watermarks remember momentary peaks")
    }

    @Test
    fun `bank may go and stay negative`() {
        var stats = Statistics()
        repeat(3) { stats = stats.applyScoreDelta(Scoring.NEW_DEAL) }
        assertEquals(-156, stats.bank)
        assertEquals(-156, stats.lowestBank)
    }

    @Test
    fun `wins and losses drive the streak counters`() {
        var stats = Statistics()
        stats = stats.gameCompleted(completed(won = true))
        stats = stats.gameCompleted(completed(won = true))
        assertEquals(2, stats.wins)
        assertEquals(2, stats.currentWinStreak)
        assertEquals(2, stats.bestWinStreak)
        assertEquals(0, stats.currentLossStreak)

        stats = stats.gameCompleted(completed(won = false))
        assertEquals(1, stats.losses)
        assertEquals(0, stats.currentWinStreak)
        assertEquals(2, stats.bestWinStreak)
        assertEquals(1, stats.currentLossStreak)
        assertEquals(1, stats.worstLossStreak)

        stats = stats.gameCompleted(completed(won = true))
        assertEquals(1, stats.currentWinStreak)
        assertEquals(0, stats.currentLossStreak)
    }

    @Test
    fun `score extremes and averages come from completed games`() {
        var stats = Statistics()
        stats = stats.gameCompleted(completed(won = true, gameScore = 208))
        stats = stats.gameCompleted(completed(won = false, gameScore = -52))
        stats = stats.gameCompleted(completed(won = false, gameScore = -12))
        assertEquals(208, stats.bestGameScore)
        assertEquals(-52, stats.worstGameScore)
        assertEquals((208 - 52 - 12) / 3.0, assertNotNull(stats.averageGameScore), 1e-9)
        assertEquals(300L * 3, stats.totalTimeSeconds)
        assertEquals(300L, stats.totalMoves)
    }

    @Test
    fun `empty statistics have no average`() {
        assertNull(Statistics().averageGameScore)
    }

    @Test
    fun `history keeps the most recent games up to the limit`() {
        var stats = Statistics()
        repeat(Statistics.HISTORY_LIMIT + 20) { i ->
            stats = stats.gameCompleted(completed(won = i % 2 == 0, moves = i))
        }
        assertEquals(Statistics.HISTORY_LIMIT, stats.history.size)
        assertEquals(Statistics.HISTORY_LIMIT + 19, stats.history.first().moves, "newest game first")
    }

    @Test
    fun `export import roundtrips`() {
        var stats = Statistics()
        stats = stats.dealStarted().applyScoreDelta(-52)
        stats = stats.gameCompleted(completed(won = true, bankAfter = 208))
        val decoded = assertNotNull(StatisticsCodec.decode(StatisticsCodec.encode(stats)))
        assertEquals(stats, decoded)
    }

    @Test
    fun `corrupt imports decode to null`() {
        assertNull(StatisticsCodec.decode(""))
        assertNull(StatisticsCodec.decode("{"))
        assertNull(StatisticsCodec.decode("""{"version":1}"""))
    }

    @Test
    fun `total time and moves accumulate`() {
        var stats = Statistics()
        stats = stats.gameCompleted(completed(won = false, moves = 10, seconds = 60))
        stats = stats.gameCompleted(completed(won = true, moves = 25, seconds = 120))
        assertEquals(35L, stats.totalMoves)
        assertEquals(180L, stats.totalTimeSeconds)
        assertTrue(stats.gamesCompleted == 2)
    }
}
