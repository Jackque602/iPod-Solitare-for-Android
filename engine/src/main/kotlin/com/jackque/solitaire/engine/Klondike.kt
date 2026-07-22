package com.jackque.solitaire.engine

/**
 * Pure rule engine for Klondike Solitaire. All functions are side-effect
 * free: they take a [GameState] and return a new one (or null when a move
 * is illegal), which keeps every rule unit-testable without any UI.
 */
object Klondike {

    fun canPlaceOnFoundation(card: Card, foundation: List<Card>): Boolean {
        val top = foundation.lastOrNull() ?: return card.rank == Rank.ACE
        return top.suit == card.suit && card.rank.value == top.rank.value + 1
    }

    fun canPlaceOnTableau(card: Card, column: List<Card>): Boolean {
        val top = column.lastOrNull() ?: return card.rank == Rank.KING
        return top.faceUp &&
            top.isRed != card.isRed &&
            top.rank.value == card.rank.value + 1
    }

    /** True when [cards] is a legal alternating-color descending run. */
    fun isValidRun(cards: List<Card>): Boolean =
        cards.all { it.faceUp } &&
            cards.zipWithNext().all { (below, above) ->
                below.rank.value == above.rank.value + 1 && below.isRed != above.isRed
            }

    fun isLegal(state: GameState, move: Move): Boolean = apply(state, move) != null

    /**
     * Apply [move] to [state] with full validation.
     * Returns null when the move is illegal.
     */
    fun apply(state: GameState, move: Move): MoveResult? = when (move) {
        is Move.Draw -> applyDraw(state)
        is Move.Recycle -> applyRecycle(state)
        is Move.WasteToFoundation -> applyWasteToFoundation(state, move.foundation)
        is Move.WasteToTableau -> applyWasteToTableau(state, move.column)
        is Move.TableauToFoundation -> applyTableauToFoundation(state, move.column, move.foundation)
        is Move.FoundationToTableau -> applyFoundationToTableau(state, move.foundation, move.column)
        is Move.TableauToTableau -> applyTableauToTableau(state, move.fromColumn, move.cardIndex, move.toColumn)
    }

    private fun applyDraw(state: GameState): MoveResult? {
        if (state.stock.isEmpty()) return null
        val n = minOf(state.drawMode.count, state.stock.size)
        val drawn = state.stock.takeLast(n)
        val newState = state.copy(
            stock = state.stock.dropLast(n),
            // Cards leave the top of the stock one at a time, so the top of
            // the stock ends up deepest in the newly drawn waste cards.
            waste = state.waste + drawn.reversed().map { it.copy(faceUp = true) },
            moveCount = state.moveCount + 1,
        )
        return MoveResult(newState, scoreDelta = 0, flippedCard = false, won = false)
    }

    private fun applyRecycle(state: GameState): MoveResult? {
        if (state.stock.isNotEmpty() || state.waste.isEmpty()) return null
        val newState = state.copy(
            // Physically turning the waste pile over reverses its order, so
            // repeated passes cycle through the stock in a stable order.
            stock = state.waste.map { it.copy(faceUp = false) }.reversed(),
            waste = emptyList(),
            moveCount = state.moveCount + 1,
        )
        return MoveResult(newState, scoreDelta = 0, flippedCard = false, won = false)
    }

    private fun applyWasteToFoundation(state: GameState, f: Int): MoveResult? {
        if (f !in 0..3) return null
        val card = state.waste.lastOrNull() ?: return null
        if (!canPlaceOnFoundation(card, state.foundations[f])) return null
        val newState = state.copy(
            waste = state.waste.dropLast(1),
            foundations = state.foundations.replaceAt(f) { it + card.copy(faceUp = true) },
            moveCount = state.moveCount + 1,
        )
        return MoveResult(newState, Scoring.CARD_TO_FOUNDATION, flippedCard = false, won = newState.isWon)
    }

    private fun applyWasteToTableau(state: GameState, col: Int): MoveResult? {
        if (col !in 0..6) return null
        val card = state.waste.lastOrNull() ?: return null
        if (!canPlaceOnTableau(card, state.tableau[col])) return null
        val newState = state.copy(
            waste = state.waste.dropLast(1),
            tableau = state.tableau.replaceAt(col) { it + card.copy(faceUp = true) },
            moveCount = state.moveCount + 1,
        )
        return MoveResult(newState, scoreDelta = 0, flippedCard = false, won = false)
    }

    private fun applyTableauToFoundation(state: GameState, col: Int, f: Int): MoveResult? {
        if (col !in 0..6 || f !in 0..3) return null
        val column = state.tableau[col]
        val card = column.lastOrNull() ?: return null
        if (!card.faceUp) return null
        if (!canPlaceOnFoundation(card, state.foundations[f])) return null
        val (remaining, flipped) = column.dropLast(1).flipTopIfNeeded()
        val newState = state.copy(
            tableau = state.tableau.replaceAt(col) { remaining },
            foundations = state.foundations.replaceAt(f) { it + card },
            moveCount = state.moveCount + 1,
        )
        return MoveResult(newState, Scoring.CARD_TO_FOUNDATION, flippedCard = flipped, won = newState.isWon)
    }

