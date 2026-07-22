package com.jackque.solitaire.engine.input

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Everything the game can do from the keyboard. */
enum class KeyAction(val label: String) {
    LEFT("Move left"),
    RIGHT("Move right"),
    UP("Move up / extend run"),
    DOWN("Move down / shrink run"),
    CONFIRM("Select / drop"),
    CANCEL("Cancel selection"),
    DRAW("Draw from stock"),
    TO_FOUNDATION("Send to foundation"),
    UNDO("Undo"),
    HINT("Hint"),
    AUTO_COMPLETE("Auto-complete"),
    NEW_GAME("New game"),
    RESTART_DEAL("Restart deal"),
    MENU("Menu"),
    STATS("Statistics"),
    FULL_REFRESH("Full screen refresh"),
}

/**
 * Maps normalized key names (Android's KeyEvent.keyCodeToString minus the
 * "KEYCODE_" prefix, e.g. "J", "ENTER", "DPAD_LEFT") to actions. Multiple
 * keys may drive the same action; every binding is user-configurable.
 */
@Serializable
data class KeyBindings(val bindings: Map<String, KeyAction>) {

    fun actionFor(keyName: String): KeyAction? = bindings[keyName.uppercase()]

    fun keysFor(action: KeyAction): List<String> =
        bindings.filterValues { it == action }.keys.sorted()

    /**
     * Bind [keyName] to [action]. The key is silently taken away from
     * whatever action previously owned it.
     */
    fun rebind(action: KeyAction, keyName: String): KeyBindings =
        copy(bindings = bindings - keyName.uppercase() + (keyName.uppercase() to action))

    /** Remove every key bound to [action]. */
    fun clear(action: KeyAction): KeyBindings =
        copy(bindings = bindings.filterValues { it != action })

    companion object {
        fun defaults(): KeyBindings = KeyBindings(
            mapOf(
                "DPAD_LEFT" to KeyAction.LEFT,
                "DPAD_RIGHT" to KeyAction.RIGHT,
                "DPAD_UP" to KeyAction.UP,
                "DPAD_DOWN" to KeyAction.DOWN,
                "J" to KeyAction.LEFT,
                "L" to KeyAction.RIGHT,
                "I" to KeyAction.UP,
                "K" to KeyAction.DOWN,
                "DPAD_CENTER" to KeyAction.CONFIRM,
                "ENTER" to KeyAction.CONFIRM,
                "X" to KeyAction.CANCEL,
                "SPACE" to KeyAction.DRAW,
                "D" to KeyAction.DRAW,
                "F" to KeyAction.TO_FOUNDATION,
                "U" to KeyAction.UNDO,
                "Z" to KeyAction.UNDO,
                "H" to KeyAction.HINT,
                "A" to KeyAction.AUTO_COMPLETE,
                "N" to KeyAction.NEW_GAME,
                "R" to KeyAction.RESTART_DEAL,
                "M" to KeyAction.MENU,
                "S" to KeyAction.STATS,
                "E" to KeyAction.FULL_REFRESH,
            )
        )
    }
}

object KeyBindingsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(bindings: KeyBindings): String = json.encodeToString(bindings)

    /** Falls back to defaults on corrupt input - the game must stay playable. */
    fun decode(text: String): KeyBindings = try {
        json.decodeFromString<KeyBindings>(text)
    } catch (e: SerializationException) {
        KeyBindings.defaults()
    } catch (e: IllegalArgumentException) {
        KeyBindings.defaults()
    }
}
