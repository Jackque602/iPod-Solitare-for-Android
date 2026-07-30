package com.jackque.solitaire.engine.backup

import com.jackque.solitaire.engine.SavedGame
import com.jackque.solitaire.engine.settings.AppSettings
import com.jackque.solitaire.engine.settings.AppSettingsCodec
import com.jackque.solitaire.engine.stats.Statistics
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Complete portable snapshot of the app's data: statistics (including the
 * cumulative bank), settings, and the deal in progress with its move log -
 * so an import restores the exact position, undo history and score.
 *
 * The JSON shape is a superset of the older statistics-only export
 * ({"version":1,"statistics":{...}}), so old files import here and files
 * exported here still open in older app versions.
 */
@Serializable
data class BackupData(
    val version: Int = 2,
    val statistics: Statistics,
    val settings: AppSettings? = null,
    val savedGame: SavedGame? = null,
)

object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(backup: BackupData): String = json.encodeToString(backup)

    /**
     * Returns null instead of throwing on malformed input. Embedded
     * settings pass through the normal settings migrations so backups
     * from older app versions land on current defaults correctly.
     */
    fun decode(text: String): BackupData? = try {
        val backup = json.decodeFromString<BackupData>(text)
        backup.copy(settings = backup.settings?.let { AppSettingsCodec.migrate(it) })
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