    private fun applyFoundationToTableau(state: GameState, f: Int, col: Int): MoveResult? {
        if (f !in 0..3 || col !in 0..6) return null
        val card = state.foundations[f].lastOrNull() ?: return null
        if (!canPlaceOnTableau(card, state.tableau[col])) return null
        val newState = state.copy(
            foundations = state.foundations.replaceAt(f) { it.dropLast(1) },
            tableau = state.tableau.replaceAt(col) { it + card.copy(faceUp = true) },
            moveCount = state.moveCount + 1,
        )
        return MoveResult(newState, Scoring.CARD_OFF_FOUNDATION, flippedCard = false, won = false)
    }

    private fun applyTableauToTableau(state: GameState, from: Int, cardIndex: Int, to: Int): MoveResult? {
        if (from !in 0..6 || to !in 0..6 || from == to) return null
        val source = state.tableau[from]
        if (cardIndex !in source.indices) return null
        val moving = source.subList(cardIndex, source.size)
        if (!isValidRun(moving)) return null
        if (!canPlaceOnTableau(moving.first(), state.tableau[to])) return null
        val (remaining, flipped) = source.subList(0, cardIndex).flipTopIfNeeded()
        val newState = state.copy(
            tableau = state.tableau
                .replaceAt(from) { remaining }
                .let { t -> t.replaceAt(to) { it + moving } },
            moveCount = state.moveCount + 1,
        )
        return MoveResult(newState, scoreDelta = 0, flippedCard = flipped, won = false)
    }

    // ------------------------------------------------------------------
    // Move enumeration, hints and auto-complete
    // ------------------------------------------------------------------

    /** Every legal move in [state], with no judgement about usefulness. */
    fun legalMoves(state: GameState): List<Move> {
        val moves = mutableListOf<Move>()
        for (col in 0..6) for (f in 0..3) {
            if (isLegal(state, Move.TableauToFoundation(col, f))) moves += Move.TableauToFoundation(col, f)
        }
        for (f in 0..3) {
            if (isLegal(state, Move.WasteToFoundation(f))) moves += Move.WasteToFoundation(f)
        }
        for (from in 0..6) {
            val source = state.tableau[from]
            for (cardIndex in source.indices) {
                if (!source[cardIndex].faceUp) continue
                for (to in 0..6) {
                    val move = Move.TableauToTableau(from, cardIndex, to)
                    if (isLegal(state, move)) moves += move
                }
            }
        }
        for (col in 0..6) {
            if (isLegal(state, Move.WasteToTableau(col))) moves += Move.WasteToTableau(col)
        }
        for (f in 0..3) for (col in 0..6) {
            if (isLegal(state, Move.FoundationToTableau(f, col))) moves += Move.FoundationToTableau(f, col)
        }
        if (isLegal(state, Move.Draw)) moves += Move.Draw
        if (isLegal(state, Move.Recycle)) moves += Move.Recycle
        return moves
    }

    /**
     * Productive moves ordered from most to least valuable. The UI shows the
     * first one and cycles through the rest on repeated hint requests.
     */
    fun hints(state: GameState): List<Move> {
        val all = legalMoves(state)
        val toFoundation = all.filter { it is Move.TableauToFoundation || it is Move.WasteToFoundation }
        val revealing = all.filterIsInstance<Move.TableauToTableau>()
            .filter { it.cardIndex > 0 && !state.tableau[it.fromColumn][it.cardIndex - 1].faceUp }
            .sortedByDescending { move ->
                state.tableau[move.fromColumn].count { !it.faceUp }
            }
        val wasteToTableau = all.filterIsInstance<Move.WasteToTableau>()
        // A whole face-up column is only worth relocating when it frees a
        // column that still hides face-down cards -> covered by `revealing`.
        // Moving a full column with nothing underneath just shuffles cards,
        // so it is never suggested.
        val draw = all.filter { it is Move.Draw || it is Move.Recycle }
        return (toFoundation + revealing + wasteToTableau + draw).distinct()
    }

    /**
     * Auto-complete is offered once nothing is hidden any more: stock and
     * waste are empty and every tableau card is face up. In that position a
     * lowest-rank-first greedy strategy always finishes the game.
     */
    fun isAutoCompleteAvailable(state: GameState): Boolean =
        !state.isWon &&
            state.stock.isEmpty() &&
            state.waste.isEmpty() &&
            state.tableau.all { column -> column.all { it.faceUp } }

    /** The next move auto-complete would play, or null when done/unavailable. */
    fun nextAutoCompleteMove(state: GameState): Move? {
        if (!isAutoCompleteAvailable(state)) return null
        var best: Move.TableauToFoundation? = null
        var bestValue = Int.MAX_VALUE
        for (col in 0..6) {
            val card = state.tableau[col].lastOrNull() ?: continue
            for (f in 0..3) {
                if (canPlaceOnFoundation(card, state.foundations[f])) {
                    if (card.rank.value < bestValue) {
                        bestValue = card.rank.value
                        best = Move.TableauToFoundation(col, f)
                    }
                    break
                }
            }
        }
        return best
    }

    private fun List<Card>.flipTopIfNeeded(): Pair<List<Card>, Boolean> {
        val top = lastOrNull() ?: return this to false
        return if (top.faceUp) {
            this to false
        } else {
            (dropLast(1) + top.copy(faceUp = true)) to true
        }
    }

    private inline fun <T> List<List<T>>.replaceAt(index: Int, transform: (List<T>) -> List<T>): List<List<T>> =
        mapIndexed { i, pile -> if (i == index) transform(pile) else pile }
}
