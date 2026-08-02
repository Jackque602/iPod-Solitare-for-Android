package com.jackque.solitaire.engine.achievements

import com.jackque.solitaire.engine.DrawMode
import com.jackque.solitaire.engine.Klondike
import com.jackque.solitaire.engine.Move
import com.jackque.solitaire.engine.SolitaireSession
import com.jackque.solitaire.engine.allFaceUpFullDeckState
import com.jackque.solitaire.engine.pile
import com.jackque.solitaire.engine.state
import com.jackque.solitaire.engine.stats.Statistics
import com.jackque.solitaire.engine.stats.StatisticsCodec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun <T> permutations(items: List<T>): List<List<T>> =
    if (items.size <= 1) listOf(items)
    else items.flatMap { head -> permutations(items - head).map { listOf(head) + it } }

class AchievementsTest {

    private val now = 1_700_000_000_000L

    private fun freshSession() =
        SolitaireSession.start(Statistics(), null, 1L, DrawMode.DRAW_ONE, now)

    // ---------------- king arrangement detection ----------------

    @Test
    fun `king patterns require all four kings face up in the exact columns`() {
        val west = allFaceUpFullDeckState(columns = listOf(0, 1, 2, 3))
        assertEquals(setOf(AchievementId.KINGS_WEST), Achievements.kingPatternsAt(west))

        val east = allFaceUpFullDeckState(columns = listOf(3, 4, 5, 6))
        assertEquals(setOf(AchievementId.KINGS_EAST), Achievements.kingPatternsAt(east))

        val procession = allFaceUpFullDeckState(columns = listOf(0, 2, 4, 6))
        assertEquals(setOf(AchievementId.KINGS_PROCESSION), Achievements.kingPatternsAt(procession))

        val scattered = allFaceUpFullDeckState(columns = listOf(0, 1, 2, 4))
        assertTrue(Achievements.kingPatternsAt(scattered).isEmpty())
    }

    @Test
    fun `face-down or missing kings do not count`() {
        val faceDown = state(
            tableau = List(7) {
                when (it) {
                    0 -> pile("Ks")
                    1 -> pile("KH")
                    2 -> pile("KD")
                    3 -> pile("KC")
                    else -> emptyList()
                }
            },
        )
        assertTrue(Achievements.kingPatternsAt(faceDown).isEmpty(), "face-down king must not count")

        val threeKings = state(
            tableau = List(7) {
                when (it) {
                    0 -> pile("KS")
                    1 -> pile("KH")
                    2 -> pile("KD")
                    else -> emptyList()
                }
            },
            waste = pile("KC"),
        )
        assertTrue(Achievements.kingPatternsAt(threeKings).isEmpty(), "king in the waste must not count")
    }

    @Test
    fun `patterns seen mid-deal are found by replay even if later destroyed`() {
        // Kings at 0,1,2 and 4 with column 3 empty; moving the lone king
        // from 4 to 3 forms WEST, moving it back destroys it again.
        val start = state(
            tableau = List(7) {
                when (it) {
                    0 -> pile("KS")
                    1 -> pile("KH")
                    2 -> pile("KD")
                    4 -> pile("KC")
                    else -> emptyList()
                }
            },
        )
        val toWest = Move.TableauToTableau(4, 0, 3)
        val andBack = Move.TableauToTableau(3, 0, 4)
        assertTrue(Klondike.isLegal(start, toWest))

        val seenStates = mutableListOf(start)
        var current = start
        for (move in listOf(toWest, andBack)) {
            current = Klondike.apply(current, move)!!.state
            seenStates += current
        }
        assertTrue(Achievements.kingPatternsAt(seenStates[1]).contains(AchievementId.KINGS_WEST))
        assertTrue(Achievements.kingPatternsAt(seenStates[2]).isEmpty())
    }

    @Test
    fun `session flags stay a pure function of the surviving move log`() {
        val session = freshSession()
        val rng = Random(99)
        repeat(150) {
            val legal = Klondike.legalMoves(session.state)
            if (legal.isNotEmpty() && rng.nextInt(5) != 0) {
                session.tryMove(legal[rng.nextInt(legal.size)], now)
            } else if (session.canUndo) {
                session.undo()
            }
        }
        assertEquals(
            Achievements.kingPatternsSeen(session.state.seed, session.state.drawMode, session.game.moves),
            session.kingPatternsSeenForTest,
        )
    }

