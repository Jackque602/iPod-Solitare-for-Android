package com.jackque.solitaire.widget

import com.jackque.solitaire.engine.KlondikeGame
import com.jackque.solitaire.engine.SavedGame
import com.jackque.solitaire.engine.achievements.AchievementsState
import com.jackque.solitaire.engine.stats.Statistics
import com.jackque.solitaire.ui.moneyText

/** The widget's lines, kept as a pure function so they are JVM-testable. */
data class WidgetContent(
    val bankLine: String,
    val dealLine: String,
    val recordLine: String,
    val tourLine: String,
)

object WidgetTexts {

    fun from(stats: Statistics, saved: SavedGame?): WidgetContent {
        val restored = saved?.let { KlondikeGame.restore(it) }
        val dealLine = if (restored != null && !restored.state.isWon) {
            "Deal in progress · ${restored.state.moveCount} moves · " +
                "${restored.state.foundationCardCount}/52 up"
        } else {
            "Ready for a new deal"
        }
        return WidgetContent(
            bankLine = moneyText(stats.bank),
            dealLine = dealLine,
            recordLine = "Wins ${stats.wins} · Losses ${stats.losses}",
            tourLine = "Grand Tour ${stats.achievements.aceConfigurationsSeen.size}" +
                "/${AchievementsState.ACE_CONFIGURATION_TOTAL}",
        )
    }
}
