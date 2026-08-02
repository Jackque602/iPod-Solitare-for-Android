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

/** The four full king-to-ace alternating runs used to build winnable states. */
val fullDeckRuns: List<List<Card>> = listOf(
    pile("KS QH JS TH 9S 8H 7S 6H 5S 4H 3S 2H AS"),
    pile("KH QS JH TS 9H 8S 7H 6S 5H 4S 3H 2S AH"),
    pile("KD QC JD TC 9D 8C 7D 6C 5D 4C 3D 2C AD"),
    pile("KC QD JC TD 9C 8D 7C 6D 5C 4D 3C 2D AC"),
)

/**
 * A full 52-card position with every card face up in four valid
 * alternating-color runs (the other tableau columns left empty), which
 * makes auto-complete available and the game winnable in exactly 52
 * moves. [columns] chooses where the four runs sit; [runOrder] permutes
 * which run goes to which column (controls the final foundation order).
 */
fun allFaceUpFullDeckState(
    columns: List<Int> = listOf(0, 1, 2, 3),
    runOrder: List<Int> = listOf(0, 1, 2, 3),
): GameState {
    require(columns.size == 4 && columns.toSet().size == 4)
    require(runOrder.toSortedSet() == sortedSetOf(0, 1, 2, 3))
    val tableau = MutableList<List<Card>>(7) { emptyList() }
    columns.forEachIndexed { i, col -> tableau[col] = fullDeckRuns[runOrder[i]] }
    return state(tableau = tableau)
}
