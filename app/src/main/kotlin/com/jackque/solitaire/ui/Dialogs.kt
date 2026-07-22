package com.jackque.solitaire.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jackque.solitaire.DialogState
import com.jackque.solitaire.GameViewModel
import com.jackque.solitaire.UiState
import com.jackque.solitaire.engine.DrawMode
import com.jackque.solitaire.engine.Scoring
import com.jackque.solitaire.engine.input.KeyAction
import com.jackque.solitaire.engine.settings.SuitStyle
import com.jackque.solitaire.engine.settings.TimerDetail
import com.jackque.solitaire.engine.stats.Statistics
import com.jackque.solitaire.ui.theme.EButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppDialogs(s: UiState.Game, vm: GameViewModel) {
    when (val d = s.dialog) {
        null -> Unit
        is DialogState.Menu -> MenuDialog(s, vm)
        is DialogState.Settings -> SettingsDialog(s, vm)
        is DialogState.Stats -> StatsDialog(s, vm)
        is DialogState.Controls -> ControlsDialog(s, vm)
        is DialogState.Rebind -> RebindDialog(s, vm, d)
        is DialogState.SeedEntry -> SeedDialog(s, vm)
        is DialogState.GameWon -> WonDialog(s, vm)
        is DialogState.ConfirmNewDeal -> ConfirmDialog(vm, d)
    }
}

/**
 * Shared dialog frame: white panel, black border, no scrim animation.
 * Hardware keys bubble to the ViewModel so MENU/CANCEL close the dialog
 * and key-capture works; [interceptKeys] is off for text entry.
 */
