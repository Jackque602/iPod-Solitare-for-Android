package com.jackque.solitaire.engine.input

import com.jackque.solitaire.engine.Dealer
import com.jackque.solitaire.engine.DrawMode
import com.jackque.solitaire.engine.Klondike
import com.jackque.solitaire.engine.Move
import com.jackque.solitaire.engine.pile
import com.jackque.solitaire.engine.state
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigatorTest {

    private val dealt = Dealer.deal(1L, DrawMode.DRAW_ONE)

    @Test
    fun `horizontal movement wraps within each row`() {
        // Top row: foundations 0-3, then stock, then waste.
        var c = Cursor(Zone.FOUNDATION, 0)
        repeat(3) { c = Navigator.move(dealt, c, Direction.RIGHT) }
        assertEquals(Cursor(Zone.FOUNDATION, 3), c)
        c = Navigator.move(dealt, c, Direction.RIGHT)
        assertEquals(Zone.STOCK, c.zone)
        c = Navigator.move(dealt, c, Direction.RIGHT)
        assertEquals(Zone.WASTE, c.zone)
        c = Navigator.move(dealt, c, Direction.RIGHT)
        assertEquals(Cursor(Zone.FOUNDATION, 0), c, "wraps back to the first foundation")
        c = Navigator.move(dealt, c, Direction.LEFT)
        assertEquals(Zone.WASTE, c.zone)
        c = Navigator.move(dealt, c, Direction.LEFT)
        assertEquals(Zone.STOCK, c.zone)

        var t = Cursor(Zone.TABLEAU, 6)
        t = Navigator.move(dealt, t, Direction.RIGHT)
        assertEquals(Cursor(Zone.TABLEAU, 0), t)
    }

    @Test
    fun `vertical movement keeps columns aligned`() {
        assertEquals(
            Cursor(Zone.TABLEAU, 5),
            Navigator.move(dealt, Cursor(Zone.STOCK), Direction.DOWN),
        )
        assertEquals(
            Cursor(Zone.TABLEAU, 6),
            Navigator.move(dealt, Cursor(Zone.WASTE), Direction.DOWN),
        )
        assertEquals(
            Cursor(Zone.TABLEAU, 2),
            Navigator.move(dealt, Cursor(Zone.FOUNDATION, 2), Direction.DOWN),
        )
        assertEquals(
            Cursor(Zone.FOUNDATION, 0),
            Navigator.move(dealt, Cursor(Zone.TABLEAU, 0), Direction.UP),
        )
        assertEquals(
            Cursor(Zone.STOCK),
            Navigator.move(dealt, Cursor(Zone.TABLEAU, 4), Direction.UP),
        )
        assertEquals(
            Cursor(Zone.STOCK),
            Navigator.move(dealt, Cursor(Zone.TABLEAU, 5), Direction.UP),
        )
        assertEquals(
            Cursor(Zone.WASTE),
            Navigator.move(dealt, Cursor(Zone.TABLEAU, 6), Direction.UP),
        )
    }

    @Test
    fun `up extends the selected run before leaving the tableau`() {
        val s = state(
            tableau = List(7) {
                when (it) {
                    3 -> pile("2c 9H 8S 7D")
                    else -> pile("KS")
                }
            },
        )
        var c = Cursor(Zone.TABLEAU, 3)
        assertEquals(2, Navigator.maxDepth(s, 3))
        c = Navigator.move(s, c, Direction.UP)
        assertEquals(1, c.depth)
        c = Navigator.move(s, c, Direction.UP)
        assertEquals(2, c.depth)
        c = Navigator.move(s, c, Direction.UP)
        assertEquals(Zone.FOUNDATION, c.zone, "past the run top the cursor leaves the tableau")

        var down = Cursor(Zone.TABLEAU, 3, depth = 2)
        down = Navigator.move(s, down, Direction.DOWN)
        assertEquals(1, down.depth)
    }

    @Test
    fun `face-down cards limit the movable run`() {
        val s = state(tableau = List(7) { if (it == 0) pile("2c 5h 8S 7D") else emptyList() })
        assertEquals(2, Navigator.faceUpRunLength(s, 0))
        // 5h is face down: only 8S 7D can be picked up.
        assertEquals(1, Navigator.maxDepth(s, 0))
    }

    @Test
    fun `broken sequences limit the movable run`() {
        val s = state(tableau = List(7) { if (it == 0) pile("9H 8S 4D") else emptyList() })
        // 4D on 8S is not a run; only the 4D can move.
        assertEquals(1, Navigator.faceUpRunLength(s, 0))
    }

    @Test
    fun `activating the stock draws or recycles`() {
        assertEquals(Move.Draw, Navigator.activate(dealt, Cursor(Zone.STOCK), null).move)

        val empty = state(waste = pile("2S"))
        assertEquals(Move.Recycle, Navigator.activate(empty, Cursor(Zone.STOCK), null).move)

        val nothing = state()
        assertNull(Navigator.activate(nothing, Cursor(Zone.STOCK), null).move)
    }

    @Test
    fun `select then drop plays a legal move`() {
        val s = state(
            waste = pile("8H"),
            tableau = List(7) { if (it == 0) pile("9C") else emptyList() },
        )
        val picked = Navigator.activate(s, Cursor(Zone.WASTE), null)
        val selection = assertNotNull(picked.selection)
        val dropped = Navigator.activate(s, Cursor(Zone.TABLEAU, 0), selection)
        assertEquals(Move.WasteToTableau(0), dropped.move)
        assertTrue(Klondike.isLegal(s, dropped.move!!))
    }

    @Test
    fun `activating the source again clears the selection`() {
        val s = state(waste = pile("8H"))
        val selection = assertNotNull(Navigator.activate(s, Cursor(Zone.WASTE), null).selection)
        val again = Navigator.activate(s, Cursor(Zone.WASTE), selection)
        assertNull(again.move)
        assertNull(again.selection)
    }

    @Test
    fun `illegal drop on another pile reselects it instead`() {
        val s = state(
            waste = pile("8H"),
            tableau = List(7) { if (it == 0) pile("TC") else emptyList() },
        )
        val selection = assertNotNull(Navigator.activate(s, Cursor(Zone.WASTE), null).selection)
        val result = Navigator.activate(s, Cursor(Zone.TABLEAU, 0), selection)
        assertNull(result.move)
        assertEquals(Selection(Zone.TABLEAU, 0, 0), result.selection)
    }

    @Test
    fun `depth selection picks up a whole run`() {
        val s = state(
            tableau = List(7) {
                when (it) {
                    0 -> pile("9H 8S 7D")
                    1 -> pile("TC")
                    else -> emptyList()
                }
            },
        )
        val cursor = Cursor(Zone.TABLEAU, 0, depth = 2)
        val selection = assertNotNull(Navigator.activate(s, cursor, null).selection)
        assertEquals(0, selection.cardIndex, "depth 2 selects down to the 9H")
        val drop = Navigator.activate(s, Cursor(Zone.TABLEAU, 1), selection)
        assertEquals(Move.TableauToTableau(0, 0, 1), drop.move)
    }

    @Test
    fun `auto foundation shortcut finds a home for the top card`() {
        val s = state(
            waste = pile("AH"),
            tableau = List(7) { if (it == 0) pile("AS") else emptyList() },
        )
        assertEquals(Move.WasteToFoundation(0), Navigator.autoFoundationMove(s, Cursor(Zone.WASTE)))
        assertEquals(Move.TableauToFoundation(0, 0), Navigator.autoFoundationMove(s, Cursor(Zone.TABLEAU, 0)))
        assertNull(Navigator.autoFoundationMove(s, Cursor(Zone.TABLEAU, 1)))
    }

    @Test
    fun `clamp fixes a cursor whose column shrank`() {
        val s = state(tableau = List(7) { if (it == 0) pile("7D") else emptyList() })
        assertEquals(
            Cursor(Zone.TABLEAU, 0, depth = 0),
            Navigator.clamp(s, Cursor(Zone.TABLEAU, 0, depth = 2)),
        )
    }
}
