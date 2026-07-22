package com.jackque.solitaire.engine.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyBindingsTest {

    @Test
    fun `defaults cover every action`() {
        val defaults = KeyBindings.defaults()
        for (action in KeyAction.entries) {
            assertTrue(defaults.keysFor(action).isNotEmpty(), "no default key for $action")
        }
    }

    @Test
    fun `lookups are case insensitive`() {
        val defaults = KeyBindings.defaults()
        assertEquals(KeyAction.DRAW, defaults.actionFor("space"))
        assertEquals(KeyAction.DRAW, defaults.actionFor("SPACE"))
        assertNull(defaults.actionFor("F12"))
    }

    @Test
    fun `rebinding steals the key from its previous action`() {
        val bindings = KeyBindings.defaults().rebind(KeyAction.HINT, "SPACE")
        assertEquals(KeyAction.HINT, bindings.actionFor("SPACE"))
        assertTrue("SPACE" !in bindings.keysFor(KeyAction.DRAW))
        // DRAW still reachable through its other default key.
        assertTrue(bindings.keysFor(KeyAction.DRAW).isNotEmpty())
    }

    @Test
    fun `clear removes all keys of an action`() {
        val bindings = KeyBindings.defaults().clear(KeyAction.UNDO)
        assertTrue(bindings.keysFor(KeyAction.UNDO).isEmpty())
    }

    @Test
    fun `bindings roundtrip through json`() {
        val custom = KeyBindings.defaults().rebind(KeyAction.MENU, "TAB")
        assertEquals(custom, KeyBindingsCodec.decode(KeyBindingsCodec.encode(custom)))
    }

    @Test
    fun `corrupt bindings fall back to defaults`() {
        assertEquals(KeyBindings.defaults(), KeyBindingsCodec.decode("truncated{"))
        assertEquals(KeyBindings.defaults(), KeyBindingsCodec.decode("""{"bindings":{"Q":"NOT_AN_ACTION"}}"""))
    }
}
