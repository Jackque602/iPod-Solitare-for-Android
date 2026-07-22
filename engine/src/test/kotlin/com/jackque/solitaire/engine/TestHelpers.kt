package com.jackque.solitaire.engine

/** Build a pile from space-separated card codes, e.g. "KS QH js". */
fun pile(codes: String): List<Card> =
    if (codes.isBlank()) emptyList()
    else codes.trim().split(Regex("\\s+")).map { Card.fromCode(it) }

/** A bare state with the given piles; defaults are all empty. */
fun state(
    stock: List<Card> = emptyList(),
    waste: List<Card> = emptyList(),
    foundations: List<List<Card>> = List(4) { emptyList() },
    tableau: List<List<Card>> = List(7) { emptyList() },
    drawMode: DrawMode = DrawMode.DRAW_ONE,
): GameState = GameState(
    seed = 0L,
    drawMode = drawMode,
    stock = stock,
    waste = waste,
    foundations = foundations,
    tableau = tableau,
)

/**
 * A full 52-card position with every card face up in four valid
 * alternating-color runs (three tableau columns left empty), which makes
 * auto-complete available and the game winnable in exactly 52 moves.
 */
fun allFaceUpFullDeckState(): GameState = state(
    tableau = listOf(
        pile("KS QH JS TH 9S 8H 7S 6H 5S 4H 3S 2H AS"),
        pile("KH QS JH TS 9H 8S 7H 6S 5H 4S 3H 2S AH"),
        pile("KD QC JD TC 9D 8C 7D 6C 5D 4C 3D 2C AD"),
        pile("KC QD JC TD 9C 8D 7C 6D 5C 4D 3C 2D AC"),
        emptyList(),
        emptyList(),
        emptyList(),
    ),
)
