package com.jackque.solitaire.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.jackque.solitaire.MainActivity
import com.jackque.solitaire.R
import com.jackque.solitaire.data.DataStoreSolitaireStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Monochrome glance panel: cumulative score, the deal in progress, the
 * win/loss record and Grand Tour progress. Sized to work as a
 * full-screen cover-display panel (e.g. on the Motorola Razr exterior
 * screen); tapping anywhere opens the game. It refreshes whenever the
 * app saves (every move) and on the system's periodic update.
 */
class SolitaireWidgetProvider : AppWidgetProvider() {

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

    companion object {

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

        private suspend fun render(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
        ) {
            val store = DataStoreSolitaireStore(context)
            val content = WidgetTexts.from(store.loadStatistics(), store.loadSavedGame())
            val launchIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_solitaire)
                views.setTextViewText(R.id.widget_bank, content.bankLine)
                views.setTextViewText(R.id.widget_deal, content.dealLine)
                views.setTextViewText(R.id.widget_record, content.recordLine)
                views.setTextViewText(R.id.widget_tour, content.tourLine)
                views.setOnClickPendingIntent(R.id.widget_root, launchIntent)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
