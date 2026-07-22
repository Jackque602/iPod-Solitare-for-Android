package com.jackque.solitaire.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KlondikeRulesTest {

    // ---------------- placement rules ----------------

    @Test
    fun `only kings may occupy an empty tableau column`() {
        assertTrue(Klondike.canPlaceOnTableau(Card.fromCode("KS"), emptyList()))
        assertFalse(Klondike.canPlaceOnTableau(Card.fromCode("QS"), emptyList()))
        assertFalse(Klondike.canPlaceOnTableau(Card.fromCode("AS"), emptyList()))
    }

    @Test
    fun `tableau stacking must descend and alternate colors`() {
        val blackNine = pile("9S")
        assertTrue(Klondike.canPlaceOnTableau(Card.fromCode("8H"), blackNine))
        assertTrue(Klondike.canPlaceOnTableau(Card.fromCode("8D"), blackNine))
        assertFalse(Klondike.canPlaceOnTableau(Card.fromCode("8S"), blackNine), "same color")
        assertFalse(Klondike.canPlaceOnTableau(Card.fromCode("8C"), blackNine), "same color")
        assertFalse(Klondike.canPlaceOnTableau(Card.fromCode("7H"), blackNine), "skips a rank")
        assertFalse(Klondike.canPlaceOnTableau(Card.fromCode("TH"), blackNine), "ascends")
    }

    @Test
    fun `cards cannot be placed on a face-down card`() {
        assertFalse(Klondike.canPlaceOnTableau(Card.fromCode("8H"), pile("9s")))
    }

    @Test
    fun `foundations build ace to king in a single suit`() {
        assertTrue(Klondike.canPlaceOnFoundation(Card.fromCode("AH"), emptyList()))
        assertFalse(Klondike.canPlaceOnFoundation(Card.fromCode("2H"), emptyList()))
        assertTrue(Klondike.canPlaceOnFoundation(Card.fromCode("2H"), pile("AH")))
        assertFalse(Klondike.canPlaceOnFoundation(Card.fromCode("2S"), pile("AH")), "wrong suit")
        assertFalse(Klondike.canPlaceOnFoundation(Card.fromCode("3H"), pile("AH")), "skips a rank")
    }

    // ---------------- draw & recycle ----------------

    @Test
    fun `draw one moves a single card to the waste face up`() {
        val s = state(stock = pile("2s 3s 4s"), drawMode = DrawMode.DRAW_ONE)
        val result = assertNotNull(Klondike.apply(s, Move.Draw))
        assertEquals(pile("2s 3s"), result.state.stock)
        assertEquals(pile("4S"), result.state.waste)
        assertEquals(1, result.state.moveCount)
        assertEquals(0, result.scoreDelta)
    }

    @Test
    fun `draw three moves up to three cards keeping draw order`() {
        val s = state(stock = pile("2s 3s 4s 5s"), drawMode = DrawMode.DRAW_THREE)
        val result = assertNotNull(Klondike.apply(s, Move.Draw))
        assertEquals(pile("2s"), result.state.stock)
        // 5s was on top of the stock so it is drawn first and buried deepest.
        assertEquals(pile("5S 4S 3S"), result.state.waste)

        val second = assertNotNull(Klondike.apply(result.state, Move.Draw))
        assertEquals(pile("5S 4S 3S 2S"), second.state.waste)
        assertTrue(second.state.stock.isEmpty())
    }

    @Test
    fun `draw from empty stock is illegal`() {
        assertNull(Klondike.apply(state(waste = pile("2S")), Move.Draw))
    }

    @Test
    fun `recycle flips the waste over preserving cycle order`() {
        val s = state(stock = pile("2s 3s 4s"), drawMode = DrawMode.DRAW_ONE)
        var current = s
        val firstPass = mutableListOf<Card>()
        repeat(3) {
            current = Klondike.apply(current, Move.Draw)!!.state
            firstPass += current.waste.last()
        }
        current = Klondike.apply(current, Move.Recycle)!!.state
        assertTrue(current.waste.isEmpty())
        assertEquals(3, current.stock.size)
        assertTrue(current.stock.all { !it.faceUp })
        val secondPass = mutableListOf<Card>()
        repeat(3) {
            current = Klondike.apply(current, Move.Draw)!!.state
            secondPass += current.waste.last()
        }
        assertEquals(firstPass, secondPass, "cards must cycle in the same order every pass")
    }

    @Test
    fun `recycle is illegal while stock remains or waste is empty`() {
        assertNull(Klondike.apply(state(stock = pile("2s"), waste = pile("3S")), Move.Recycle))
        assertNull(Klondike.apply(state(), Move.Recycle))
    }

    // ---------------- pile-to-pile moves ----------------

    @Test
    fun `waste to tableau and foundation only use the top card`() {
        val s = state(
            waste = pile("9S 8H"),
            tableau = List(7) { if (it == 0) pile("9C") else emptyList() },
        )
        val result = assertNotNull(Klondike.apply(s, Move.WasteToTableau(0)))
        assertEquals(pile("9S"), result.state.waste)
        assertEquals(pile("9C 8H"), result.state.tableau[0])

        // 9S under the top of the waste can never be played directly.
        assertNull(Klondike.apply(s, Move.WasteToTableau(1)))
    }

    @Test
    fun `moving a tableau card flips the revealed card`() {
        val s = state(
            tableau = List(7) {
                when (it) {
                    0 -> pile("2c 8H")
                    1 -> pile("9C")
                    else -> emptyList()
                }
            },
        )
        val result = assertNotNull(Klondike.apply(s, Move.TableauToTableau(0, 1, 1)))
        assertTrue(result.flippedCard)
        assertEquals(pile("2C"), result.state.tableau[0])
        assertEquals(pile("9C 8H"), result.state.tableau[1])
    }

    @Test
    fun `a multi-card run moves as a unit`() {
        val s = state(
            tableau = List(7) {
                when (it) {
                    0 -> pile("3d TS 9H 8S")
                    1 -> pile("JH")
                    else -> emptyList()
                }
            },
        )
        val result = assertNotNull(Klondike.apply(s, Move.TableauToTableau(0, 1, 1)))
        assertEquals(pile("3D"), result.state.tableau[0])
        assertEquals(pile("JH TS 9H 8S"), result.state.tableau[1])
        assertTrue(result.flippedCard)
    }

    @Test
    fun `face-down cards cannot be moved`() {
        val s = state(
            tableau = List(7) {
                when (it) {
                    0 -> pile("9h 8S")
                    1 -> pile("TS")
                    else -> emptyList()
                }
            },
        )
        assertNull(Klondike.apply(s, Move.TableauToTableau(0, 0, 1)))
    }

    @Test
    fun `moving to the same column is illegal`() {
        val s = state(tableau = List(7) { if (it == 0) pile("KS") else emptyList() })
        assertNull(Klondike.apply(s, Move.TableauToTableau(0, 0, 0)))
    }

    @Test
    fun `foundation to tableau returns the card into play`() {
        val s = state(
            foundations = listOf(pile("AS 2S"), emptyList(), emptyList(), emptyList()),
            tableau = List(7) { if (it == 0) pile("3H") else emptyList() },
        )
        val result = assertNotNull(Klondike.apply(s, Move.FoundationToTableau(0, 0)))
        assertEquals(pile("AS"), result.state.foundations[0])
        assertEquals(pile("3H 2S"), result.state.tableau[0])
        assertEquals(Scoring.CARD_OFF_FOUNDATION, result.scoreDelta)
    }

    @Test
    fun `out of range indices are rejected not crashing`() {
        val s = Dealer.deal(1L, DrawMode.DRAW_ONE)
        assertNull(Klondike.apply(s, Move.TableauToFoundation(7, 0)))
        assertNull(Klondike.apply(s, Move.TableauToFoundation(0, 4)))
        assertNull(Klondike.apply(s, Move.TableauToTableau(0, 99, 1)))
        assertNull(Klondike.apply(s, Move.FoundationToTableau(-1, 0)))
        assertNull(Klondike.apply(s, Move.WasteToTableau(-1)))
    }

    @Test
    fun `win is detected on the final foundation move`() {
        val foundations = listOf(
            pile("AS 2S 3S 4S 5S 6S 7S 8S 9S TS JS QS KS"),
            pile("AH 2H 3H 4H 5H 6H 7H 8H 9H TH JH QH KH"),
            pile("AD 2D 3D 4D 5D 6D 7D 8D 9D TD JD QD KD"),
            pile("AC 2C 3C 4C 5C 6C 7C 8C 9C TC JC QC"),
        )
        val s = state(
            foundations = foundations,
            tableau = List(7) { if (it == 0) pile("KC") else emptyList() },
        )
        val result = assertNotNull(Klondike.apply(s, Move.TableauToFoundation(0, 3)))
        assertTrue(result.won)
        assertTrue(result.state.isWon)
    }
}