@Composable
private fun DialogShell(
    vm: GameViewModel,
    title: String,
    interceptKeys: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = vm::closeDialog,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        val keyModifier = if (interceptKeys) {
            Modifier.onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) vm.onKey(keyNameOf(event)) else false
            }
        } else {
            Modifier
        }
        Column(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 380.dp)
                .heightIn(max = 560.dp)
                .background(Color.White)
                .border(2.dp, Color.Black)
                .then(keyModifier)
                .focusRequester(focusRequester)
                .focusable()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(title, color = Color.Black, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun MenuItem(text: String, enabled: Boolean = true, tag: String = "", onClick: () -> Unit) {
    EButton(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .let { if (tag.isEmpty()) it else it.testTag(tag) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun MenuDialog(s: UiState.Game, vm: GameViewModel) {
    DialogShell(vm, "Solitaire") {
        MenuItem("Resume") { vm.closeDialog() }
        MenuItem("New Game", tag = "menu_new_game") { vm.onNewGameRequested(restart = false) }
        MenuItem("Restart This Deal") { vm.onNewGameRequested(restart = true) }
        MenuItem("New Game From Seed…") { vm.openDialog(DialogState.SeedEntry) }
        val nextMode = if (s.settings.drawMode == DrawMode.DRAW_ONE) DrawMode.DRAW_THREE else DrawMode.DRAW_ONE
        MenuItem("Draw Mode: ${label(s.settings.drawMode)} (tap for ${label(nextMode)})") {
            vm.onDrawModeChanged(nextMode)
        }
        MenuItem("Hint") { vm.closeDialog(); vm.onHint() }
        MenuItem("Auto-Complete", enabled = s.autoCompleteAvailable) {
            vm.closeDialog(); vm.onAutoComplete()
        }
        MenuItem("Statistics", tag = "menu_stats") { vm.openDialog(DialogState.Stats) }
        MenuItem("Settings") { vm.openDialog(DialogState.Settings) }
        MenuItem("Controls") { vm.openDialog(DialogState.Controls) }
        if (s.settings.eInkMode) {
            MenuItem("Full Screen Refresh") { vm.closeDialog(); vm.onFullRefresh() }
        }
    }
}

private fun label(mode: DrawMode): String =
    if (mode == DrawMode.DRAW_ONE) "Draw 1" else "Draw 3"

@Composable
private fun SettingRow(labelText: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = labelText,
            modifier = Modifier.weight(1f),
            color = Color.Black,
            fontSize = 15.sp,
        )
        EButton(text = value, onClick = onClick)
    }
}

@Composable
private fun SettingsDialog(s: UiState.Game, vm: GameViewModel) {
    DialogShell(vm, "Settings") {
        val st = s.settings
        SettingRow("E-Ink mode", if (st.eInkMode) "On" else "Off") {
            vm.onEInkModeChanged(!st.eInkMode)
        }
        SettingRow("Red suits", st.suitStyle.label) {
            val next = SuitStyle.entries[(st.suitStyle.ordinal + 1) % SuitStyle.entries.size]
            vm.onSuitStyleChanged(next)
        }
        SettingRow("Timer", st.timerDetail.label) {
            val next = TimerDetail.entries[(st.timerDetail.ordinal + 1) % TimerDetail.entries.size]
            vm.onTimerDetailChanged(next)
        }
        SettingRow("Draw mode (next deal)", label(st.drawMode)) {
            val next = if (st.drawMode == DrawMode.DRAW_ONE) DrawMode.DRAW_THREE else DrawMode.DRAW_ONE
            vm.onDrawModeChanged(next)
        }
        Spacer(Modifier.height(8.dp))
        MenuItem("Keyboard Bindings") { vm.openDialog(DialogState.Rebind()) }
        MenuItem("Close") { vm.closeDialog() }
    }
}

private fun hms(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val sec = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
private fun StatRow(labelText: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(labelText, Modifier.weight(1f), color = Color.Black, fontSize = 14.sp)
        Text(value, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatsDialog(s: UiState.Game, vm: GameViewModel) {
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val json = vm.exportStatisticsJson()
            if (json != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    }
                    vm.notify("Statistics exported")
                } catch (e: Exception) {
                    vm.notify("Export failed")
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (text != null && vm.importStatisticsJson(text)) {
                    vm.notify("Statistics imported")
                } else {
                    vm.notify("Import failed: not a valid statistics file")
                }
            } catch (e: Exception) {
                vm.notify("Import failed")
            }
        }
    }

    DialogShell(vm, "Statistics") {
        val stats: Statistics = s.stats
        val completed = stats.gamesCompleted
        StatRow("Cumulative score", moneyText(stats.bank))
        StatRow("Highest score", moneyText(stats.highestBank))
        StatRow("Lowest score", moneyText(stats.lowestBank))
        Spacer(Modifier.height(6.dp))
        StatRow("Deals started", "${stats.gamesStarted}")
        StatRow("Wins", "${stats.wins}")
        StatRow("Losses", "${stats.losses}")
        if (completed > 0) {
            StatRow("Win rate", "${stats.wins * 100 / completed}%")
        }
        StatRow("Current win streak", "${stats.currentWinStreak}")
        StatRow("Best win streak", "${stats.bestWinStreak}")
        StatRow("Worst loss streak", "${stats.worstLossStreak}")
        Spacer(Modifier.height(6.dp))
        StatRow("Total moves", "${stats.totalMoves}")
        StatRow("Total time", hms(stats.totalTimeSeconds))
        StatRow("Foundation cards", "${stats.totalFoundationCards}")
        StatRow("Best game score", stats.bestGameScore?.let { money(it) } ?: "—")
        StatRow("Worst game score", stats.worstGameScore?.let { money(it) } ?: "—")
        StatRow("Average game score", stats.averageGameScore?.let { "%.1f".format(it) } ?: "—")
        if (stats.history.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Recent games", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.US) }
            stats.history.take(10).forEach { game ->
                Text(
                    text = buildString {
                        append(if (game.won) "Won " else "Lost")
                        append("  ")
                        append(money(game.gameScore))
                        append("  ·  ${game.moves} moves  ·  ${hms(game.durationSeconds)}")
                        append("  ·  ")
                        append(dateFormat.format(Date(game.timestampMillis)))
                    },
                    color = Color.Black,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        MenuItem("Export…") { exportLauncher.launch("solitaire-statistics.json") }
        MenuItem("Import…") { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        MenuItem("Close") { vm.closeDialog() }
    }
}

private fun money(value: Int): String = if (value < 0) "-$${-value}" else "+$$value"

@Composable
private fun ControlsDialog(s: UiState.Game, vm: GameViewModel) {
    DialogShell(vm, "Controls") {
        Text(
            "Move the cursor over a pile, select it, then select a destination. " +
                "On the tableau, UP extends the picked-up run before leaving the column.",
            color = Color.Black,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        KeyAction.entries.forEach { action ->
            StatRow(action.label, s.settings.keyBindings.keysFor(action).joinToString(" / ").ifEmpty { "—" })
        }
        Spacer(Modifier.height(8.dp))
        MenuItem("Change Bindings") { vm.openDialog(DialogState.Rebind()) }
        MenuItem("Close") { vm.closeDialog() }
    }
}

@Composable
private fun RebindDialog(s: UiState.Game, vm: GameViewModel, d: DialogState.Rebind) {
    if (d.capturing != null) {
        DialogShell(vm, "Press a key") {
            Text(
                "Press the key to use for:\n${d.capturing.label}",
                color = Color.Black,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(10.dp))
            MenuItem("Cancel") { vm.openDialog(DialogState.Rebind()) }
        }
        return
    }
    DialogShell(vm, "Keyboard Bindings") {
        KeyAction.entries.forEach { action ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(action.label, color = Color.Black, fontSize = 14.sp)
                    Text(
                        s.settings.keyBindings.keysFor(action).joinToString(" / ").ifEmpty { "unbound" },
                        color = Color.Black,
                        fontSize = 12.sp,
                    )
                }
                EButton("Set") { vm.onStartRebind(action) }
                Spacer(Modifier.width(6.dp))
                EButton("Clear") { vm.onClearBinding(action) }
            }
        }
        Spacer(Modifier.height(8.dp))
        MenuItem("Restore Defaults") { vm.onResetBindings() }
        MenuItem("Close") { vm.closeDialog() }
    }
}

@Composable
private fun SeedDialog(s: UiState.Game, vm: GameViewModel) {
    DialogShell(vm, "New Game From Seed", interceptKeys = false) {
        Text(
            "Current deal seed: ${s.state.seed}\n" +
                "The same seed always deals the same game. Starting a new deal costs \$52.",
            color = Color.Black,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        var text by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, Color.Black)
                .padding(10.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { input ->
                    text = input.filter { it.isDigit() || it == '-' }
                    error = false
                },
                textStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("seed_input"),
            )
        }
        if (error) {
            Spacer(Modifier.height(4.dp))
            Text("Enter a whole number", color = Color.Black, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EButton("Deal", Modifier.weight(1f)) {
                val seed = text.toLongOrNull()
                if (seed == null) error = true else vm.onNewGameWithSeed(seed)
            }
            EButton("Cancel", Modifier.weight(1f)) { vm.closeDialog() }
        }
    }
}

@Composable
private fun WonDialog(s: UiState.Game, vm: GameViewModel) {
    DialogShell(vm, "You Won!") {
        Text(
            "This deal: +$${Scoring.WIN_NET}\n" +
                "Cumulative score: ${moneyText(s.bank)}\n" +
                "Moves: ${s.state.moveCount}   Time: ${hms(s.elapsedSeconds)}",
            modifier = Modifier.testTag("won_text"),
            color = Color.Black,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(12.dp))
        MenuItem("New Game", tag = "won_new_game") { vm.onNewGameConfirmed(restart = false) }
        MenuItem("View Board") { vm.closeDialog() }
    }
}

@Composable
private fun ConfirmDialog(vm: GameViewModel, d: DialogState.ConfirmNewDeal) {
    DialogShell(vm, if (d.restart) "Restart this deal?" else "Start a new game?") {
        Text(
            "The current deal counts as a loss and the next deal costs \$52.",
            color = Color.Black,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EButton("Deal", Modifier.weight(1f).testTag("confirm_deal")) {
                vm.onNewGameConfirmed(d.restart)
            }
            EButton("Cancel", Modifier.weight(1f)) { vm.closeDialog() }
        }
    }
}
