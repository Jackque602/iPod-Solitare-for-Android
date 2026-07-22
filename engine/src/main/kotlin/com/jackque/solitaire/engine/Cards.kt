package com.jackque.solitaire.engine

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The four suits. "Red" suits are hearts and diamonds; on a monochrome
 * display they are rendered with outline/striped glyphs or letters instead
 * of color (see the app's card renderer).
 */
enum class Suit(val letter: Char, val symbol: Char, val isRed: Boolean) {
    SPADES('S', '♠', false),
    HEARTS('H', '♥', true),
    DIAMONDS('D', '♦', true),
    CLUBS('C', '♣', false);

    companion object {
        fun fromLetter(letter: Char): Suit =
            entries.firstOrNull { it.letter == letter.uppercaseChar() }
                ?: throw IllegalArgumentException("Unknown suit letter: $letter")
    }
}

enum class Rank(val value: Int, val letter: Char, val label: String) {
    ACE(1, 'A', "A"),
    TWO(2, '2', "2"),
    THREE(3, '3', "3"),
    FOUR(4, '4', "4"),
    FIVE(5, '5', "5"),
    SIX(6, '6', "6"),
    SEVEN(7, '7', "7"),
    EIGHT(8, '8', "8"),
    NINE(9, '9', "9"),
    TEN(10, 'T', "10"),
    JACK(11, 'J', "J"),
    QUEEN(12, 'Q', "Q"),
    KING(13, 'K', "K");

    companion object {
        fun fromLetter(letter: Char): Rank =
            entries.firstOrNull { it.letter == letter.uppercaseChar() }
                ?: throw IllegalArgumentException("Unknown rank letter: $letter")
    }
}

/**
 * A single playing card. Serialized compactly as a two-character code:
 * rank letter + suit letter. The case of the suit letter carries the
 * face-up flag ("AS" = ace of spades face up, "As" = face down).
 */
@Serializable(with = CardSerializer::class)
data class Card(val rank: Rank, val suit: Suit, val faceUp: Boolean = false) {
    val isRed: Boolean get() = suit.isRed

    fun code(): String {
        val suitChar = if (faceUp) suit.letter.uppercaseChar() else suit.letter.lowercaseChar()
        return "${rank.letter}$suitChar"
    }

    override fun toString(): String = code()

    companion object {
        fun fromCode(code: String): Card {
            require(code.length == 2) { "Card code must be 2 characters: $code" }
            val rank = Rank.fromLetter(code[0])
            val suit = Suit.fromLetter(code[1])
            return Card(rank, suit, faceUp = code[1].isUpperCase())
        }
    }
}

object CardSerializer : KSerializer<Card> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Card", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Card) = encoder.encodeString(value.code())

    override fun deserialize(decoder: Decoder): Card = Card.fromCode(decoder.decodeString())
}
