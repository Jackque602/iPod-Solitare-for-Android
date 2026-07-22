package com.jackque.solitaire.engine

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent form of a game in progress: the deal is identified by its seed
 * and the position by the move log, so the save stays tiny and restoring is
 * an exact deterministic replay (including the undo history and score).
 */
@Serializable
data class SavedGame(
    val seed: Long,
    val drawMode: DrawMode,
    val moves: List<Move>,
    val elapsedSeconds: Long = 0,
)

object SavedGameCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "t"
    }

    fun encode(saved: SavedGame): String = json.encodeToString(saved)

    /** Returns null instead of throwing on malformed/corrupt input. */
    fun decode(text: String): SavedGame? = try {
        json.decodeFromString<SavedGame>(text)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
