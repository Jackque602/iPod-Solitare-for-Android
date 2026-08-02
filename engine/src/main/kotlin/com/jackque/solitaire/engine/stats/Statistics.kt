package com.jackque.solitaire.engine.stats

import com.jackque.solitaire.engine.DrawMode
import com.jackque.solitaire.engine.achievements.AchievementsState
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One finished (won or abandoned) deal, kept in the history list. */
@Serializable
data class CompletedGame(
    val timestampMillis: Long,
    val seed: Long,
    val drawMode: DrawMode,
    val won: Boolean,
    val moves: Int,
    val durationSeconds: Long,
    /** This deal's own net score: -52 buy-in + $5 per foundation card. */
    val gameScore: Int,
    /** Cumulative bank right after this game ended. */
    val bankAfter: Long,
)

/**
 * All persistent player statistics, including the cumulative money score
 * ("bank"). Immutable: every event produces an updated copy, which makes
 * the arithmetic trivially unit-testable.
 */
@Serializable
data class Statistics(
    val gamesStarted: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val currentWinStreak: Int = 0,
    val bestWinStreak: Int = 0,
    val currentLossStreak: Int = 0,
    val worstLossStreak: Int = 0,
    val totalMoves: Long = 0,
    val totalTimeSeconds: Long = 0,
    /** Net number of cards ever moved onto foundations (undo subtracts). */
    val totalFoundationCards: Long = 0,
    /** The cumulative iPod money score. May be negative. */
    val bank: Long = 0,
    val highestBank: Long = 0,
    val lowestBank: Long = 0,
    val bestGameScore: Int? = null,
    val worstGameScore: Int? = null,
    val sumGameScores: Long = 0,
    val history: List<CompletedGame> = emptyList(),
    val achievements: AchievementsState = AchievementsState(),
) {
    val gamesCompleted: Int get() = wins + losses

    val averageGameScore: Double?
        get() = if (gamesCompleted == 0) null else sumGameScores.toDouble() / gamesCompleted

    /**
     * Apply a money delta (deal buy-in, foundation move or undo) to the
     * bank. [foundationCardsDelta] is +1 when a card landed on a foundation,
     * -1 when one left (including via undo), 0 otherwise.
     */
    fun applyScoreDelta(delta: Int, foundationCardsDelta: Int = 0): Statistics {
        if (delta == 0 && foundationCardsDelta == 0) return this
        val newBank = bank + delta
        return copy(
            bank = newBank,
            highestBank = maxOf(highestBank, newBank),
            lowestBank = minOf(lowestBank, newBank),
            totalFoundationCards = totalFoundationCards + foundationCardsDelta,
        )
    }

    /** Called when a new deal starts. The -52 buy-in goes through [applyScoreDelta]. */
    fun dealStarted(): Statistics = copy(gamesStarted = gamesStarted + 1)

    /**
     * Called when a deal ends - won, or abandoned after at least one move
     * (starting a new game or restarting the deal mid-game counts as a loss).
     */
    fun gameCompleted(game: CompletedGame): Statistics {
        val newWins = if (game.won) wins + 1 else wins
        val newLosses = if (game.won) losses else losses + 1
        val winStreak = if (game.won) currentWinStreak + 1 else 0
        val lossStreak = if (game.won) 0 else currentLossStreak + 1
        return copy(
            wins = newWins,
            losses = newLosses,
            currentWinStreak = winStreak,
            bestWinStreak = maxOf(bestWinStreak, winStreak),
            currentLossStreak = lossStreak,
            worstLossStreak = maxOf(worstLossStreak, lossStreak),
            totalMoves = totalMoves + game.moves,
            totalTimeSeconds = totalTimeSeconds + game.durationSeconds,
            bestGameScore = maxOf(bestGameScore ?: game.gameScore, game.gameScore),
            worstGameScore = minOf(worstGameScore ?: game.gameScore, game.gameScore),
            sumGameScores = sumGameScores + game.gameScore,
            history = (listOf(game) + history).take(HISTORY_LIMIT),
        )
    }

    companion object {
        const val HISTORY_LIMIT = 100
    }
}

/** Versioned envelope used for statistics export/import. */
@Serializable
data class StatisticsExport(
    val version: Int = 1,
    val statistics: Statistics,
)

object StatisticsCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(statistics: Statistics): String =
        json.encodeToString(StatisticsExport(statistics = statistics))

    /** Returns null instead of throwing on malformed/corrupt input. */
    fun decode(text: String): Statistics? = try {
        json.decodeFromString<StatisticsExport>(text).statistics
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
