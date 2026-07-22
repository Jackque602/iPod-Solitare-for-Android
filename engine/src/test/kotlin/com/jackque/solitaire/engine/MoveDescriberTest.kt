package com.jackque.solitaire.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class MoveDescriberTest {

    @Test
    fun `describes the moving card and its destination`() {
        val s = state(
            waste = pile("8H"),
            foundations = listOf(pile("AS"), emptyList(), emptyList(), emptyList()),
            tableau = List(7) { if (it == 0) pile("9C 8D") else emptyList() },
        )
        assertEquals("Draw from the stock", MoveDescriber.describe(s, Move.Draw))
        assertEquals("Turn the waste over", MoveDescriber.describe(s, Move.Recycle))
        assertEquals("8♥ to the foundation", MoveDescriber.describe(s, Move.WasteToFoundation(0)))
        assertEquals("8♥ to column 3", MoveDescriber.describe(s, Move.WasteToTableau(2)))
        assertEquals("8♦ to the foundation", MoveDescriber.describe(s, Move.TableauToFoundation(0, 1)))
        assertEquals("A♠ back to column 1", MoveDescriber.describe(s, Move.FoundationToTableau(0, 0)))
        assertEquals("8♦ to column 2", MoveDescriber.describe(s, Move.TableauToTableau(0, 1, 1)))
    }
}
