package com.jackque.solitaire

import com.jackque.solitaire.engine.DrawMode
import com.jackque.solitaire.engine.Move
import com.jackque.solitaire.engine.input.KeyAction
import com.jackque.solitaire.engine.stats.Statistics
import com.jackque.solitaire.engine.stats.StatisticsCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for the ViewModel over a fake store. The infinite ticker
 * coroutine means tests advance the scheduler manually with runCurrent()
 * (never advanceUntilIdle, which would chase delay(1000) forever).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun run() = dispatcher.scheduler.runCurrent()

    private fun createVm(store: FakeSolitaireStore): GameViewModel {
        val vm = GameViewModel(store, clock = { 1_700_000_000_000 }, seedSource = { 42L })
        run()
        return vm
    }

    private fun game(vm: GameViewModel): UiState.Game = vm.ui.value as UiState.Game

    @Test
    fun `fresh start charges the buy-in once`() {
        val store = FakeSolitaireStore()
        val vm = createVm(store)
        assertEquals(-52L, game(vm).bank)
        assertEquals(1, game(vm).stats.gamesStarted)
    }

    @Test
    fun `restoring a saved game does not charge again`() {
        val store = FakeSolitaireStore()
        val first = createVm(store)
        first.onDraw()
        run()
        val bankAfterDraw = game(first).bank
        assertEquals(1, game(first).state.moveCount)
        assertNotNull(store.savedGame)

        // Simulate a process death and relaunch against the same store.
        val second = createVm(store)
        assertEquals(bankAfterDraw, game(second).bank)
        assertEquals(1, game(second).state.moveCount)
        assertEquals(1, game(second).stats.gamesStarted)
        assertTrue(game(second).canUndo)
    }

    @Test
    fun `every move is auto-saved`() {
        val store = FakeSolitaireStore()
        val vm = createVm(store)
        val writesBefore = store.savedGameWrites
        vm.onDraw()
        run()
        assertTrue(store.savedGameWrites > writesBefore)
        assertEquals(1, store.savedGame!!.moves.size)
        vm.onUndo()
        run()
        assertEquals(0, store.savedGame!!.moves.size)
    }

    @Test
    fun `undo refunds the bank exactly`() {
        val store = FakeSolitaireStore()
        val vm = createVm(store)
        vm.onDraw()
        run()
        val bank = game(vm).bank
        vm.onUndo()
        run()
        assertEquals(bank, game(vm).bank)
        assertFalse(game(vm).canUndo)
    }

    @Test
    fun `keyboard keys map through the configurable bindings`() {
        val store = FakeSolitaireStore()
        val vm = createVm(store)
        assertTrue("SPACE is bound to draw by default", vm.onKey("SPACE"))
        run()
        assertEquals(1, game(vm).state.moveCount)
        assertFalse("unbound keys are not consumed", vm.onKey("F12"))
    }

    @Test
    fun `rebinding captures the next key and persists`() {
        val store = FakeSolitaireStore()
        val vm = createVm(store)
        vm.onStartRebind(KeyAction.HINT)
        run()
        assertTrue(vm.onKey("Q"))
        run()
        assertEquals(KeyAction.HINT, game(vm).settings.keyBindings.actionFor("Q"))
        assertEquals(KeyAction.HINT, store.settings.keyBindings.actionFor("Q"))
    }

    @Test
    fun `new game requires confirmation mid-deal and charges after it`() {
        val store = FakeSolitaireStore()
        val vm = createVm(store)
        vm.onDraw()
        run()
        vm.onNewGameRequested(restart = false)
        run()
        assertTrue(game(vm).dialog is DialogState.ConfirmNewDeal)
        assertEquals("not charged until confirmed", -52L, game(vm).bank)

        vm.onNewGameConfirmed(restart = false)
        run()
        assertEquals(-104L, game(vm).bank)
        assertEquals(1, game(vm).stats.losses)
        assertEquals(0, game(vm).state.moveCount)
    }

    @Test
    fun `restart reuses the same seed`() {
        val store = FakeSolitaireStore()
        val vm = createVm(store)
        val seed = game(vm).state.seed
        vm.onDraw()
        run()
        vm.onNewGameRequested(restart = true)
        run()
        vm.onNewGameConfirmed(restart = true)
        run()
        assertEquals(seed, game(vm).state.seed)
        assertEquals(0, game(vm).state.moveCount)
    }

    @Test
    fun `backup export and import restore the game position and undo history`() {
        val vm = createVm(FakeSolitaireStore())
        vm.onDraw()
        run()
        val exported = vm.exportBackupJson()
        assertNotNull(exported)
        val bank = game(vm).bank
        val moves = game(vm).state.moveCount

        // Import into a completely separate install.
        val otherStore = FakeSolitaireStore()
        val other = createVm(otherStore)
        assertTrue(other.importBackupJson(exported!!))
        run()
        assertEquals(bank, game(other).bank)
        assertEquals(moves, game(other).state.moveCount)
        assertTrue("undo history survives import", game(other).canUndo)
        assertNotNull("import persists the restored game", otherStore.savedGame)
    }

    @Test
    fun `legacy statistics-only files import and keep the current deal`() {
        val vm = createVm(FakeSolitaireStore())
        vm.onDraw()
        run()
        val movesBefore = game(vm).state.moveCount
        val legacy = StatisticsCodec.encode(Statistics(bank = 416, wins = 2, highestBank = 500))
        assertTrue(vm.importBackupJson(legacy))
        run()
        assertEquals(416L, game(vm).bank)
        assertEquals("stats-only import keeps the deal in progress", movesBefore, game(vm).state.moveCount)

        assertFalse(vm.importBackupJson("not json"))
    }

    @Test
    fun `hints surface in the status message`() {
        val store = FakeSolitaireStore()
        val vm = createVm(store)
        vm.onHint()
        run()
        val message = game(vm).statusMessage
        assertNotNull(message)
        assertTrue(message!!.startsWith("Hint:"))
    }

    @Test
    fun `corrupt saved game falls back to a fresh deal`() {
        val store = FakeSolitaireStore(
            savedGame = com.jackque.solitaire.engine.SavedGame(
                seed = 1L,
                drawMode = DrawMode.DRAW_ONE,
                moves = listOf(Move.WasteToFoundation(0)),
            ),
        )
        val vm = createVm(store)
        assertEquals(-52L, game(vm).bank)
        assertEquals(0, game(vm).state.moveCount)
    }

    @Test
    fun `ticker advances the clock once per second of virtual time`() {
        val store = FakeSolitaireStore()
        val vm = createVm(store)
        // Seconds-level timer detail publishes a fresh timer text per tick.
        vm.onTimerDetailChanged(com.jackque.solitaire.engine.settings.TimerDetail.SECONDS)
        run()
        dispatcher.scheduler.advanceTimeBy(3_000)
        run()
        assertEquals(3L, game(vm).elapsedSeconds)
        assertEquals("0:03", game(vm).timerText)
    }

    @Test
    fun `fresh installs deal draw three by default`() {
        val store = FakeSolitaireStore()
        val vm = createVm(store)
        assertEquals(DrawMode.DRAW_THREE, game(vm).state.drawMode)
    }

    @Test
    fun `draw mode setting applies to the next deal`() {
        val store = FakeSolitaireStore()
        val vm = createVm(store)
        vm.onDrawModeChanged(DrawMode.DRAW_ONE)
        run()
        assertEquals("current deal keeps its mode", DrawMode.DRAW_THREE, game(vm).state.drawMode)
        vm.onNewGameRequested(restart = false)
        run()
        assertEquals(DrawMode.DRAW_ONE, game(vm).state.drawMode)
        assertEquals(DrawMode.DRAW_ONE, store.settings.drawMode)
    }
}
