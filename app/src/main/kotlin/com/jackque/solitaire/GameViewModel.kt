package com.jackque.solitaire

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jackque.solitaire.data.SolitaireStore
import com.jackque.solitaire.engine.DrawMode
import com.jackque.solitaire.engine.GameState
import com.jackque.solitaire.engine.Klondike
import com.jackque.solitaire.engine.Move
import com.jackque.solitaire.engine.MoveDescriber
import com.jackque.solitaire.engine.SolitaireSession
import com.jackque.solitaire.engine.input.Cursor
import com.jackque.solitaire.engine.input.Direction
import com.jackque.solitaire.engine.input.KeyAction
import com.jackque.solitaire.engine.input.Navigator
import com.jackque.solitaire.engine.input.Selection
import com.jackque.solitaire.engine.input.Zone
import com.jackque.solitaire.engine.settings.AppSettings
import com.jackque.solitaire.engine.settings.SuitStyle
import com.jackque.solitaire.engine.settings.TimerDetail
import com.jackque.solitaire.engine.achievements.AchievementId
import com.jackque.solitaire.engine.backup.BackupCodec
import com.jackque.solitaire.engine.backup.BackupData
import com.jackque.solitaire.engine.stats.Statistics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Which modal screen is currently open, if any. */
sealed interface DialogState {
    data object Menu : DialogState
    data object Settings : DialogState
    data object Stats : DialogState
    data object Achievements : DialogState
    /** Grand Tour detail: the 24-arrangement progress board. */
    data object GrandTour : DialogState
    data object Controls : DialogState
    /** Key-binding list; [capturing] is the action awaiting its new key. */
    data class Rebind(val capturing: KeyAction? = null) : DialogState
    data object SeedEntry : DialogState
    data object GameWon : DialogState
    /** Asks before abandoning a deal in progress (it costs $52 and a loss). */
    data class ConfirmNewDeal(val restart: Boolean) : DialogState
}

sealed interface UiState {
    data object Loading : UiState

    data class Game(
        val state: GameState,
        val bank: Long,
        val elapsedSeconds: Long,
        val timerText: String,
        val cursor: Cursor,
        val selection: Selection?,
        val canUndo: Boolean,
        val autoCompleteAvailable: Boolean,
        val won: Boolean,
        val settings: AppSettings,
        val stats: Statistics,
        val dialog: DialogState?,
        /** One-line message under the status bar (hints, illegal move, ...). */
        val statusMessage: String?,
        /** Increment triggers one full black/white e-ink refresh flash. */
        val refreshPulse: Int,
        /** Achievements earned by the just-won deal, for the win dialog. */
        val newAchievements: List<AchievementId> = emptyList(),
    ) : UiState
}

