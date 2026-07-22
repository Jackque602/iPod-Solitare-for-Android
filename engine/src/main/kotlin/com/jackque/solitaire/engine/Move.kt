package com.jackque.solitaire.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Every player action that mutates the game. Moves are serializable so a
 * game in progress can be persisted as (seed + move list) and replayed
 * deterministically on restore.
 */
@Serializable
sealed class Move {

    /** Turn 1 or 3 cards (depending on [DrawMode]) from the stock onto the waste. */
    @Serializable
    @SerialName("draw")
    data object Draw : Move()

    /** Turn the exhausted waste pile back over to form a new stock. */
    @Serializable
    @SerialName("recycle")
    data object Recycle : Move()

    @Serializable
    @SerialName("wf")
    data class WasteToFoundation(val foundation: Int) : Move()

    @Serializable
    @SerialName("wt")
    data class WasteToTableau(val column: Int) : Move()

    @Serializable
    @SerialName("tf")
    data class TableauToFoundation(val column: Int, val foundation: Int) : Move()

    @Serializable
    @SerialName("ft")
    data class FoundationToTableau(val foundation: Int, val column: Int) : Move()

    @Serializable
    @SerialName("tt")
    data class TableauToTableau(val fromColumn: Int, val cardIndex: Int, val toColumn: Int) : Move()
}

/** Result of successfully applying a [Move]. */
data class MoveResult(
    val state: GameState,
    /** Money delta caused by this move: +5 to foundation, -5 off foundation, else 0. */
    val scoreDelta: Int,
    /** True when the move exposed (flipped) a face-down tableau card. */
    val flippedCard: Boolean,
    /** True when the game is won after this move. */
    val won: Boolean,
)
