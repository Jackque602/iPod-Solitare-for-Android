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
 *  - OUTLINE: red suit symbols are hollow outlines, black suits are solid
 *  - LETTERS: S/H/D/C letters, red ones hollow
 *  - FILLED:  every suit solid (for regular LCDs)
 */
object CardDesign {
    val corner = RoundedCornerShape(8)
}

private fun suitText(suit: Suit, style: SuitStyle): String = when (style) {
    SuitStyle.LETTERS -> suit.letter.toString()
    else -> suit.symbol.toString()
}

/** Hollow glyphs for red suits in OUTLINE/LETTERS styles. */
private fun isHollow(suit: Suit, style: SuitStyle): Boolean =
    style != SuitStyle.FILLED && suit.isRed

@Composable
private fun SuitGlyph(suit: Suit, style: SuitStyle, size: TextUnit, bold: Boolean = true) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    if (isHollow(suit, style)) {
        Text(
            text = suitText(suit, style),
            style = TextStyle(
                color = Color.Black,
                fontSize = size,
                fontWeight = weight,
                drawStyle = Stroke(width = 2.5f),
            ),
            maxLines = 1,
        )
    } else {
        Text(
            text = suitText(suit, style),
            color = Color.Black,
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
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(CardDesign.corner)
            .background(Color.White)
            .border(1.5.dp, Color.Black, CardDesign.corner),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 3.dp, top = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = card.rank.label,
                color = Color.Black,
                fontSize = rankSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            SuitGlyph(card.suit, suitStyle, smallSuit)
        }
        Box(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp),
        ) {
            SuitGlyph(card.suit, suitStyle, bigSuit)
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
