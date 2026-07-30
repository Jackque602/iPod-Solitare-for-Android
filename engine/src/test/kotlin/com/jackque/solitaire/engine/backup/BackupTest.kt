package com.jackque.solitaire.engine.backup

import com.jackque.solitaire.engine.DrawMode
import com.jackque.solitaire.engine.Klondike
import com.jackque.solitaire.engine.KlondikeGame
import com.jackque.solitaire.engine.SolitaireSession
import com.jackque.solitaire.engine.settings.AppSettings
import com.jackque.solitaire.engine.settings.SuitStyle
import com.jackque.solitaire.engine.stats.Statistics
import com.jackque.solitaire.engine.stats.StatisticsCodec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BackupTest {

    private fun playedSession(): SolitaireSession {
        val session = SolitaireSession.start(Statistics(), null, 42L, DrawMode.DRAW_THREE, 1_700_000_000_000)
        val rng = Random(7)
        repeat(30) {
            val moves = Klondike.legalMoves(session.state)
            if (moves.isNotEmpty()) session.tryMove(moves[rng.nextInt(moves.size)], 1_700_000_000_000)
        }
        return session
    }

    @Test
    fun `full backup roundtrips exactly`() {
        val session = playedSession()
        val backup = BackupData(
            statistics = session.stats,
            settings = AppSettings(suitStyle = SuitStyle.LETTERS),
            savedGame = session.toSavedGame(),
        )
        val decoded = assertNotNull(BackupCodec.decode(BackupCodec.encode(backup)))
        assertEquals(backup.statistics, decoded.statistics)
        assertEquals(backup.savedGame, decoded.savedGame)
        assertEquals(SuitStyle.LETTERS, decoded.settings?.suitStyle)

        // The embedded save restores the exact position and undo history.
        val restored = assertNotNull(KlondikeGame.restore(decoded.savedGame!!))
        assertEquals(session.state, restored.state)
    }

    @Test
    fun `legacy statistics-only exports import as backups`() {
        var stats = Statistics()
        stats = stats.dealStarted().applyScoreDelta(-52)
        val legacy = StatisticsCodec.encode(stats)
        val decoded = assertNotNull(BackupCodec.decode(legacy))
        assertEquals(stats, decoded.statistics)
        assertNull(decoded.settings)
        assertNull(decoded.savedGame)
    }

    @Test
    fun `backups remain readable by the legacy statistics importer`() {
        val session = playedSession()
        val text = BackupCodec.encode(
            BackupData(statistics = session.stats, savedGame = session.toSavedGame()),
        )
        assertEquals(session.stats, StatisticsCodec.decode(text))
    }

    @Test
    fun `embedded settings pass through migrations`() {
        val text = """{"version":2,"statistics":{},"settings":{"suitStyle":"OUTLINE","drawMode":"DRAW_ONE"}}"""
        val decoded = assertNotNull(BackupCodec.decode(text))
        assertEquals(SuitStyle.INVERTED, decoded.settings?.suitStyle, "v1 OUTLINE default migrates")
        assertEquals(DrawMode.DRAW_THREE, decoded.settings?.drawMode, "old DRAW_ONE default migrates")
    }

    @Test
    fun `corrupt backups decode to null`() {
        assertNull(BackupCodec.decode(""))
        assertNull(BackupCodec.decode("not json"))
        assertNull(BackupCodec.decode("""{"version":2}"""), "statistics are required")
        assertNull(BackupCodec.decode("""{"statistics":{},"savedGame":{"seed":"x"}}"""))
    }
}
