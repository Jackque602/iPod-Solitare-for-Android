package com.jackque.solitaire

import com.jackque.solitaire.data.SolitaireStore
import com.jackque.solitaire.engine.SavedGame
import com.jackque.solitaire.engine.settings.AppSettings
import com.jackque.solitaire.engine.stats.Statistics

class FakeSolitaireStore(
    var settings: AppSettings = AppSettings(),
    var statistics: Statistics = Statistics(),
    var savedGame: SavedGame? = null,
) : SolitaireStore {

    var savedGameWrites = 0
        private set
    var statisticsWrites = 0
        private set

    override suspend fun loadSettings(): AppSettings = settings

    override suspend fun saveSettings(settings: AppSettings) {
        this.settings = settings
    }

    override suspend fun loadStatistics(): Statistics = statistics

    override suspend fun saveStatistics(statistics: Statistics) {
        this.statistics = statistics
        statisticsWrites++
    }

    override suspend fun loadSavedGame(): SavedGame? = savedGame

    override suspend fun saveSavedGame(saved: SavedGame?) {
        savedGame = saved
        savedGameWrites++
    }
}
