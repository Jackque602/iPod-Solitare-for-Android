package com.jackque.solitaire.engine

/**
 * A mutable game session on top of the pure [Klondike] rules.
 *
 * Keeps a full snapshot history so undo is exact (state, move count AND
 * score are restored precisely), plus the move log so the whole session can
 * be persisted as (seed + moves) and replayed deterministically.
 */
class KlondikeGame private constructor(
    initialState: GameState,
) {
    var state: GameState = initialState
        private set

    private data class HistoryEntry(val stateBefore: GameState, val scoreDelta: Int)

    private val history = ArrayDeque<HistoryEntry>()
    private val moveLog = mutableListOf<Move>()

    /** All moves played so far, in order. */
    val moves: List<Move> get() = moveLog

    val canUndo: Boolean get() = history.isNotEmpty()

    /**
     * Try to play [move]. Returns the result (including the money delta the
     * caller must add to the cumulative bank) or null when illegal.
     */
    fun tryMove(move: Move): MoveResult? {
        val result = Klondike.apply(state, move) ?: return null
        history.addLast(HistoryEntry(state, result.scoreDelta))
        moveLog.add(move)
        state = result.state
        return result
    }

    /**
     * Undo the last move. Returns the money delta to apply to the bank
     * (the exact negation of what the move earned) or null if there is
     * nothing to undo.
     */
    fun undo(): Int? {
        val entry = history.removeLastOrNull() ?: return null
        moveLog.removeAt(moveLog.lastIndex)
        state = entry.stateBefore
        return -entry.scoreDelta
    }

    fun hints(): List<Move> = Klondike.hints(state)

    val isAutoCompleteAvailable: Boolean get() = Klondike.isAutoCompleteAvailable(state)

    fun nextAutoCompleteMove(): Move? = Klondike.nextAutoCompleteMove(state)

    fun toSavedGame(elapsedSeconds: Long): SavedGame =
        SavedGame(state.seed, state.drawMode, moveLog.toList(), elapsedSeconds)

    companion object {
        /** Start a fresh deal. The caller applies [Scoring.NEW_DEAL] to the bank. */
        fun newDeal(seed: Long, drawMode: DrawMode): KlondikeGame =
            KlondikeGame(Dealer.deal(seed, drawMode))

        /** Test seam: a game standing at an arbitrary crafted position. */
        internal fun atStateForTest(state: GameState): KlondikeGame = KlondikeGame(state)

        /**
         * Rebuild a session from a save by replaying its move log from the
         * seed. Restoring never charges the $52 buy-in again - that happened
         * when the deal originally started. Returns null when the save is
         * corrupt (a logged move no longer applies).
         */
        fun restore(saved: SavedGame): KlondikeGame? {
            val game = newDeal(saved.seed, saved.drawMode)
            for (move in saved.moves) {
                game.tryMove(move) ?: return null
            }
            return game
        }
    }
}
