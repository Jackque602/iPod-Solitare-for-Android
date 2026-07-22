package com.jackque.solitaire.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jackque.solitaire.engine.Card
import com.jackque.solitaire.engine.Suit
import com.jackque.solitaire.engine.settings.SuitStyle

/**
 * All card artwork is original and drawn with plain shapes and text for a
 * 1-bit monochrome display. "Red" suits (hearts/diamonds) are distinguished
 * without color, in the player's choice of style:
 *  - INVERTED: the whole red card is black with white markings - survives
 *    1-bit e-ink at any size, default
 *  - OUTLINE: red suit symbols are hollow outlines
 *  - LETTERS: S/H/D/C letters, red ones on a white-on-black badge
 *  - FILLED:  every suit solid (for regular LCDs)
 */
object CardDesign {
    val corner = RoundedCornerShape(8)
}

private fun suitText(suit: Suit, style: SuitStyle): String = when (style) {
    SuitStyle.LETTERS -> suit.letter.toString()
    else -> suit.symbol.toString()
}

/** White-on-black letter badge for red suits in the LETTERS style. */
private fun isBadged(suit: Suit, style: SuitStyle): Boolean =
    style == SuitStyle.LETTERS && suit.isRed

/** Hollow glyphs for red suits in the OUTLINE style. */
private fun isHollow(suit: Suit, style: SuitStyle): Boolean =
    style == SuitStyle.OUTLINE && suit.isRed

@Composable
private fun SuitGlyph(
    suit: Suit,
    style: SuitStyle,
    size: TextUnit,
    ink: Color = Color.Black,
    bold: Boolean = true,
) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    val text = suitText(suit, style)
    when {
        isBadged(suit, style) -> {
            Box(
                modifier = Modifier
                    .background(Color.Black, RoundedCornerShape(25))
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = size,
                    fontWeight = weight,
                    maxLines = 1,
                )
            }
        }
        isHollow(suit, style) -> {
            // Stroke width scales with the glyph so the outline stays open
            // instead of filling in at small sizes.
            val strokeWidth = with(LocalDensity.current) { size.toPx() / 9f }
            Text(
                text = text,
                style = TextStyle(
                    color = ink,
                    fontSize = size,
                    fontWeight = weight,
                    drawStyle = Stroke(width = strokeWidth),
                ),
                maxLines = 1,
            )
        }
        else -> Text(
            text = text,
            color = ink,
            fontSize = size,
            fontWeight = weight,
            maxLines = 1,
        )
    }
}

@Composable
fun CardFace(
    card: Card,
    width: Dp,
    height: Dp,
    suitStyle: SuitStyle,
    modifier: Modifier = Modifier,
) {
    val rankSize = (width.value * 0.34f).sp
    val smallSuit = (width.value * 0.30f).sp
    val bigSuit = (width.value * 0.52f).sp
    // In the INVERTED style the entire red card face is filled black with
    // white markings. A thin white rim stays between the fill and the card
    // border so stacked cards and the black cursor outline remain visible.
    val invertedCard = suitStyle == SuitStyle.INVERTED && card.isRed
    val ink = if (invertedCard) Color.White else Color.Black
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(CardDesign.corner)
            .background(Color.White)
            .border(1.5.dp, Color.Black, CardDesign.corner),
    ) {
        if (invertedCard) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.5.dp)
                    .clip(RoundedCornerShape(15))
                    .background(Color.Black),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = card.rank.label,
                color = ink,
                fontSize = rankSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            SuitGlyph(card.suit, suitStyle, smallSuit, ink)
        }
        Box(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 3.dp),
        ) {
            SuitGlyph(card.suit, suitStyle, bigSuit, ink)
        }
    }
}

/** Face-down card: a diagonal-hatch pattern, readable at 1-bit depth. */
@Composable
fun CardBack(width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(CardDesign.corner)
            .background(Color.White)
            .border(1.5.dp, Color.Black, CardDesign.corner),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val step = 7.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(
                    color = Color.Black,
                    start = Offset(x, size.height),
                    end = Offset(x + size.height, 0f),
                    strokeWidth = 1.2.dp.toPx(),
                )
                x += step
            }
        }
    }
}

/** Empty pile slot: a thin dashed-look outline with an optional label. */
@Composable
fun EmptySlot(
    width: Dp,
    height: Dp,
    label: String = "",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(CardDesign.corner)
            .background(Color.White)
            .border(1.dp, Color.Black, CardDesign.corner),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = Color.Black,
                fontSize = (width.value * 0.4f).sp,
                fontWeight = FontWeight.Light,
            )
        }
    }
}
