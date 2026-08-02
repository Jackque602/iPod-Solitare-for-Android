package com.jackque.solitaire.engine

import com.jackque.solitaire.engine.achievements.AchievementId
import com.jackque.solitaire.engine.achievements.Achievements
import com.jackque.solitaire.engine.achievements.AchievementsState
import com.jackque.solitaire.engine.stats.CompletedGame
import com.jackque.solitaire.engine.stats.Statistics

/**
 * The complete game session: current deal + cumulative bank + statistics,
 * with every scoring and bookkeeping rule in one tested place. The UI layer
 * only forwards intents and persists [toSavedGame]/[stats] after each call.
 *
 * Clock-free by design: callers pass wall-clock timestamps in and tick the
 * elapsed-seconds counter, so every rule stays deterministic under test.
 */
class SolitaireSession private constructor(
    game: KlondikeGame,
    stats: Statistics,
    elapsedSeconds: Long,
    dealSettled: Boolean,
) {
    var game: KlondikeGame = game
        private set
    var stats: Statistics = stats
        private set
    var elapsedSeconds: Long = elapsedSeconds
        private set

    /** True once this deal's win or loss has been recorded in [stats]. */
    private var dealSettled: Boolean = dealSettled

    private var hintCycle: List<Move> = emptyList()
    private var hintIndex: Int = -1
    private var hintStateMoveCount: Int = -1

    /** King arrangements seen at any point along the current deal. */
    private var kingPatternsSeen: MutableSet<AchievementId> =
        Achievements.kingPatternsAt(game.state).toMutableSet()

    private val newlyUnlocked = mutableListOf<AchievementId>()

    val state: GameState get() = game.state
    val bank: Long get() = stats.bank
    val canUndo: Boolean get() = game.canUndo
    val isWon: Boolean get() = state.isWon

    /**
     * Start a fresh deal, charging the $52 buy-in. If the current deal has
     * at least one move played and is unfinished, it is recorded as a loss
     * first. Restarting the same seed is just a new deal with the old seed:
     * it charges another $52, exactly like the original iPod game.
     */
    fun newDeal(seed: Long, drawMode: DrawMode, timestampMillis: Long) {
        settleAsLossIfNeeded(timestampMillis)
        game = KlondikeGame.newDeal(seed, drawMode)
        elapsedSeconds = 0
        dealSettled = false
        resetHints()
        kingPatternsSeen = Achievements.kingPatternsAt(state).toMutableSet()
        stats = stats.dealStarted().applyScoreDelta(Scoring.NEW_DEAL)
    }

    fun restartDeal(timestampMillis: Long) = newDeal(state.seed, state.drawMode, timestampMillis)

    /**
     * Play a move. Score deltas go straight into the bank; winning settles
     * the deal into the statistics. Returns null when illegal.
     */
    fun tryMove(move: Move, timestampMillis: Long): MoveResult? {
        if (isWon) return null
        val result = game.tryMove(move) ?: return null
        resetHints()
        kingPatternsSeen += Achievements.kingPatternsAt(result.state)
        stats = stats.applyScoreDelta(result.scoreDelta, foundationCardsDelta = foundationDelta(result.scoreDelta))
        if (result.won) settleAsWin(timestampMillis)
        return result
    }

    /** Undo the last move, refunding its score change exactly. */
    fun undo(): Boolean {
        if (isWon && dealSettled) return false
        val refund = game.undo() ?: return false
        resetHints()
        // Achievements are a pure function of the surviving move log, so
        // an undone arrangement stops counting (matches save/restore).
        kingPatternsSeen = Achievements.kingPatternsSeen(state.seed, state.drawMode, game.moves)
        stats = stats.applyScoreDelta(refund, foundationCardsDelta = foundationDelta(refund))
        return true
    }

    /** Cycle through the productive moves for the current position. */
    fun nextHint(): Move? {
        if (hintStateMoveCount != state.moveCount) {
            hintCycle = game.hints()
            hintIndex = -1
            hintStateMoveCount = state.moveCount
        }
        if (hintCycle.isEmpty()) return null
        hintIndex = (hintIndex + 1) % hintCycle.size
        return hintCycle[hintIndex]
    }

    val isAutoCompleteAvailable: Boolean get() = game.isAutoCompleteAvailable

    /**
     * Play the entire auto-complete sequence. Every card scores its normal
     * +$5 through [tryMove], so a finished game always nets +$208 for the
     * deal. Returns the number of moves played.
     */
    fun autoCompleteAll(timestampMillis: Long): Int {
        var played = 0
        while (!isWon) {
            val move = game.nextAutoCompleteMove() ?: break
            tryMove(move, timestampMillis) ?: break
            played++
        }
        return played
    }

    fun tick() {
        if (!isWon) elapsedSeconds++
    }

    fun toSavedGame(): SavedGame = game.toSavedGame(elapsedSeconds)

    private fun settleAsWin(timestampMillis: Long) {
        if (dealSettled) return
        dealSettled = true

        var achievements = stats.achievements
        fun unlock(id: AchievementId) {
            if (!achievements.isUnlocked(id)) {
                achievements = achievements.copy(unlocked = achievements.unlocked + (id to timestampMillis))
                newlyUnlocked += id
            }
        }
        Achievements.foundationConfiguration(state)?.let { code ->
            achievements = achievements.copy(
                aceConfigurationsSeen = achievements.aceConfigurationsSeen + code,
            )
        }
        if (achievements.aceConfigurationsSeen.size >= AchievementsState.ACE_CONFIGURATION_TOTAL) {
            unlock(AchievementId.ACE_GRAND_TOUR)
        }
        if (!Achievements.usedRecycle(game.moves)) unlock(AchievementId.SINGLE_PASS)
        kingPatternsSeen.forEach { unlock(it) }

        stats = stats.gameCompleted(
            CompletedGame(
                timestampMillis = timestampMillis,
                seed = state.seed,
                drawMode = state.drawMode,
                won = true,
                moves = state.moveCount,
                durationSeconds = elapsedSeconds,
                gameScore = Scoring.gameScore(state),
                bankAfter = stats.bank,
            )
        ).copy(achievements = achievements)
    }

    /** Achievements earned since the last call; the UI announces them. */
    fun consumeNewlyUnlocked(): List<AchievementId> {
        val out = newlyUnlocked.toList()
        newlyUnlocked.clear()
        return out
    }

    internal val kingPatternsSeenForTest: Set<AchievementId> get() = kingPatternsSeen

    private fun settleAsLossIfNeeded(timestampMillis: Long) {
        if (dealSettled || state.moveCount == 0) return
        dealSettled = true
        stats = stats.gameCompleted(
            CompletedGame(
                timestampMillis = timestampMillis,
                seed = state.seed,
                drawMode = state.drawMode,
                won = false,
                moves = state.moveCount,
                durationSeconds = elapsedSeconds,
                gameScore = Scoring.gameScore(state),
                bankAfter = stats.bank,
            )
        )
    }

    private fun foundationDelta(scoreDelta: Int): Int = when (scoreDelta) {
        Scoring.CARD_TO_FOUNDATION -> 1
        Scoring.CARD_OFF_FOUNDATION -> -1
        else -> 0
    }

    private fun resetHints() {
        hintStateMoveCount = -1
    }

    /** Replace the statistics wholesale (used by import). The bank comes with them. */
    fun importStatistics(imported: Statistics) {
        stats = imported
    }

    /** Test seam: jump to a crafted position without touching the bank. */
    internal fun forceStateForTest(target: GameState) {
        game = KlondikeGame.atStateForTest(target)
        resetHints()
        kingPatternsSeen = Achievements.kingPatternsAt(target).toMutableSet()
    }

    companion object {
        /**
         * Create a session at app start. When [savedGame] restores an
         * in-progress deal, NO buy-in is charged - the $52 was paid when
         * that deal originally started. Only a missing/corrupt save (or a
         * save that finished) starts a fresh deal, which does pay $52.
         */
        fun start(
            stats: Statistics,
            savedGame: SavedGame?,
            newSeed: Long,
            drawMode: DrawMode,
            timestampMillis: Long,
        ): SolitaireSession {
            if (savedGame != null) {
                val restored = KlondikeGame.restore(savedGame)
                if (restored != null && !restored.state.isWon) {
                    val session = SolitaireSession(
                        game = restored,
                        stats = stats,
                        elapsedSeconds = savedGame.elapsedSeconds,
                        dealSettled = false,
                    )
                    // Recover arrangements seen earlier in the restored deal.
                    session.kingPatternsSeen =
                        Achievements.kingPatternsSeen(savedGame.seed, savedGame.drawMode, savedGame.moves)
                    return session
                }
            }
            val session = SolitaireSession(
                game = KlondikeGame.newDeal(newSeed, drawMode),
                stats = stats,
                elapsedSeconds = 0,
                dealSettled = false,
            )
            session.stats = session.stats.dealStarted().applyScoreDelta(Scoring.NEW_DEAL)
            return session
        }
    }
}
