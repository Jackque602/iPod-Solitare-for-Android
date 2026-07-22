package com.jackque.solitaire.engine.settings

import com.jackque.solitaire.engine.DrawMode
import com.jackque.solitaire.engine.input.KeyBindings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * How "red" suits (hearts/diamonds) are told apart on a monochrome screen:
 * - INVERTED: red suits drawn white-on-black - unmistakable at 1-bit depth
 *   on e-ink panels (default)
 * - OUTLINE: red suits drawn as hollow outlines
 * - LETTERS: S/H/D/C letters, red ones white-on-black
 * - FILLED: all suits solid black (for color/grayscale LCDs)
 */
enum class SuitStyle(val label: String) {
    INVERTED("Red suits inverted"),
    OUTLINE("Red suits outlined"),
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
    /**
     * Settings-format version, used for forward migrations. Old files
     * without the field decode as 1 (the field's default), which is what
     * lets [AppSettingsCodec] tell them apart from current ones.
     */
    val version: Int = 1,
    val drawMode: DrawMode = DrawMode.DRAW_ONE,
    /** E-Ink mode is the default: no animations, maximum contrast, minimal redraws. */
    val eInkMode: Boolean = true,
    val suitStyle: SuitStyle = SuitStyle.INVERTED,
    val timerDetail: TimerDetail = TimerDetail.MINUTES,
    val keyBindings: KeyBindings = KeyBindings.defaults(),
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}

object AppSettingsCodec {
    // encodeDefaults keeps the version field (and everything else) in the
    // stored JSON even when it matches the data-class default.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(settings: AppSettings): String =
        json.encodeToString(settings.copy(version = AppSettings.CURRENT_VERSION))

    /** Falls back to defaults on corrupt input. */
    fun decode(text: String): AppSettings = try {
        migrate(json.decodeFromString<AppSettings>(text))
    } catch (e: SerializationException) {
        migrate(AppSettings())
    } catch (e: IllegalArgumentException) {
        migrate(AppSettings())
    }

    /**
     * Version 1 -> 2: OUTLINE was the original default but proved
     * indistinguishable from solid glyphs on real 1-bit e-ink panels, so
     * v1 files still on it are moved to the new INVERTED default. Users
     * who pick OUTLINE again afterwards keep it (their file says v2).
     */
    private fun migrate(settings: AppSettings): AppSettings {
        var result = settings
        if (result.version < 2 && result.suitStyle == SuitStyle.OUTLINE) {
            result = result.copy(suitStyle = SuitStyle.INVERTED)
        }
        return result.copy(version = AppSettings.CURRENT_VERSION)
    }
}
