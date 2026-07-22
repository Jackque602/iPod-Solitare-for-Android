package com.jackque.solitaire.data

import com.jackque.solitaire.engine.SavedGame
import com.jackque.solitaire.engine.settings.AppSettings
import com.jackque.solitaire.engine.stats.Statistics

/**
 * Persistence boundary of the app. Backed by DataStore in production and by
 * an in-memory fake in unit tests.
 */
interface SolitaireStore {
    suspend fun loadSettings(): AppSettings
    suspend fun saveSettings(settings: AppSettings)

    suspend fun loadStatistics(): Statistics
    suspend fun saveStatistics(statistics: Statistics)

    /** Null when there is no (or no valid) game in progress. */
    suspend fun loadSavedGame(): SavedGame?

    /** Passing null clears the save (e.g. after a win). */
    suspend fun saveSavedGame(saved: SavedGame?)
}
