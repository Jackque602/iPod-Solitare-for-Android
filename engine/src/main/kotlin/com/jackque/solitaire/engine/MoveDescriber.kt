package com.jackque.solitaire.engine

/** Short human-readable descriptions of moves, used by the hint display. */
object MoveDescriber {

    private fun label(card: Card): String = "${card.rank.label}${card.suit.symbol}"

    fun describe(state: GameState, move: Move): String = when (move) {
        is Move.Draw -> "Draw from the stock"
        is Move.Recycle -> "Turn the waste over"
        is Move.WasteToFoundation ->
            state.waste.lastOrNull()?.let { "${label(it)} to the foundation" } ?: "Waste to foundation"
        is Move.WasteToTableau ->
            state.waste.lastOrNull()?.let { "${label(it)} to column ${move.column + 1}" } ?: "Waste to tableau"
        is Move.TableauToFoundation ->
            state.tableau[move.column].lastOrNull()?.let { "${label(it)} to the foundation" }
                ?: "Column ${move.column + 1} to foundation"
        is Move.FoundationToTableau ->
            state.foundations[move.foundation].lastOrNull()?.let { "${label(it)} back to column ${move.column + 1}" }
                ?: "Foundation to column ${move.column + 1}"
        is Move.TableauToTableau -> {
            val card = state.tableau[move.fromColumn].getOrNull(move.cardIndex)
            if (card != null) "${label(card)} to column ${move.toColumn + 1}"
            else "Column ${move.fromColumn + 1} to column ${move.toColumn + 1}"
        }
    }
}
