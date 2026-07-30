@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.jackque.solitaire.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jackque.solitaire.DialogState
import com.jackque.solitaire.GameViewModel
import com.jackque.solitaire.UiState
import com.jackque.solitaire.engine.Card
import com.jackque.solitaire.engine.GameState
import com.jackque.solitaire.engine.input.Cursor
import com.jackque.solitaire.engine.input.Selection
import com.jackque.solitaire.engine.input.Zone
import com.jackque.solitaire.ui.theme.EButton
import com.jackque.solitaire.ui.theme.tap
import kotlinx.coroutines.delay

fun moneyText(value: Long): String = if (value < 0) "-$${-value}" else "$$value"

internal fun keyNameOf(event: KeyEvent): String =
    android.view.KeyEvent.keyCodeToString(event.nativeKeyEvent.keyCode).removePrefix("KEYCODE_")

@Composable
fun SolitaireApp(vm: GameViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    when (val s = ui) {
        is UiState.Loading -> Box(Modifier.fillMaxSize().background(Color.White))
        is UiState.Game -> {
            GameScreen(s, vm)
            AppDialogs(s, vm)
            FullRefreshOverlay(s.refreshPulse)
        }
    }
}

@Composable
private fun GameScreen(s: UiState.Game, vm: GameViewModel) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(s.dialog) {
        if (s.dialog == null) focusRequester.requestFocus()
    }
    // The system back button/gesture first drops an active selection,
    // then undoes moves one at a time; with nothing left to undo it
    // falls through to the system and leaves the app.
    BackHandler(enabled = s.selection != null || s.canUndo) {
        if (s.selection != null) vm.onCancelSelection() else vm.onUndo()
    }

    // safeDrawing keeps the board clear of system bars and display
    // cutouts - e.g. the camera lenses that intrude into the Motorola
    // Razr cover display.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) vm.onKey(keyNameOf(event)) else false
            },
    ) {
        when (layoutModeFor(maxWidth.value, maxHeight.value)) {
            LayoutMode.TALL -> TallLayout(s, vm)
            LayoutMode.COMPACT -> CompactLayout(s, vm)
        }
    }
}

/** Classic portrait phone layout. */
@Composable
private fun TallLayout(s: UiState.Game, vm: GameViewModel) {
    Column(Modifier.fillMaxSize()) {
        StatusBar(s, vm)
        MessageLine(s.statusMessage)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 6.dp),
        ) {
            val gap = 4.dp
            val cardW = (maxWidth - gap * 6) / 7
            val cardH = cardW * 1.42f
            val tableauAvailable = maxHeight - cardH - 10.dp
            Column {
                TopRow(s, vm, cardW, cardH, gap)
                Spacer(Modifier.height(10.dp))
                BoardColumns(s, vm, cardW, cardH, gap, tableauAvailable)
            }
        }
        BottomBar(s, vm)
    }
}

/**
 * Near-square/wide screens (Motorola Razr cover display, landscape
 * phones): one slim status line, the board sized by height as well as
 * width, and the action buttons in a vertical rail on the right.
 */
@Composable
private fun CompactLayout(s: UiState.Game, vm: GameViewModel) {
    Column(Modifier.fillMaxSize()) {
        CompactStatusBar(s)
        Row(Modifier.fillMaxWidth().weight(1f)) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp),
            ) {
                val gap = 3.dp
                val cardWByWidth = (maxWidth - gap * 6) / 7
                val cardHByHeight = maxHeight * 0.27f
                val cardH = min(cardWByWidth * 1.42f, cardHByHeight)
                val cardW = cardH / 1.42f
                val tableauAvailable = maxHeight - cardH - 6.dp
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column {
                        TopRow(s, vm, cardW, cardH, gap)
                        Spacer(Modifier.height(6.dp))
                        BoardColumns(s, vm, cardW, cardH, gap, tableauAvailable)
                    }
                }
            }
            ActionRail(s, vm)
        }
    }
}

@Composable
private fun BoardColumns(
    s: UiState.Game,
    vm: GameViewModel,
    cardW: Dp,
    cardH: Dp,
    gap: Dp,
    tableauAvailable: Dp,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        for (col in 0..6) {
            TableauColumn(col, s, vm, cardW, cardH, tableauAvailable)
        }
    }
}

