package com.jackque.solitaire.engine.settings

import com.jackque.solitaire.engine.DrawMode
import com.jackque.solitaire.engine.input.KeyBindings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * How suits are told apart on a monochrome screen:
 * - OUTLINE: black suits solid, red suits drawn as outlines (default)
 * - LETTERS: S/H/D/C letters, red ones outlined
 * - FILLED: all suits solid (for color/grayscale LCDs)
 */
enum class SuitStyle(val label: String) {
    OUTLINE("Outlined red suits"),
    LETTERS("Suit letters"),
    FILLED("All suits filled"),
}

/** How often the status-bar timer text changes (fewer updates = fewer e-ink refreshes). */
enum class TimerDetail(val label: String) {
    OFF("Hidden"),
    MINUTES("Minutes"),
    SECONDS("Seconds"),
}

@Serializable
data class AppSettings(
    val drawMode: DrawMode = DrawMode.DRAW_ONE,
    /** E-Ink mode is the default: no animations, maximum contrast, minimal redraws. */
    val eInkMode: Boolean = true,
    val suitStyle: SuitStyle = SuitStyle.OUTLINE,
    val timerDetail: TimerDetail = TimerDetail.MINUTES,
    val keyBindings: KeyBindings = KeyBindings.defaults(),
)

object AppSettingsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(settings: AppSettings): String = json.encodeToString(settings)

    /** Falls back to defaults on corrupt input. */
    fun decode(text: String): AppSettings = try {
        json.decodeFromString<AppSettings>(text)
    } catch (e: SerializationException) {
        AppSettings()
    } catch (e: IllegalArgumentException) {
        AppSettings()
    }
}
