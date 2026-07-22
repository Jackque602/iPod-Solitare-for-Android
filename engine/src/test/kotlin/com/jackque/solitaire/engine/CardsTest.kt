package com.jackque.solitaire.engine

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardsTest {

    @Test
    fun `standard deck has 52 unique cards`() {
        val deck = Dealer.standardDeck()
        assertEquals(52, deck.size)
        assertEquals(52, deck.map { it.rank to it.suit }.toSet().size)
    }

    @Test
    fun `card code roundtrips for every card both faces`() {
        for (suit in Suit.entries) for (rank in Rank.entries) for (faceUp in listOf(true, false)) {
            val card = Card(rank, suit, faceUp)
            assertEquals(card, Card.fromCode(card.code()))
        }
    }

    @Test
    fun `case of suit letter encodes face`() {
        assertTrue(Card.fromCode("AS").faceUp)
        assertFalse(Card.fromCode("As").faceUp)
        assertEquals(Rank.TEN, Card.fromCode("Th").rank)
        assertEquals(Suit.HEARTS, Card.fromCode("Th").suit)
    }

    @Test
    fun `invalid codes are rejected`() {
        assertFailsWith<IllegalArgumentException> { Card.fromCode("A") }
        assertFailsWith<IllegalArgumentException> { Card.fromCode("XS") }
        assertFailsWith<IllegalArgumentException> { Card.fromCode("AX") }
    }

    @Test
    fun `json serialization uses compact codes`() {
        // Rank letters are canonically uppercase; the suit letter's case
        // carries the face-up flag.
        val json = Json.encodeToString(pile("AS kh"))
        assertEquals("""["AS","Kh"]""", json)
        assertEquals(pile("AS kh"), Json.decodeFromString<List<Card>>(json))
    }

    @Test
    fun `red suits are hearts and diamonds`() {
        assertTrue(Suit.HEARTS.isRed)
        assertTrue(Suit.DIAMONDS.isRed)
        assertFalse(Suit.SPADES.isRed)
        assertFalse(Suit.CLUBS.isRed)
    }
}
