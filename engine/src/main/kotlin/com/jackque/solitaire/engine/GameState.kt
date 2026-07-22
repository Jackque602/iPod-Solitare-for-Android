package com.jackque.solitaire.engine

import kotlinx.serialization.Serializable

enum class DrawMode(val count: Int) { DRAW_ONE(1), DRAW_THREE(3) }

/**
 * Immutable snapshot of a Klondike game.
 *
 * Pile ordering convention: for every pile the LAST element of the list is
 * the top of the pile (the card that is visible / playable).
 */
@Serializable
data class GameState(
    val seed: Long,
    val drawMode: DrawMode,
    val stock: List<Card>,
    val waste: List<Card>,
    val foundations: List<List<Card>>,
    val tableau: List<List<Card>>,
    val moveCount: Int = 0,
) {
    init {
        require(foundations.size == 4) { "Klondike has exactly 4 foundations" }
        require(tableau.size == 7) { "Klondike has exactly 7 tableau columns" }
    }

    val foundationCardCount: Int get() = foundations.sumOf { it.size }

    val isWon: Boolean get() = foundationCardCount == 52
}

/**
 * Deterministic pseudo-random number generator (SplitMix64). Implemented
 * here rather than relying on a platform RNG so that a given seed always
 * produces exactly the same deal on every device and app version.
 */
class SeededRng(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return ((nextLong() ushr 1) % bound).toInt()
    }
}

object Dealer {

    /** The full 52-card deck in a fixed canonical order. */
    fun standardDeck(): List<Card> =
        Suit.entries.flatMap { suit -> Rank.entries.map { rank -> Card(rank, suit) } }

    /** Fisher-Yates shuffle driven entirely by [SeededRng] for determinism. */
    fun shuffledDeck(seed: Long): List<Card> {
        val deck = standardDeck().toMutableList()
        val rng = SeededRng(seed)
        for (i in deck.indices.reversed()) {
            if (i == 0) break
            val j = rng.nextInt(i + 1)
            val tmp = deck[i]
            deck[i] = deck[j]
            deck[j] = tmp
        }
        return deck
    }

    /**
     * Deal a new game: column N of the tableau (0-based) receives N+1 cards
     * with only the last one face up; the remaining 24 cards form the stock.
     */
    fun deal(seed: Long, drawMode: DrawMode): GameState {
        val deck = shuffledDeck(seed)
        var index = 0
        val tableau = List(7) { col ->
            List(col + 1) { row ->
                deck[index++].copy(faceUp = row == col)
            }
        }
        // The next card of the deck must be the first card drawn. Stock keeps
        // its top at the END of the list, so the remainder is reversed.
        val stock = deck.subList(index, deck.size).map { it.copy(faceUp = false) }.reversed()
        return GameState(
            seed = seed,
            drawMode = drawMode,
            stock = stock,
            waste = emptyList(),
            foundations = List(4) { emptyList() },
            tableau = tableau,
        )
    }
}
