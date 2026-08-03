package com.jackque.solitaire.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jackque.solitaire.engine.SavedGame
import com.jackque.solitaire.engine.SavedGameCodec
import com.jackque.solitaire.engine.settings.AppSettings
import com.jackque.solitaire.engine.settings.AppSettingsCodec
import com.jackque.solitaire.engine.stats.Statistics
import com.jackque.solitaire.engine.stats.StatisticsCodec
import com.jackque.solitaire.widget.SolitaireWidgetProvider
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "solitaire")

/**
 * DataStore-backed persistence. Everything is stored as JSON strings whose
 * codecs live (and are unit tested) in the engine module. Corrupt content
 * degrades gracefully: settings/statistics fall back to defaults, a broken
 * save falls back to a fresh deal.
 */
class DataStoreSolitaireStore(private val context: Context) : SolitaireStore {

    private object Keys {
        val settings = stringPreferencesKey("settings_json")
        val statistics = stringPreferencesKey("statistics_json")
        val savedGame = stringPreferencesKey("saved_game_json")
        val widgetSelection = stringPreferencesKey("widget_selection")
    }

    /** The widget board's pending selection (encoded by WidgetPlay). */
    suspend fun loadWidgetSelection(): String =
        context.dataStore.data.first()[Keys.widgetSelection] ?: ""

    suspend fun saveWidgetSelection(text: String) {
        context.dataStore.edit { it[Keys.widgetSelection] = text }
    }

    override suspend fun loadSettings(): AppSettings {
        val text = context.dataStore.data.first()[Keys.settings] ?: return AppSettings()
        return AppSettingsCodec.decode(text)
    }

    override suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { it[Keys.settings] = AppSettingsCodec.encode(settings) }
    }

    override suspend fun loadStatistics(): Statistics {
        val text = context.dataStore.data.first()[Keys.statistics] ?: return Statistics()
        return StatisticsCodec.decode(text) ?: Statistics()
    }

    override suspend fun saveStatistics(statistics: Statistics) {
        context.dataStore.edit { it[Keys.statistics] = StatisticsCodec.encode(statistics) }
        // Statistics are written on every persisted change (each move, win,
        // import, ...), which makes this the one hook the glance widget
        // needs to stay current.
        SolitaireWidgetProvider.updateAll(context)
    }

    override suspend fun loadSavedGame(): SavedGame? {
        val text = context.dataStore.data.first()[Keys.savedGame] ?: return null
        return SavedGameCodec.decode(text)
    }

    override suspend fun saveSavedGame(saved: SavedGame?) {
        context.dataStore.edit { prefs ->
            if (saved == null) {
                prefs.remove(Keys.savedGame)
            } else {
                prefs[Keys.savedGame] = SavedGameCodec.encode(saved)
            }
        }
    }
}
