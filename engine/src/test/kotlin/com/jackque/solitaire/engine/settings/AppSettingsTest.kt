package com.jackque.solitaire.engine.settings

import com.jackque.solitaire.engine.DrawMode
import com.jackque.solitaire.engine.input.KeyAction
import com.jackque.solitaire.engine.input.KeyBindings
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTest {

    @Test
    fun `defaults are e-ink first with inverted red suits and draw three`() {
        val settings = AppSettingsCodec.decode("")
        assertEquals(true, settings.eInkMode)
        assertEquals(SuitStyle.INVERTED, settings.suitStyle)
        assertEquals(TimerDetail.MINUTES, settings.timerDetail)
        assertEquals(DrawMode.DRAW_THREE, settings.drawMode)
        assertEquals(AppSettings.CURRENT_VERSION, settings.version)
    }

    @Test
    fun `settings roundtrip through json`() {
        val custom = AppSettings(
            drawMode = DrawMode.DRAW_THREE,
            eInkMode = false,
            suitStyle = SuitStyle.LETTERS,
            timerDetail = TimerDetail.SECONDS,
            keyBindings = KeyBindings.defaults().rebind(KeyAction.HINT, "Q"),
        )
        val decoded = AppSettingsCodec.decode(AppSettingsCodec.encode(custom))
        assertEquals(custom.copy(version = AppSettings.CURRENT_VERSION), decoded)
    }

    @Test
    fun `v1 files on the old OUTLINE default migrate to INVERTED`() {
        val decoded = AppSettingsCodec.decode("""{"suitStyle":"OUTLINE","eInkMode":true}""")
        assertEquals(SuitStyle.INVERTED, decoded.suitStyle)
        assertEquals(AppSettings.CURRENT_VERSION, decoded.version)
    }

    @Test
    fun `v2 files keep a deliberately chosen OUTLINE`() {
        val decoded = AppSettingsCodec.decode("""{"version":2,"suitStyle":"OUTLINE"}""")
        assertEquals(SuitStyle.OUTLINE, decoded.suitStyle)
    }

    @Test
    fun `v2 files on the old DRAW_ONE default migrate to DRAW_THREE`() {
        val decoded = AppSettingsCodec.decode("""{"version":2,"drawMode":"DRAW_ONE"}""")
        assertEquals(DrawMode.DRAW_THREE, decoded.drawMode)
        assertEquals(AppSettings.CURRENT_VERSION, decoded.version)
    }

    @Test
    fun `v3 files keep a deliberately chosen DRAW_ONE`() {
        val decoded = AppSettingsCodec.decode("""{"version":3,"drawMode":"DRAW_ONE"}""")
        assertEquals(DrawMode.DRAW_ONE, decoded.drawMode)
    }

    @Test
    fun `v1 files migrate both defaults at once`() {
        val decoded = AppSettingsCodec.decode("""{"suitStyle":"OUTLINE","drawMode":"DRAW_ONE"}""")
        assertEquals(SuitStyle.INVERTED, decoded.suitStyle)
        assertEquals(DrawMode.DRAW_THREE, decoded.drawMode)
    }

    @Test
    fun `migration does not touch other v1 choices`() {
        val decoded = AppSettingsCodec.decode("""{"suitStyle":"LETTERS","drawMode":"DRAW_THREE"}""")
        assertEquals(SuitStyle.LETTERS, decoded.suitStyle)
        assertEquals(DrawMode.DRAW_THREE, decoded.drawMode)
    }

    @Test
    fun `corrupt settings fall back to defaults`() {
        assertEquals(SuitStyle.INVERTED, AppSettingsCodec.decode("{").suitStyle)
        assertEquals(SuitStyle.INVERTED, AppSettingsCodec.decode("""{"suitStyle":"MAUVE"}""").suitStyle)
    }
}