    // ---------------- unlocking on wins ----------------

    @Test
    fun `winning with kings west unlocks exactly that court`() {
        val session = freshSession()
        session.forceStateForTest(allFaceUpFullDeckState(columns = listOf(0, 1, 2, 3)))
        session.autoCompleteAll(now)
        val unlocked = session.stats.achievements.unlocked.keys
        assertTrue(AchievementId.KINGS_WEST in unlocked)
        assertFalse(AchievementId.KINGS_EAST in unlocked)
        assertFalse(AchievementId.KINGS_PROCESSION in unlocked)
        // A clean auto-complete win also never recycled.
        assertTrue(AchievementId.SINGLE_PASS in unlocked)
        assertEquals(
            listOf(AchievementId.SINGLE_PASS, AchievementId.KINGS_WEST).toSet(),
            session.consumeNewlyUnlocked().toSet(),
        )
        assertTrue(session.consumeNewlyUnlocked().isEmpty(), "consume clears the queue")
    }

    @Test
    fun `east and procession unlock from their columns`() {
        val east = freshSession()
        east.forceStateForTest(allFaceUpFullDeckState(columns = listOf(3, 4, 5, 6)))
        east.autoCompleteAll(now)
        assertTrue(AchievementId.KINGS_EAST in east.stats.achievements.unlocked)

        val procession = freshSession()
        procession.forceStateForTest(allFaceUpFullDeckState(columns = listOf(0, 2, 4, 6)))
        procession.autoCompleteAll(now)
        assertTrue(AchievementId.KINGS_PROCESSION in procession.stats.achievements.unlocked)
    }

    @Test
    fun `achievements unlock only once`() {
        val session = freshSession()
        session.forceStateForTest(allFaceUpFullDeckState())
        session.autoCompleteAll(now)
        session.consumeNewlyUnlocked()
        val firstUnlockTime = session.stats.achievements.unlocked[AchievementId.KINGS_WEST]

        session.newDeal(2L, DrawMode.DRAW_ONE, now + 1000)
        session.forceStateForTest(allFaceUpFullDeckState())
        session.autoCompleteAll(now + 2000)
        assertEquals(firstUnlockTime, session.stats.achievements.unlocked[AchievementId.KINGS_WEST])
        assertTrue(session.consumeNewlyUnlocked().isEmpty(), "no re-announcement")
    }

    @Test
    fun `a win after recycling does not earn single pass`() {
        val session = freshSession()
        // Stock empty, QS on the waste, KS alone on the tableau, spades
        // built to the jack: recycle, draw it back, then finish.
        val nearWin = state(
            waste = pile("QS"),
            foundations = listOf(
                pile("AS 2S 3S 4S 5S 6S 7S 8S 9S TS JS"),
                pile("AH 2H 3H 4H 5H 6H 7H 8H 9H TH JH QH KH"),
                pile("AD 2D 3D 4D 5D 6D 7D 8D 9D TD JD QD KD"),
                pile("AC 2C 3C 4C 5C 6C 7C 8C 9C TC JC QC KC"),
            ),
            tableau = List(7) { if (it == 0) pile("KS") else emptyList() },
        )
        session.forceStateForTest(nearWin)
        checkNotNull(session.tryMove(Move.Recycle, now))
        checkNotNull(session.tryMove(Move.Draw, now))
        checkNotNull(session.tryMove(Move.WasteToFoundation(0), now))
        checkNotNull(session.tryMove(Move.TableauToFoundation(0, 0), now))
        assertTrue(session.isWon)
        assertFalse(AchievementId.SINGLE_PASS in session.stats.achievements.unlocked)

        // The same finish without the recycle detour earns it.
        val clean = freshSession()
        clean.forceStateForTest(nearWin)
        checkNotNull(clean.tryMove(Move.WasteToFoundation(0), now))
        checkNotNull(clean.tryMove(Move.TableauToFoundation(0, 0), now))
        assertTrue(clean.isWon)
        assertTrue(AchievementId.SINGLE_PASS in clean.stats.achievements.unlocked)
    }

