package com.jackque.solitaire.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.jackque.solitaire.R
import com.jackque.solitaire.data.DataStoreSolitaireStore
import com.jackque.solitaire.engine.Klondike
import com.jackque.solitaire.engine.KlondikeGame
import com.jackque.solitaire.engine.Scoring
import com.jackque.solitaire.engine.input.Selection
import com.jackque.solitaire.engine.input.Zone
import com.jackque.solitaire.ui.moneyText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A tap-playable Solitaire board as a home-screen widget, sized so
 * Motorola's cover display can host it as a full-screen panel. Taps on
 * piles select and move cards through the same engine and save file as
 * the app; Undo/Auto/New round out the controls. The status text opens
 * the full app for everything else (hints, settings, statistics).
 */
class SolitaireWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TAP) {
            val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    runCatching { WidgetGame.handleAction(context.applicationContext, token) }
                    updateAll(context.applicationContext)
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                render(context.applicationContext, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    companion object {
        const val ACTION_TAP = "com.jackque.solitaire.widget.TAP"
        const val EXTRA_TOKEN = "token"

        /** Refresh every placed widget; no-op (and cheap) when none exist. */
        suspend fun updateAll(context: Context) {
            runCatching {
                withContext(Dispatchers.Default) {
                    val manager = AppWidgetManager.getInstance(context) ?: return@withContext
                    val ids = manager.getAppWidgetIds(
                        ComponentName(context, SolitaireWidgetProvider::class.java),
                    )
                    if (ids.isNotEmpty()) render(context.applicationContext, manager, ids)
                }
            }
        }

        private fun tapIntent(context: Context, token: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                token.hashCode(),
                Intent(context, SolitaireWidgetProvider::class.java)
                    .setAction(ACTION_TAP)
                    .setData(Uri.parse("solitaire://tap/$token"))
                    .putExtra(EXTRA_TOKEN, token),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun openAppIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, com.jackque.solitaire.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val store = DataStoreSolitaireStore(context)
            val stats = store.loadStatistics()
            val saved = store.loadSavedGame()
            val game = saved?.let { KlondikeGame.restore(it) }
            val selection = if (game == null) null else WidgetPlay.sanitize(
                game.state,
                WidgetPlay.decodeSelection(store.loadWidgetSelection()),
            )
            val density = context.resources.displayMetrics.density

            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    .takeIf { it > 0 } ?: 340
                val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                    .takeIf { it > 0 } ?: 400

                val cellW = ((widthDp - 14) / 7f * density).toInt()
                val cardH = (cellW * 1.42f).coerceAtMost(heightDp * density * 0.26f).toInt()
                val tableauH = ((heightDp - 46) * density - cardH).toInt().coerceAtLeast(cardH)

                val views = RemoteViews(context.packageName, R.layout.widget_board)
                views.setOnClickPendingIntent(R.id.widget_status, openAppIntent(context))
                views.setOnClickPendingIntent(R.id.widget_btn_undo, tapIntent(context, WidgetGame.TOKEN_UNDO))
                views.setOnClickPendingIntent(R.id.widget_btn_auto, tapIntent(context, WidgetGame.TOKEN_AUTO))
                views.setOnClickPendingIntent(R.id.widget_btn_new, tapIntent(context, WidgetGame.TOKEN_NEW))

                if (game == null) {
                    views.setTextViewText(R.id.widget_status, moneyText(stats.bank))
                    views.setTextViewText(
                        R.id.widget_message,
                        "Ready - New deals for \$52 · tap the score for the full app",
                    )
                    views.setViewVisibility(R.id.widget_message, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.widget_top_row, android.view.View.GONE)
                    views.setViewVisibility(R.id.widget_tableau_row, android.view.View.GONE)
                    views.setViewVisibility(R.id.widget_btn_auto, android.view.View.GONE)
                } else {
                    val state = game.state
                    views.setTextViewText(
                        R.id.widget_status,
                        "${moneyText(stats.bank)} · ${state.moveCount} moves",
                    )
                    if (state.isWon) {
                        views.setTextViewText(R.id.widget_message, "You won! +\$${Scoring.WIN_NET}")
                        views.setViewVisibility(R.id.widget_message, android.view.View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.widget_message, android.view.View.GONE)
                    }
                    views.setViewVisibility(R.id.widget_top_row, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.widget_tableau_row, android.view.View.VISIBLE)
                    views.setViewVisibility(
                        R.id.widget_btn_auto,
                        if (Klondike.isAutoCompleteAvailable(state)) android.view.View.VISIBLE
                        else android.view.View.GONE,
                    )

                    val foundationIds =
                        listOf(R.id.widget_f0, R.id.widget_f1, R.id.widget_f2, R.id.widget_f3)
                    foundationIds.forEachIndexed { f, viewId ->
                        val sel = selection != null && selection.zone == Zone.FOUNDATION && selection.index == f
                        views.setImageViewBitmap(
                            viewId,
                            BoardRenderer.topCardBitmap(
                                state.foundations[f].lastOrNull(), "A", sel, cellW, cardH,
                            ),
                        )
                        views.setOnClickPendingIntent(viewId, tapIntent(context, "f$f"))
                    }
                    views.setImageViewBitmap(
                        R.id.widget_stock,
                        BoardRenderer.stockBitmap(state.stock.size, state.waste.isNotEmpty(), cellW, cardH),
                    )
                    views.setOnClickPendingIntent(R.id.widget_stock, tapIntent(context, "stock"))
                    views.setImageViewBitmap(
                        R.id.widget_waste,
                        BoardRenderer.topCardBitmap(
                            state.waste.lastOrNull(), "",
                            selection?.zone == Zone.WASTE, cellW, cardH,
                        ),
                    )
                    views.setOnClickPendingIntent(R.id.widget_waste, tapIntent(context, "waste"))

                    val tableauIds = listOf(
                        R.id.widget_t0, R.id.widget_t1, R.id.widget_t2, R.id.widget_t3,
                        R.id.widget_t4, R.id.widget_t5, R.id.widget_t6,
                    )
                    tableauIds.forEachIndexed { col, viewId ->
                        val selectedFrom = selection
                            ?.takeIf { it.zone == Zone.TABLEAU && it.index == col }
                            ?.cardIndex
                        views.setImageViewBitmap(
                            viewId,
                            BoardRenderer.columnBitmap(state.tableau[col], selectedFrom, cellW, tableauH),
                        )
                        views.setOnClickPendingIntent(viewId, tapIntent(context, "t$col"))
                    }
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}
