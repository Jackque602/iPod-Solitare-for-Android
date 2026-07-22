package com.jackque.solitaire

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Compose UI tests. They run against the real activity with
 * real DataStore persistence, so the first step always deals a fresh game
 * through the menu to make the assertions deterministic.
 */
@RunWith(AndroidJUnit4::class)
class GameScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun freshGame() {
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("menu_new_game").performClick()
        // Mid-deal the new game asks for confirmation first.
        compose.waitForIdle()
        val confirm = compose.onAllNodesWithTag("confirm_deal")
        if (confirm.fetchSemanticsNodes().isNotEmpty()) {
            confirm.onFirst().performClick()
        }
        compose.waitForIdle()
    }

    @Test
    fun statusBarShowsScoreMovesTimerAndMenu() {
        compose.onNodeWithTag("bank").assertIsDisplayed()
        compose.onNodeWithTag("moves").assertIsDisplayed()
        compose.onNodeWithTag("timer").assertIsDisplayed()
        compose.onNodeWithTag("menu").assertIsDisplayed()
        compose.onNodeWithText("Moves 0").assertIsDisplayed()
    }

    @Test
    fun boardShowsAllPiles() {
        compose.onNodeWithTag("stock").assertIsDisplayed()
        compose.onNodeWithTag("waste").assertIsDisplayed()
        for (f in 0..3) {
            compose.onNodeWithTag("foundation$f").assertIsDisplayed()
        }
        for (col in 0..6) {
            compose.onNodeWithTag("tableau$col").assertIsDisplayed()
        }
        compose.onNodeWithTag("btn_undo").assertIsDisplayed()
        compose.onNodeWithTag("btn_hint").assertIsDisplayed()
    }

    @Test
    fun tappingStockDrawsACard() {
        compose.onNodeWithText("Moves 0").assertIsDisplayed()
        compose.onNodeWithTag("stock").performClick()
        compose.onNodeWithText("Moves 1").assertIsDisplayed()
    }

    @Test
    fun undoRevertsTheDraw() {
        compose.onNodeWithTag("stock").performClick()
        compose.onNodeWithText("Moves 1").assertIsDisplayed()
        compose.onNodeWithTag("btn_undo").performClick()
        compose.onNodeWithText("Moves 0").assertIsDisplayed()
    }

    @Test
    fun hintShowsAMessage() {
        compose.onNodeWithTag("btn_hint").performClick()
        compose.onNodeWithText("Hint:", substring = true).assertIsDisplayed()
    }

    @Test
    fun menuOpensAndShowsStatistics() {
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("menu_stats").performClick()
        compose.onNodeWithText("Cumulative score", substring = true).assertIsDisplayed()
    }
}
