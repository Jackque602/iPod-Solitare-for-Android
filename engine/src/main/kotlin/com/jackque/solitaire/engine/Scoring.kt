package com.jackque.solitaire.engine

/**
 * Original iPod click-wheel Solitaire money scoring (cumulative Vegas style).
 *
 * - Starting ANY new deal (including restarting the current deal) costs $52.
 * - Every card moved onto a foundation earns $5.
 * - Moving a card back off a foundation returns the $5.
 * - Completing a game therefore nets 52 x $5 - $52 = +$208.
 * - The balance is cumulative across games, persists forever and may go
 *   negative. It is a score, not currency: no betting, banking or gambling.
 */
object Scoring {
    const val NEW_DEAL = -52
    const val CARD_TO_FOUNDATION = 5
    const val CARD_OFF_FOUNDATION = -5

    /** Net result of a fully completed game: -52 + 52 * 5 = +208. */
    const val WIN_NET = NEW_DEAL + 52 * CARD_TO_FOUNDATION

    /** The current deal's own score: the $52 buy-in plus foundation earnings. */
    fun gameScore(state: GameState): Int =
        NEW_DEAL + CARD_TO_FOUNDATION * state.foundationCardCount
}
