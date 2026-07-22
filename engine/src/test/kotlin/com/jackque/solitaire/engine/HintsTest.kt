package com.jackque.solitaire.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HintsTest {

    @Test
    fun `foundation moves are suggested first`() {
        val s = state(
            waste = pile("AH"),
            stock = pile("2s"),
            tableau = List(7) { if (it == 0) pile("9C 8H") else emptyList() },
        )
        val hints = Klondike.hints(s)
        assertTrue(hints.isNotEmpty())
        assertEquals(Move.WasteToFoundation(0), hints.first())
    }

    @Test
    fun `moves that reveal hidden cards outrank waste plays`() {
        val s = state(
            waste = pile("7S"),
            tableau = List(7) {
                when (it) {
                    0 -> pile("2c 8H")   // moving 8H reveals the hidden 2c
                    1 -> pile("9C")
                    2 -> pile("8D")      // 7S from waste could go here
                    else -> emptyList()
                }
            },
        )
        val hints = Klondike.hints(s)
        val revealing = hints.indexOf(Move.TableauToTableau(0, 1, 1))
        val wastePlay = hints.indexOf(Move.WasteToTableau(2))
        assertTrue(revealing >= 0 && wastePlay >= 0)
        assertTrue(revealing < wastePlay, "revealing move must be ranked above waste play")
    }

    @Test
    fun `pointless full-column shuffles are not suggested`() {
        val s = state(
            tableau = List(7) {
                when (it) {
                    0 -> pile("8H")
                    1 -> pile("9C")
                    2 -> pile("9S")
                    else -> emptyList()
                }
            },
        )
        // 8H could legally move onto either black nine, but it reveals
        // nothing and frees no column that hides anything.
        val hints = Klondike.hints(s)
        assertTrue(hints.none { it is Move.TableauToTableau })
    }

    @Test
    fun `draw is suggested when nothing else is productive`() {
        val s = state(stock = pile("2s 9d"), tableau = List(7) { if (it == 0) pile("KS") else emptyList() })
        assertEquals(listOf<Move>(Move.Draw), Klondike.hints(s))
    }

    @Test
    fun `a completely stuck game yields no hints`() {
        val s = state(
            tableau = listOf(
                pile("KS"), pile("KC"), pile("QS"), pile("QC"),
                pile("JS"), pile("JC"), pile("TS"),
            ),
        )
        assertTrue(Klondike.hints(s).isEmpty())
        assertTrue(Klondike.legalMoves(s).isEmpty())
    }
}
