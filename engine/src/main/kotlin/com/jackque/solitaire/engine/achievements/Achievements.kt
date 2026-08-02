package com.jackque.solitaire.engine.achievements

import com.jackque.solitaire.engine.Dealer
import com.jackque.solitaire.engine.DrawMode
import com.jackque.solitaire.engine.GameState
import com.jackque.solitaire.engine.Klondike
import com.jackque.solitaire.engine.Move
import com.jackque.solitaire.engine.Rank
import kotlinx.serialization.Serializable

enum class AchievementId(val title: String, val description: String) {
    ACE_GRAND_TOUR(
        "Grand Tour",
        "Across your wins, finish games with the four foundations arranged " +
            "in every one of the 24 possible suit orders.",
    ),
    SINGLE_PASS(
        "Single Pass",
        "Win a game without ever turning the waste back over into the stock.",
    ),
    KINGS_WEST(
        "Kings' Court: West",
        "Win a deal in which all four kings stood face up in the four " +
            "leftmost columns at the same time.",
    ),
    KINGS_EAST(
        "Kings' Court: East",
        "Win a deal in which all four kings stood face up in the four " +
            "rightmost columns at the same time.",
    ),
    KINGS_PROCESSION(
        "Kings' Procession",
        "Win a deal in which the four kings stood face up on alternating " +
            "columns (1-3-5-7) at the same time.",
    ),
}

/** Persistent achievement progress; lives inside Statistics so it is saved
 *  and carried along by gameplay-data backups automatically. */
@Serializable
data class AchievementsState(
    /** Unlocked achievement -> wall-clock time it happened. */
    val unlocked: Map<AchievementId, Long> = emptyMap(),
    /** Foundation suit orders (e.g. "SHDC") seen across wins, of the 24 possible. */
    val aceConfigurationsSeen: Set<String> = emptySet(),
) {
    fun isUnlocked(id: AchievementId): Boolean = unlocked.containsKey(id)

    companion object {
        const val ACE_CONFIGURATION_TOTAL = 24
    }
}

object Achievements {

    val KINGS_WEST_COLUMNS = setOf(0, 1, 2, 3)
    val KINGS_EAST_COLUMNS = setOf(3, 4, 5, 6)
    val KINGS_PROCESSION_COLUMNS = setOf(0, 2, 4, 6)

    /** Tableau columns currently holding a face-up king. */
    fun faceUpKingColumns(state: GameState): Set<Int> =
        (0..6).filter { col ->
            state.tableau[col].any { it.faceUp && it.rank == Rank.KING }
        }.toSet()

    /**
     * King-arrangement achievements satisfied by this exact position.
     * Set equality with a four-column pattern requires all four kings to
     * be face up on the tableau, one in each pattern column.
     */
    fun kingPatternsAt(state: GameState): Set<AchievementId> {
        val columns = faceUpKingColumns(state)
        val out = mutableSetOf<AchievementId>()
        if (columns == KINGS_WEST_COLUMNS) out += AchievementId.KINGS_WEST
        if (columns == KINGS_EAST_COLUMNS) out += AchievementId.KINGS_EAST
        if (columns == KINGS_PROCESSION_COLUMNS) out += AchievementId.KINGS_PROCESSION
        return out
    }

    /**
     * King patterns visible at ANY point along the deal: the initial
     * layout and every position reached by [moves]. Undo trims the move
     * log, so an arrangement that was undone no longer counts - the
     * result is a pure function of the surviving moves, which also makes
     * save/restore reproduce it exactly.
     */
    fun kingPatternsSeen(seed: Long, drawMode: DrawMode, moves: List<Move>): MutableSet<AchievementId> {
        var state = Dealer.deal(seed, drawMode)
        val seen = kingPatternsAt(state).toMutableSet()
        for (move in moves) {
            state = Klondike.apply(state, move)?.state ?: break
            seen += kingPatternsAt(state)
        }
        return seen
    }

    fun usedRecycle(moves: List<Move>): Boolean = moves.any { it is Move.Recycle }

    /** Foundation suit order of a won game, e.g. "SHDC"; null before the win. */
    fun foundationConfiguration(state: GameState): String? =
        if (!state.isWon) null
        else state.foundations.joinToString("") { it.first().suit.letter.toString() }
}