/** One-line chrome for compact screens; the menu button lives in the rail. */
@Composable
private fun CompactStatusBar(s: UiState.Game) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = moneyText(s.bank),
            modifier = Modifier.testTag("bank"),
            color = Color.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = s.statusMessage ?: buildString {
                append("Moves ${s.state.moveCount}")
                if (s.timerText.isNotEmpty()) append("  ·  ${s.timerText}")
            },
            modifier = Modifier.testTag("message"),
            color = Color.Black,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun ActionRail(s: UiState.Game, vm: GameViewModel) {
    Column(
        modifier = Modifier
            .width(84.dp)
            .fillMaxHeight()
            .padding(start = 2.dp, end = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        EButton("Menu", Modifier.fillMaxWidth().testTag("menu"), fontSize = 13.sp) {
            vm.openDialog(DialogState.Menu)
        }
        EButton(
            "Undo",
            Modifier.fillMaxWidth().testTag("btn_undo"),
            enabled = s.canUndo,
            fontSize = 13.sp,
            onClick = vm::onUndo,
        )
        EButton("Hint", Modifier.fillMaxWidth().testTag("btn_hint"), fontSize = 13.sp, onClick = vm::onHint)
        if (s.autoCompleteAvailable) {
            EButton("Auto", Modifier.fillMaxWidth().testTag("btn_auto"), fontSize = 13.sp, onClick = vm::onAutoComplete)
        } else {
            EButton(
                "Draw",
                Modifier.fillMaxWidth().testTag("btn_draw"),
                enabled = s.state.stock.isNotEmpty() || s.state.waste.isNotEmpty(),
                fontSize = 13.sp,
                onClick = vm::onDraw,
            )
        }
        if (s.settings.eInkMode) {
            EButton("Refresh", Modifier.fillMaxWidth().testTag("btn_refresh"), fontSize = 13.sp, onClick = vm::onFullRefresh)
        }
    }
}

// ----------------------------------------------------------------------
// Status bar - stable layout: fixed heights, no elements appear/disappear.
// ----------------------------------------------------------------------

@Composable
private fun StatusBar(s: UiState.Game, vm: GameViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = moneyText(s.bank),
            modifier = Modifier.testTag("bank"),
            color = Color.Black,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Moves ${s.state.moveCount}",
            modifier = Modifier.testTag("moves"),
            color = Color.Black,
            fontSize = 15.sp,
        )
        Text(
            text = s.timerText,
            modifier = Modifier.testTag("timer"),
            color = Color.Black,
            fontSize = 15.sp,
        )
        Box(
            modifier = Modifier
                .testTag("menu")
                .border(1.5.dp, Color.Black)
                .tap { vm.openDialog(DialogState.Menu) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text("Menu", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun MessageLine(message: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = message ?: "",
            modifier = Modifier.testTag("message"),
            color = Color.Black,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

// ----------------------------------------------------------------------
// Board
// ----------------------------------------------------------------------

private fun Modifier.cursorHighlight(show: Boolean): Modifier =
    if (show) border(3.dp, Color.Black, CardDesign.corner) else this

private fun Modifier.selectionHighlight(show: Boolean): Modifier =
    if (!show) this else drawBehind {
        drawRoundRect(
            color = Color.Black,
            style = Stroke(
                width = 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
            ),
            cornerRadius = CornerRadius(6.dp.toPx()),
        )
    }

@Composable
private fun TopRow(s: UiState.Game, vm: GameViewModel, cardW: Dp, cardH: Dp, gap: Dp) {
    val state = s.state
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        // Foundations (the "ace shelf") on the left, aligned with tableau 0..3.
        val sel = s.selection
        for (f in 0..3) {
            val top = state.foundations[f].lastOrNull()
            val isCursor = s.cursor.zone == Zone.FOUNDATION && s.cursor.index == f
            val isSelected = sel != null && sel.zone == Zone.FOUNDATION && sel.index == f
            Box(
                modifier = Modifier
                    .testTag("foundation$f")
                    .cursorHighlight(isCursor)
                    .selectionHighlight(isSelected)
                    .tap { vm.onZoneTap(Cursor(Zone.FOUNDATION, f)) },
            ) {
                if (top != null) {
                    CardFace(top, cardW, cardH, s.settings.suitStyle)
                } else {
                    EmptySlot(cardW, cardH, label = "A")
                }
            }
        }
        // Gap column keeps the deck right-aligned with tableau 5..6.
        Spacer(Modifier.width(cardW))
        // Stock
        val stockCursor = s.cursor.zone == Zone.STOCK
        Box(
            modifier = Modifier
                .testTag("stock")
                .cursorHighlight(stockCursor)
                .tap { vm.onZoneTap(Cursor(Zone.STOCK)) },
        ) {
            if (state.stock.isNotEmpty()) {
                Box {
                    CardBack(cardW, cardH)
                    Text(
                        text = "${state.stock.size}",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.White)
                            .padding(horizontal = 3.dp),
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                EmptySlot(cardW, cardH, label = if (state.waste.isEmpty()) "" else "R")
            }
        }
        // Waste
        val wasteTop = state.waste.lastOrNull()
        val wasteCursor = s.cursor.zone == Zone.WASTE
        val wasteSelected = s.selection?.zone == Zone.WASTE
        Box(
            modifier = Modifier
                .testTag("waste")
                .cursorHighlight(wasteCursor)
                .selectionHighlight(wasteSelected)
                .combinedClickable(
                    interactionSource = null,
                    indication = null,
                    onDoubleClick = { vm.onSendToFoundation(Cursor(Zone.WASTE)) },
                    onClick = { vm.onZoneTap(Cursor(Zone.WASTE)) },
                ),
        ) {
            if (wasteTop != null) {
                CardFace(wasteTop, cardW, cardH, s.settings.suitStyle)
            } else {
                EmptySlot(cardW, cardH)
            }
        }
    }
}

@Composable
private fun TableauColumn(
    col: Int,
    s: UiState.Game,
    vm: GameViewModel,
    cardW: Dp,
    cardH: Dp,
    available: Dp,
) {
    val cards = s.state.tableau[col]
    val downStep = cardH * 0.18f
    val upStep = cardH * 0.34f
    val downCount = cards.count { !it.faceUp }
    val upCount = cards.size - downCount
    val needed = cardH + downStep * downCount + upStep * (upCount - 1).coerceAtLeast(0)
    val scale = if (needed > available && cards.size > 1) (available - cardH) / (needed - cardH) else 1f
    val dStep = downStep * scale
    val uStep = upStep * scale

    val cursorHere = s.cursor.zone == Zone.TABLEAU && s.cursor.index == col
    val cursorCardIndex = if (cursorHere) cards.size - 1 - s.cursor.depth else -1
    val selection = s.selection

    Box(modifier = Modifier.width(cardW).height(available).testTag("tableau$col")) {
        if (cards.isEmpty()) {
            EmptySlot(
                cardW, cardH,
                modifier = Modifier
                    .cursorHighlight(cursorHere)
                    .tap { vm.onZoneTap(Cursor(Zone.TABLEAU, col)) },
            )
        } else {
            var y = 0.dp
            cards.forEachIndexed { i, card ->
                val depth = cards.size - 1 - i
                val isSelected = selection != null &&
                    selection.zone == Zone.TABLEAU &&
                    selection.index == col &&
                    i >= selection.cardIndex
                val base = Modifier
                    .offset(y = y)
                    .cursorHighlight(cursorHere && i == cursorCardIndex)
                    .selectionHighlight(isSelected)
                if (card.faceUp) {
                    CardFace(
                        card, cardW, cardH, s.settings.suitStyle,
                        modifier = base.combinedClickable(
                            interactionSource = null,
                            indication = null,
                            onDoubleClick = { vm.onSendToFoundation(Cursor(Zone.TABLEAU, col)) },
                            onClick = { vm.onZoneTap(Cursor(Zone.TABLEAU, col, depth)) },
                        ),
                    )
                } else {
                    CardBack(
                        cardW, cardH,
                        modifier = base.tap { vm.onZoneTap(Cursor(Zone.TABLEAU, col, 0)) },
                    )
                }
                y += if (card.faceUp) uStep else dStep
            }
        }
    }
}

// ----------------------------------------------------------------------
// Bottom action bar - large touch targets for the most-used actions.
// ----------------------------------------------------------------------

@Composable
private fun BottomBar(s: UiState.Game, vm: GameViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EButton(
            text = "Undo",
            modifier = Modifier.weight(1f).testTag("btn_undo"),
            enabled = s.canUndo,
            onClick = vm::onUndo,
        )
        EButton(
            text = "Hint",
            modifier = Modifier.weight(1f).testTag("btn_hint"),
            onClick = vm::onHint,
        )
        if (s.autoCompleteAvailable) {
            EButton(
                text = "Auto",
                modifier = Modifier.weight(1f).testTag("btn_auto"),
                onClick = vm::onAutoComplete,
            )
        } else {
            EButton(
                text = "Draw",
                modifier = Modifier.weight(1f).testTag("btn_draw"),
                enabled = s.state.stock.isNotEmpty() || s.state.waste.isNotEmpty(),
                onClick = vm::onDraw,
            )
        }
        if (s.settings.eInkMode) {
            EButton(
                text = "Refresh",
                modifier = Modifier.weight(1f).testTag("btn_refresh"),
                onClick = vm::onFullRefresh,
            )
        }
    }
}

/**
 * E-ink ghosting eraser: drives the panel through two full black->white
 * inversion cycles (the same waveform trick e-readers use), ending on a
 * white frame so the panel settles clean before the game repaints.
 * Deliberately hard cuts - no fade animation.
 */
@Composable
private fun FullRefreshOverlay(pulse: Int) {
    var phase by remember { mutableStateOf(0) }
    LaunchedEffect(pulse) {
        if (pulse > 0) {
            repeat(2) {
                phase = 1
                delay(320)
                phase = 2
                delay(320)
            }
            phase = 0
        }
    }
    when (phase) {
        1 -> Box(Modifier.fillMaxSize().background(Color.Black).testTag("refresh_overlay"))
        2 -> Box(Modifier.fillMaxSize().background(Color.White).testTag("refresh_overlay"))
    }
}
