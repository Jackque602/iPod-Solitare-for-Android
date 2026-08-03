package com.jackque.solitaire.widget

import android.content.Context
import com.jackque.solitaire.data.DataStoreSolitaireStore
import com.jackque.solitaire.engine.SolitaireSession
import kotlin.random.Random

/**
 * Executes widget actions against the same persisted game the app uses:
 * load, apply, save. The app reconciles from storage when it comes to
 * the foreground, so play flows freely between widget and app.
 */
object WidgetGame {

    const val TOKEN_UNDO = "undo"
    const val TOKEN_NEW = "new"
    const val TOKEN_AUTO = "auto"

    suspend fun handleAction(context: Context, token: String) {
        val store = DataStoreSolitaireStore(context)
        val stats = store.loadStatistics()
        val saved = store.loadSavedGame()
        val settings = store.loadSettings()
        val now = System.currentTimeMillis()

        when (token) {
            TOKEN_NEW -> {
                val session = SolitaireSession.start(stats, saved, Random.nextLong(), settings.drawMode, now)
                // start() deals fresh (charging the buy-in) when there was no
                // usable save; only a genuinely restored deal needs newDeal,
                // which records its loss and charges again - same as in-app.
                val restoredExisting = saved != null &&
                    session.state.seed == saved.seed &&
                    session.state.moveCount == saved.moves.size
                if (restoredExisting && !session.isWon) {
                    session.newDeal(Random.nextLong(), settings.drawMode, now)
                }
                store.saveWidgetSelection("")
                persist(store, session)
            }
            TOKEN_UNDO -> {
                saved ?: return
                val session = SolitaireSession.start(stats, saved, Random.nextLong(), settings.drawMode, now)
                if (session.undo()) {
                    store.saveWidgetSelection("")
                    persist(store, session)
                }
            }
            TOKEN_AUTO -> {
                saved ?: return
                val session = SolitaireSession.start(stats, saved, Random.nextLong(), settings.drawMode, now)
                if (session.isAutoCompleteAvailable) {
                    session.autoCompleteAll(now)
                    store.saveWidgetSelection("")
                    persist(store, session)
                }
            }
            else -> {
                saved ?: return
                val session = SolitaireSession.start(stats, saved, Random.nextLong(), settings.drawMode, now)
                val previous = WidgetPlay.sanitize(
                    session.state,
                    WidgetPlay.decodeSelection(store.loadWidgetSelection()),
                )
                val next = WidgetPlay.tap(session, previous, token, now)
                store.saveWidgetSelection(WidgetPlay.encodeSelection(next))
                persist(store, session)
            }
        }
    }

    private suspend fun persist(store: DataStoreSolitaireStore, session: SolitaireSession) {
        store.saveSavedGame(if (session.isWon) null else session.toSavedGame())
        store.saveStatistics(session.stats)
    }
}
