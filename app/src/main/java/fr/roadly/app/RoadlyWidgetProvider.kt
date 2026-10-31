package fr.roadly.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Widget « Roadly » : trois raccourcis (domicile, travail, recherche) qui ouvrent l'application avec l'action correspondante. */
class RoadlyWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, build(context)) }
    }

    private fun build(context: Context): RemoteViews = RemoteViews(context.packageName, R.layout.widget_roadly).apply {
        setOnClickPendingIntent(R.id.widget_home, pending(context, ExternalIntents.ACTION_HOME, 1))
        setOnClickPendingIntent(R.id.widget_work, pending(context, ExternalIntents.ACTION_WORK, 2))
        setOnClickPendingIntent(R.id.widget_search, pending(context, ExternalIntents.ACTION_SEARCH, 3))
    }

    private fun pending(context: Context, action: String, code: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
