package com.jackque.solitaire.engine.input

import com.jackque.solitaire.engine.GameState
import com.jackque.solitaire.engine.Klondike
import com.jackque.solitaire.engine.Move
import kotlinx.serialization.Serializable

/** The interactive areas of the board. */
enum class Zone { STOCK, WASTE, FOUNDATION, TABLEAU }

/**
 * Keyboard/touch cursor. [index] is the foundation number (0-3) or tableau
 * column (0-6); [depth] is only used on the tableau and counts how many
 * extra cards above the top card are included when picking up a run
 * (0 = just the top card).
 */
@Serializable
data class Cursor(val zone: Zone, val index: Int = 0, val depth: Int = 0)

/** A picked-up card or run waiting for a destination. */
@Serializable
data class Selection(val zone: Zone, val index: Int = 0, val cardIndex: Int = 0)

enum class Direction { LEFT, RIGHT, UP, DOWN }

/**
 * What pressing CONFIRM at the cursor should do: play a move, change the
 * selection, or clear it. Exactly reflecting iPod Solitaire's
 * select-source-then-select-target interaction, shared by touch taps and
 * the keyboard so both behave identically.
 */
data class Activation(
    val move: Move? = null,
    val selection: Selection? = null,
)

/**
 * Pure cursor-navigation and selection model. The top row lays out as
 * columns 0..6: stock, waste, (gap), foundations 0-3; the tableau row has
 * columns 0..6. Vertical movement keeps the horizontal column aligned.
 */
object Navigator {

    /** Length of the movable face-up run on top of a tableau column. */
    fun faceUpRunLength(state: GameState, column: Int): Int {
        val pile = state.tableau[column]
        var count = 0
        for (i in pile.indices.reversed()) {
            val card = pile[i]
            if (!card.faceUp) break
            // Only count upward while it remains a movable run.
            if (i < pile.lastIndex) {
                val above = pile[i + 1]
                if (card.rank.value != above.rank.value + 1 || card.isRed == above.isRed) break
            }
            count++
        }
        return count
    }

    fun maxDepth(state: GameState, column: Int): Int =
        (faceUpRunLength(state, column) - 1).coerceAtLeast(0)

    private fun topSlotForColumn(column: Int): Cursor = when (column) {
        0 -> Cursor(Zone.STOCK)
        1, 2 -> Cursor(Zone.WASTE)
        else -> Cursor(Zone.FOUNDATION, column - 3)
    }

    private fun columnForTopSlot(cursor: Cursor): Int = when (cursor.zone) {
        Zone.STOCK -> 0
        Zone.WASTE -> 1
        Zone.FOUNDATION -> cursor.index + 3
        Zone.TABLEAU -> cursor.index
    }

    fun move(state: GameState, cursor: Cursor, direction: Direction): Cursor = when (direction) {
        Direction.LEFT, Direction.RIGHT -> {
            val delta = if (direction == Direction.LEFT) -1 else 1
            if (cursor.zone == Zone.TABLEAU) {
                Cursor(Zone.TABLEAU, (cursor.index + delta + 7) % 7)
            } else {
                // Top row positions: 0=stock, 1=waste, 2..5=foundations.
                val position = when (cursor.zone) {
                    Zone.STOCK -> 0
                    Zone.WASTE -> 1
                    else -> cursor.index + 2
                }
                when (val next = (position + delta + 6) % 6) {
                    0 -> Cursor(Zone.STOCK)
                    1 -> Cursor(Zone.WASTE)
                    else -> Cursor(Zone.FOUNDATION, next - 2)
                }
            }
        }
        Direction.UP ->
            if (cursor.zone == Zone.TABLEAU) {
                if (cursor.depth < maxDepth(state, cursor.index)) {
                    cursor.copy(depth = cursor.depth + 1)
                } else {
                    topSlotForColumn(cursor.index)
                }
            } else cursor
        Direction.DOWN ->
            if (cursor.zone == Zone.TABLEAU) {
                if (cursor.depth > 0) cursor.copy(depth = cursor.depth - 1) else cursor
            } else {
                Cursor(Zone.TABLEAU, columnForTopSlot(cursor))
            }
    }

    /** CONFIRM/tap at [cursor] with an optional existing [selection]. */
    fun activate(state: GameState, cursor: Cursor, selection: Selection?): Activation {
        if (selection == null) return select(state, cursor)

        val move = moveFor(state, selection, cursor)
        if (move != null && Klondike.isLegal(state, move)) return Activation(move = move)

        // Not a legal drop: tapping the source again clears the selection,
        // anything else selectable becomes the new selection.
        val newSelection = select(state, cursor).selection
        return if (newSelection == selection) Activation() else Activation(selection = newSelection)
    }

    private fun select(state: GameState, cursor: Cursor): Activation = when (cursor.zone) {
        Zone.STOCK ->
            if (state.stock.isNotEmpty()) Activation(move = Move.Draw)
            else if (state.waste.isNotEmpty()) Activation(move = Move.Recycle)
            else Activation()
        Zone.WASTE ->
            if (state.waste.isNotEmpty()) Activation(selection = Selection(Zone.WASTE)) else Activation()
        Zone.FOUNDATION ->
            if (state.foundations[cursor.index].isNotEmpty()) {
                Activation(selection = Selection(Zone.FOUNDATION, cursor.index))
            } else Activation()
        Zone.TABLEAU -> {
            val pile = state.tableau[cursor.index]
            val depth = cursor.depth.coerceAtMost(maxDepth(state, cursor.index))
            if (pile.isEmpty()) Activation()
            else Activation(selection = Selection(Zone.TABLEAU, cursor.index, pile.size - 1 - depth))
        }
    }

    /** Translate a selection + destination cursor into a concrete move. */
    fun moveFor(state: GameState, selection: Selection, cursor: Cursor): Move? = when (selection.zone) {
        Zone.WASTE -> when (cursor.zone) {
            Zone.FOUNDATION -> Move.WasteToFoundation(cursor.index)
            Zone.TABLEAU -> Move.WasteToTableau(cursor.index)
            else -> null
        }
        Zone.FOUNDATION -> when (cursor.zone) {
            Zone.TABLEAU -> Move.FoundationToTableau(selection.index, cursor.index)
            else -> null
        }
        Zone.TABLEAU -> when (cursor.zone) {
            Zone.FOUNDATION ->
                if (selection.cardIndex == state.tableau[selection.index].lastIndex) {
                    Move.TableauToFoundation(selection.index, cursor.index)
                } else null
            Zone.TABLEAU -> Move.TableauToTableau(selection.index, selection.cardIndex, cursor.index)
            else -> null
        }
        Zone.STOCK -> null
    }

    /**
     * The move the TO_FOUNDATION shortcut (or a double tap) should play for
     * the top card at [cursor]: the first legal foundation, if any.
     */
    fun autoFoundationMove(state: GameState, cursor: Cursor): Move? {
        val candidates = when (cursor.zone) {
            Zone.WASTE -> (0..3).map { Move.WasteToFoundation(it) }
            Zone.TABLEAU -> (0..3).map { Move.TableauToFoundation(cursor.index, it) }
            else -> emptyList()
        }
        return candidates.firstOrNull { Klondike.isLegal(state, it) }
    }

    /** Keep the cursor valid after the state changed under it. */
    fun clamp(state: GameState, cursor: Cursor): Cursor = when (cursor.zone) {
        Zone.TABLEAU -> cursor.copy(depth = cursor.depth.coerceIn(0, maxDepth(state, cursor.index)))
        else -> cursor
    }
}
