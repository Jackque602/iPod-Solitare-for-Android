package com.jackque.solitaire.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Strict monochrome theme for e-ink: pure black on pure white, no tonal
 * surfaces, no elevation tints, and (throughout the app) no ripples and no
 * animations - composables use [tap] which draws no indication.
 */
private val MonochromeColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    secondary = Color.Black,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color.Black,
    outline = Color.Black,
    error = Color.Black,
    onError = Color.White,
)

@Composable
fun SolitaireTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MonochromeColors, content = content)
}

/** Click without ripple or any other animated indication (e-ink friendly). */
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = null,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
)

/**
 * The app's standard button: black border, white fill, large touch target.
 * Focus (from the hardware keyboard) is shown by a thicker border.
 */
@Composable
fun EButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: TextUnit = 15.sp,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .border(1.5.dp, if (enabled) Color.Black else Color.Gray)
            .tap(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) Color.Black else Color.Gray,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** Uniform border width used for the keyboard cursor. */
val CursorBorder: Dp = 3.dp
