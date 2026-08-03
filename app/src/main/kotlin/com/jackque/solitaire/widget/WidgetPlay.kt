package com.jackque.solitaire.widget

import com.jackque.solitaire.engine.GameState
import com.jackque.solitaire.engine.SolitaireSession
import com.jackque.solitaire.engine.input.Cursor
import com.jackque.solitaire.engine.input.Navigator
import com.jackque.solitaire.engine.input.Selection
import com.jackque.solitaire.engine.input.Zone

/**
 * Pure tap-to-play logic for the widget board. Zone tokens ("stock",
 * "waste", "f0".."f3", "t0".."t6") arrive from per-view PendingIntents;
 * everything else reuses the app's Navigator select-then-drop model.
 * Because widgets cannot express tap position within a column, tapping
 * an already-selected column picks up one more card per tap and then
 * clears the selection.
 */
object WidgetPlay {

    val ZONE_TOKENS: List<String> =
        listOf("stock", "waste") + (0..3).map { "f$it" } + (0..6).map { "t$it" }

    fun cursorFor(token: String): Cursor? = when {
        token == "stock" -> Cursor(Zone.STOCK)
        token == "waste" -> Cursor(Zone.WASTE)
        token.startsWith("f") ->
            token.drop(1).toIntOrNull()?.takeIf { it in 0..3 }?.let { Cursor(Zone.FOUNDATION, it) }
        token.startsWith("t") ->
            token.drop(1).toIntOrNull()?.takeIf { it in 0..6 }?.let { Cursor(Zone.TABLEAU, it) }
        else -> null
    }

    /** Apply one tap; returns the selection to keep for the next tap. */
    fun tap(
        session: SolitaireSession,
        selection: Selection?,
        token: String,
        timestampMillis: Long,
    ): Selection? {
        val cursor = cursorFor(token) ?: return selection
        val state = session.state

        // Tapping the selected column again deepens the picked-up run.
        if (selection != null &&
            cursor.zone == Zone.TABLEAU &&
            selection.zone == Zone.TABLEAU &&
            selection.index == cursor.index
        ) {
            val pile = state.tableau[cursor.index]
            val maxDepth = Navigator.maxDepth(state, cursor.index)
            val currentDepth = pile.lastIndex - selection.cardIndex
            return if (currentDepth < maxDepth) {
                selection.copy(cardIndex = selection.cardIndex - 1)
            } else {
                null
            }
        }

        val activation = Navigator.activate(state, cursor, selection)
        val move = activation.move
        return if (move != null) {
            session.tryMove(move, timestampMillis)
            null
        } else {
            activation.selection
        }
    }

    fun encodeSelection(selection: Selection?): String =
        selection?.let { "${it.zone.name}:${it.index}:${it.cardIndex}" } ?: ""

    fun decodeSelection(text: String): Selection? {
        val parts = text.split(":")
        if (parts.size != 3) return null
        val zone = runCatching { Zone.valueOf(parts[0]) }.getOrNull() ?: return null
        val index = parts[1].toIntOrNull() ?: return null
        val cardIndex = parts[2].toIntOrNull() ?: return null
        return Selection(zone, index, cardIndex)
    }

    /** Drop a stored selection that no longer matches the position. */
    fun sanitize(state: GameState, selection: Selection?): Selection? {
        selection ?: return null
        return when (selection.zone) {
            Zone.STOCK -> null
            Zone.WASTE ->
                if (state.waste.isNotEmpty() && selection.cardIndex == 0) selection else null
            Zone.FOUNDATION ->
                selection.takeIf {
                    it.index in 0..3 && state.foundations[it.index].isNotEmpty() && it.cardIndex == 0
                }
            Zone.TABLEAU -> {
                if (selection.index !in 0..6) return null
                val pile = state.tableau[selection.index]
                if (selection.cardIndex !in pile.indices) return null
                val depth = pile.lastIndex - selection.cardIndex
                if (depth <= Navigator.maxDepth(state, selection.index)) selection else null
            }
        }
    }
}