    @Test
    fun `grand tour needs all 24 foundation arrangements and tracks progress`() {
        val session = freshSession()
        val orders = permutations(listOf(0, 1, 2, 3))
        assertEquals(24, orders.size)
        orders.forEachIndexed { index, order ->
            if (index > 0) session.newDeal(index.toLong(), DrawMode.DRAW_ONE, now)
            session.forceStateForTest(allFaceUpFullDeckState(runOrder = order))
            session.autoCompleteAll(now)
            assertEquals(index + 1, session.stats.achievements.aceConfigurationsSeen.size)
            val unlocked = AchievementId.ACE_GRAND_TOUR in session.stats.achievements.unlocked
            assertEquals(index == 23, unlocked, "grand tour must unlock exactly on the 24th arrangement")
        }
    }

    @Test
    fun `repeating the same arrangement does not add progress`() {
        val session = freshSession()
        session.forceStateForTest(allFaceUpFullDeckState())
        session.autoCompleteAll(now)
        session.newDeal(2L, DrawMode.DRAW_ONE, now)
        session.forceStateForTest(allFaceUpFullDeckState())
        session.autoCompleteAll(now)
        assertEquals(1, session.stats.achievements.aceConfigurationsSeen.size)
    }

    @Test
    fun `abandoned deals unlock nothing`() {
        val session = freshSession()
        session.forceStateForTest(allFaceUpFullDeckState())
        session.tryMove(Klondike.nextAutoCompleteMove(session.state)!!, now)
        session.newDeal(5L, DrawMode.DRAW_ONE, now)
        assertTrue(session.stats.achievements.unlocked.isEmpty())
        assertTrue(session.stats.achievements.aceConfigurationsSeen.isEmpty())
    }

    @Test
    fun `the configuration catalog lists all 24 orders grouped by leading suit`() {
        val all = Achievements.allFoundationConfigurations()
        assertEquals(24, all.size)
        assertEquals(24, all.toSet().size)
        assertTrue(all.all { it.length == 4 && it.toSet() == setOf('S', 'H', 'D', 'C') })
        assertEquals("SHDC", all.first())
        // Grouped: six S-leading, then six H-leading, then D, then C.
        assertEquals(
            listOf('S', 'H', 'D', 'C'),
            all.chunked(6).map { group -> group.map { it.first() }.distinct().single() },
        )
        // Every code a win can produce is present in the catalog.
        val winnable = allFaceUpFullDeckState()
        var current = winnable
        while (!current.isWon) {
            current = Klondike.apply(current, Klondike.nextAutoCompleteMove(current)!!)!!.state
        }
        assertTrue(checkNotNull(Achievements.foundationConfiguration(current)) in all)
    }

    // ---------------- persistence ----------------

    @Test
    fun `achievement state roundtrips through statistics json`() {
        val session = freshSession()
        session.forceStateForTest(allFaceUpFullDeckState())
        session.autoCompleteAll(now)
        val decoded = checkNotNull(StatisticsCodec.decode(StatisticsCodec.encode(session.stats)))
        assertEquals(session.stats.achievements, decoded.achievements)
    }

    @Test
    fun `old statistics files without achievements still decode`() {
        val stats = checkNotNull(StatisticsCodec.decode("""{"version":1,"statistics":{"wins":3}}"""))
        assertEquals(3, stats.wins)
        assertEquals(AchievementsState(), stats.achievements)
    }

    @Test
    fun `a transient king arrangement still counts toward the deal`() {
        val session = freshSession()
        val start = state(
            tableau = List(7) {
                when (it) {
                    0 -> pile("KS")
                    1 -> pile("KH")
                    2 -> pile("KD")
                    4 -> pile("KC")
                    else -> emptyList()
                }
            },
        )
        session.forceStateForTest(start)
        assertTrue(session.kingPatternsSeenForTest.isEmpty())
        // Form Kings' Court West for a moment, then break it up again.
        checkNotNull(session.tryMove(Move.TableauToTableau(4, 0, 3), now))
        checkNotNull(session.tryMove(Move.TableauToTableau(3, 0, 4), now))
        assertTrue(
            AchievementId.KINGS_WEST in session.kingPatternsSeenForTest,
            "the arrangement existed during the deal, so it counts",
        )
    }
}