class GameViewModel(
    private val store: SolitaireStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val seedSource: () -> Long = { Random.nextLong() },
) : ViewModel() {

    private val _ui = MutableStateFlow<UiState>(UiState.Loading)
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var session: SolitaireSession? = null
    private var settings: AppSettings = AppSettings()
    private var cursor: Cursor = Cursor(Zone.STOCK)
    private var selection: Selection? = null
    private var dialog: DialogState? = null
    private var statusMessage: String? = null
    private var refreshPulse: Int = 0
    private var windowActive: Boolean = true
    private var lastPersistedElapsed: Long = 0
    private var recentAchievements: List<AchievementId> = emptyList()

    init {
        viewModelScope.launch {
            settings = store.loadSettings()
            val stats = store.loadStatistics()
            val saved = store.loadSavedGame()
            session = SolitaireSession.start(
                stats = stats,
                savedGame = saved,
                newSeed = seedSource(),
                drawMode = settings.drawMode,
                timestampMillis = clock(),
            )
            persist()
            publish()
            startTicker()
        }
    }

    // ------------------------------------------------------------------
    // Board interaction (shared by touch and keyboard)
    // ------------------------------------------------------------------

    fun onDirection(direction: Direction) {
        val s = session ?: return
        clearMessage()
        cursor = Navigator.move(s.state, cursor, direction)
        publish()
    }

    fun onConfirm() {
        val s = session ?: return
        act(Navigator.activate(s.state, cursor, selection))
    }

    /** A tap moves the cursor there and activates, exactly like CONFIRM. */
    fun onZoneTap(target: Cursor) {
        val s = session ?: return
        cursor = Navigator.clamp(s.state, target)
        act(Navigator.activate(s.state, cursor, selection))
    }

    /** Double tap / TO_FOUNDATION shortcut for the pile at [target]. */
    fun onSendToFoundation(target: Cursor? = null) {
        val s = session ?: return
        val at = target?.let { Navigator.clamp(s.state, it) } ?: cursor
        cursor = at
        val move = Navigator.autoFoundationMove(s.state, at)
        if (move != null) applyMove(move) else message("No foundation accepts that card")
    }

    fun onCancelSelection() {
        selection = null
        clearMessage()
        publish()
    }

    fun onDraw() {
        val s = session ?: return
        val move = when {
            s.state.stock.isNotEmpty() -> Move.Draw
            s.state.waste.isNotEmpty() -> Move.Recycle
            else -> return
        }
        applyMove(move)
    }

    fun onUndo() {
        val s = session ?: return
        clearMessage()
        if (s.undo()) {
            selection = null
            cursor = Navigator.clamp(s.state, cursor)
            persist()
            publish()
        } else {
            message("Nothing to undo")
        }
    }

    fun onHint() {
        val s = session ?: return
        val hint = s.nextHint()
        statusMessage = if (hint != null) {
            "Hint: " + MoveDescriber.describe(s.state, hint)
        } else {
            "No moves available - try Undo or a new game"
        }
        publish()
    }

    fun onAutoComplete() {
        val s = session ?: return
        if (!s.isAutoCompleteAvailable) {
            message("Auto-complete needs an empty stock and no hidden cards")
            return
        }
        s.autoCompleteAll(clock())
        selection = null
        cursor = Navigator.clamp(s.state, cursor)
        if (s.isWon) {
            dialog = DialogState.GameWon
            recentAchievements = s.consumeNewlyUnlocked()
        }
        persist()
        publish()
    }

    private fun act(activation: com.jackque.solitaire.engine.input.Activation) {
        val move = activation.move
        if (move != null) {
            applyMove(move)
        } else {
            selection = activation.selection
            clearMessage()
            publish()
        }
    }

    private fun applyMove(move: Move) {
        val s = session ?: return
        clearMessage()
        val result = s.tryMove(move, clock())
        if (result == null) {
            message("Illegal move")
            return
        }
        selection = null
        cursor = Navigator.clamp(s.state, cursor)
        if (result.won) {
            dialog = DialogState.GameWon
            recentAchievements = s.consumeNewlyUnlocked()
        }
        persist()
        publish()
    }

    // ------------------------------------------------------------------
    // Game lifecycle
    // ------------------------------------------------------------------

    /** New game / restart entry point; asks for confirmation mid-deal. */
    fun onNewGameRequested(restart: Boolean = false) {
        val s = session ?: return
        if (!s.isWon && s.state.moveCount > 0) {
            dialog = DialogState.ConfirmNewDeal(restart)
            publish()
        } else {
            startNewDeal(restart)
        }
    }

    fun onNewGameConfirmed(restart: Boolean) = startNewDeal(restart)

    /** Deal a specific seed (from the seed-entry dialog). */
    fun onNewGameWithSeed(seed: Long) {
        val s = session ?: return
        s.newDeal(seed, settings.drawMode, clock())
        afterNewDeal()
    }

    private fun startNewDeal(restart: Boolean) {
        val s = session ?: return
        if (restart) {
            s.restartDeal(clock())
        } else {
            s.newDeal(seedSource(), settings.drawMode, clock())
        }
        afterNewDeal()
    }

    private fun afterNewDeal() {
        selection = null
        cursor = Cursor(Zone.STOCK)
        dialog = null
        recentAchievements = emptyList()
        clearMessage()
        persist()
        publish()
    }

    // ------------------------------------------------------------------
    // Dialogs, settings, keyboard
    // ------------------------------------------------------------------

    fun openDialog(target: DialogState) {
        dialog = target
        publish()
    }

    fun closeDialog() {
        dialog = null
        publish()
    }

    fun onEInkModeChanged(enabled: Boolean) = updateSettings { it.copy(eInkMode = enabled) }

    fun onSuitStyleChanged(style: SuitStyle) = updateSettings { it.copy(suitStyle = style) }

    fun onTimerDetailChanged(detail: TimerDetail) = updateSettings { it.copy(timerDetail = detail) }

    /** Applies to the next deal; the current one keeps its mode. */
    fun onDrawModeChanged(mode: DrawMode) = updateSettings { it.copy(drawMode = mode) }

    fun onStartRebind(action: KeyAction) {
        dialog = DialogState.Rebind(capturing = action)
        publish()
    }

    fun onClearBinding(action: KeyAction) =
        updateSettings { it.copy(keyBindings = it.keyBindings.clear(action)) }

    fun onResetBindings() =
        updateSettings { it.copy(keyBindings = com.jackque.solitaire.engine.input.KeyBindings.defaults()) }

    fun onFullRefresh() {
        refreshPulse++
        publish()
    }

    /**
     * Hardware keyboard entry point. [keyName] is the normalized Android
     * key name ("J", "ENTER", "DPAD_LEFT"). Returns true when consumed.
     */
    fun onKey(keyName: String): Boolean {
        val s = session ?: return false
        val current = dialog

        // A rebind capture swallows the next key press.
        if (current is DialogState.Rebind) {
            val capturing = current.capturing
            if (capturing != null) {
                updateSettings { it.copy(keyBindings = it.keyBindings.rebind(capturing, keyName)) }
                dialog = DialogState.Rebind(capturing = null)
                publish()
                return true
            }
        }

        val action = settings.keyBindings.actionFor(keyName) ?: return false

        // While a dialog is open only MENU/CANCEL (close) are handled here;
        // everything else stays with the dialog's own focus navigation.
        if (current != null) {
            return when (action) {
                KeyAction.MENU, KeyAction.CANCEL -> {
                    dialog = null
                    publish()
                    true
                }
                else -> false
            }
        }

        when (action) {
            KeyAction.LEFT -> onDirection(Direction.LEFT)
            KeyAction.RIGHT -> onDirection(Direction.RIGHT)
            KeyAction.UP -> onDirection(Direction.UP)
            KeyAction.DOWN -> onDirection(Direction.DOWN)
            KeyAction.CONFIRM -> onConfirm()
            KeyAction.CANCEL -> onCancelSelection()
            KeyAction.DRAW -> onDraw()
            KeyAction.TO_FOUNDATION -> onSendToFoundation()
            KeyAction.UNDO -> onUndo()
            KeyAction.HINT -> onHint()
            KeyAction.AUTO_COMPLETE -> onAutoComplete()
            KeyAction.NEW_GAME -> onNewGameRequested(restart = false)
            KeyAction.RESTART_DEAL -> onNewGameRequested(restart = true)
            KeyAction.MENU -> openDialog(DialogState.Menu)
            KeyAction.STATS -> openDialog(DialogState.Stats)
            KeyAction.FULL_REFRESH -> onFullRefresh()
        }
        return true
    }

    // ------------------------------------------------------------------
    // Gameplay data export / import
    // ------------------------------------------------------------------

    /**
     * Everything worth carrying to another device: statistics (with the
     * bank), settings, and the deal in progress including its move log,
     * so the position, undo history and score restore exactly.
     */
    fun exportBackupJson(): String? {
        val s = session ?: return null
        return BackupCodec.encode(
            BackupData(
                statistics = s.stats,
                settings = settings,
                savedGame = if (s.isWon) null else s.toSavedGame(),
            )
        )
    }

    /**
     * Replace the app's data with an imported backup. Accepts both full
     * backups and the older statistics-only export format (which keeps
     * the current deal and only swaps the statistics). No buy-in is
     * charged for a restored in-progress deal - it was paid when that
     * deal originally started.
     */
    fun importBackupJson(text: String): Boolean {
        val current = session ?: return false
        val backup = BackupCodec.decode(text) ?: return false
        backup.settings?.let { imported ->
            settings = imported
            viewModelScope.launch { store.saveSettings(imported) }
        }
        if (backup.savedGame != null) {
            session = SolitaireSession.start(
                stats = backup.statistics,
                savedGame = backup.savedGame,
                newSeed = seedSource(),
                drawMode = settings.drawMode,
                timestampMillis = clock(),
            )
        } else {
            current.importStatistics(backup.statistics)
        }
        selection = null
        cursor = Cursor(Zone.STOCK)
        recentAchievements = emptyList()
        clearMessage()
        persist()
        publish()
        return true
    }

    /** Surface a transient message in the status line (e.g. export results). */
    fun notify(text: String) = message(text)

    // ------------------------------------------------------------------
    // Lifecycle & timer
    // ------------------------------------------------------------------

    fun onWindowActiveChanged(active: Boolean) {
        windowActive = active
        if (!active) {
            persist()
        } else {
            viewModelScope.launch { reconcileWithStore() }
        }
    }

    /**
     * The widget plays against the persisted save directly, so when the
     * app comes back to the foreground the in-memory session may be
     * stale. If storage tells a different story (elapsed time aside),
     * rebuild from storage.
     */
    private suspend fun reconcileWithStore() {
        val s = session ?: return
        val storedStats = store.loadStatistics()
        val storedSave = store.loadSavedGame()
        val inMemory = if (s.isWon) null else s.toSavedGame()
        val sameGame = storedSave?.copy(elapsedSeconds = 0) == inMemory?.copy(elapsedSeconds = 0)
        if (sameGame && storedStats == s.stats) return

        session = SolitaireSession.start(
            stats = storedStats,
            savedGame = storedSave,
            newSeed = seedSource(),
            drawMode = settings.drawMode,
            timestampMillis = clock(),
        )
        selection = null
        cursor = Cursor(Zone.STOCK)
        recentAchievements = emptyList()
        clearMessage()
        publish()
    }

    private fun startTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val s = session ?: continue
                if (!windowActive || s.isWon || dialog != null) continue
                s.tick()
                // Persist elapsed time occasionally so a swipe-kill loses
                // at most half a minute of timer (moves persist instantly).
                if (s.elapsedSeconds - lastPersistedElapsed >= 30) persist()
                val current = _ui.value
                if (current is UiState.Game && timerText(s.elapsedSeconds) != current.timerText) {
                    publish()
                }
            }
        }
    }

    private fun timerText(elapsed: Long): String = when (settings.timerDetail) {
        TimerDetail.OFF -> ""
        TimerDetail.MINUTES -> "%d min".format(elapsed / 60)
        TimerDetail.SECONDS -> "%d:%02d".format(elapsed / 60, elapsed % 60)
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
        viewModelScope.launch { store.saveSettings(settings) }
        publish()
    }

    private fun persist() {
        val s = session ?: return
        lastPersistedElapsed = s.elapsedSeconds
        val saved = if (s.isWon) null else s.toSavedGame()
        val stats = s.stats
        viewModelScope.launch {
            store.saveSavedGame(saved)
            store.saveStatistics(stats)
        }
    }

    private fun message(text: String) {
        statusMessage = text
        publish()
    }

    private fun clearMessage() {
        statusMessage = null
    }

    private fun publish() {
        val s = session ?: return
        _ui.value = UiState.Game(
            state = s.state,
            bank = s.bank,
            elapsedSeconds = s.elapsedSeconds,
            timerText = timerText(s.elapsedSeconds),
            cursor = cursor,
            selection = selection,
            canUndo = s.canUndo,
            autoCompleteAvailable = s.isAutoCompleteAvailable,
            won = s.isWon,
            settings = settings,
            stats = s.stats,
            dialog = dialog,
            statusMessage = statusMessage,
            refreshPulse = refreshPulse,
            newAchievements = recentAchievements,
        )
    }

    class Factory(private val store: SolitaireStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GameViewModel(store) as T
    }
}
