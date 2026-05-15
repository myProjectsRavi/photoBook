package com.photobook.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.photobook.app.MainActivity
import com.photobook.app.R
import com.photobook.app.feature.memories.MemoryStory

class OnThisDayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { widgetId ->
            bindWidget(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetId = widgetId,
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "photobook_widget"
        private const val KEY_TITLE = "widget_title"
        private const val KEY_SUBTITLE = "widget_subtitle"
        private const val KEY_QUERY = "widget_query"
        private const val KEY_STORY_IDS = "widget_story_ids"

        fun cacheStory(context: Context, story: MemoryStory?) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(
                    KEY_TITLE,
                    story?.title?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.widget_on_this_day_title),
                )
                .putString(
                    KEY_SUBTITLE,
                    story?.subtitle?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.widget_on_this_day_subtitle),
                )
                .putString(KEY_QUERY, story?.suggestedQuery?.ifBlank { "today" } ?: "today")
                .putString(KEY_STORY_IDS, story?.photoIds?.joinToString(",").orEmpty())
                .apply()
            refreshAll(context)
        }

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, OnThisDayWidgetProvider::class.java),
            )
            if (ids.isNotEmpty()) {
                val intent = Intent(context, OnThisDayWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }

        private fun bindWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val title = prefs.getString(
                KEY_TITLE,
                context.getString(R.string.widget_on_this_day_title),
            ).orEmpty()
            val subtitle = prefs.getString(
                KEY_SUBTITLE,
                context.getString(R.string.widget_on_this_day_subtitle),
            ).orEmpty()
            val query = prefs.getString(KEY_QUERY, "today").orEmpty()
            val storyIdsCsv = prefs.getString(KEY_STORY_IDS, "").orEmpty()

            val openIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_LAUNCH_QUERY, query)
                putExtra(MainActivity.EXTRA_WIDGET_STORY_IDS, storyIdsCsv)
                putExtra(MainActivity.EXTRA_WIDGET_STORY_TITLE, title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                openIntent,
                flags,
            )

            val views = RemoteViews(context.packageName, R.layout.widget_on_this_day).apply {
                setTextViewText(R.id.widgetTitle, title)
                setTextViewText(R.id.widgetSubtitle, subtitle)
                setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)
                setOnClickPendingIntent(R.id.widgetOpenAction, pendingIntent)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
