package com.shoplist.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.shoplist.app.MainActivity
import com.shoplist.app.R
import com.shoplist.app.ShopListApp
import com.shoplist.app.data.local.ListEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ListWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    refresh(context)
                } catch (e: Exception) {
                    // widget refresh is best-effort
                }
            }
        }

        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ListWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val items = ShopListApp.from(context).repo.listSnapshot()
            manager.updateAppWidget(ids, build(context, items))
        }

        private fun openApp(context: Context, route: String, code: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ROUTE, route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun build(context: Context, items: List<ListEntry>): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_list)
            v.setTextViewText(R.id.w_count, "${items.size} מוצרים ברשימה")
            val ids = intArrayOf(R.id.w_item1, R.id.w_item2, R.id.w_item3)
            ids.forEachIndexed { i, viewId ->
                val text = when {
                    i < items.size -> {
                        val e = items[i]
                        "☐ ${e.name}" + if (e.quantity > 1) " × ${e.quantity}" else ""
                    }
                    i == 0 -> "הרשימה ריקה"
                    else -> ""
                }
                v.setTextViewText(viewId, text)
            }
            val addIntent = Intent(context, QuickAddActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            v.setOnClickPendingIntent(
                R.id.w_add,
                PendingIntent.getActivity(
                    context, 10, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            v.setOnClickPendingIntent(R.id.w_shop, openApp(context, "shop", 11))
            v.setOnClickPendingIntent(R.id.w_open, openApp(context, "home", 12))
            v.setOnClickPendingIntent(R.id.w_title, openApp(context, "home", 13))
            return v
        }
    }
}
