package com.htmlwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.*

class HtmlWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.htmlwidget.REFRESH"
        const val EXTRA_ID = "wid"

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_layout)
            val name = Prefs.getName(ctx, id)
            val uri = Prefs.getUri(ctx, id)

            views.setTextViewText(R.id.tv_name, name)
            views.setTextViewText(R.id.tv_time, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))

            // Dokunca yenile
            val pi = refreshPendingIntent(ctx, id)
            views.setOnClickPendingIntent(R.id.iv_content, pi)
            views.setOnClickPendingIntent(R.id.ll_status, pi)

            if (uri == null) {
                views.setViewVisibility(R.id.iv_content, View.GONE)
                views.setViewVisibility(R.id.ll_status, View.VISIBLE)
                views.setTextViewText(R.id.tv_status, ctx.getString(R.string.no_file))
                mgr.updateAppWidget(id, views)
                return
            }

            // Yükleniyor göster
            views.setViewVisibility(R.id.iv_content, View.GONE)
            views.setViewVisibility(R.id.ll_status, View.VISIBLE)
            views.setTextViewText(R.id.tv_status, ctx.getString(R.string.loading))
            mgr.updateAppWidget(id, views)

            // Ayarları oku
            val wDp = Prefs.getWidthDp(ctx, id)
            val hDp = Prefs.getHeightDp(ctx, id)
            val wPx = HtmlRenderer.dpToPx(ctx, wDp)
            val hPx = HtmlRenderer.dpToPx(ctx, hDp)
            val scrollX = Prefs.getScrollX(ctx, id)
            val scrollY = Prefs.getScrollY(ctx, id)
            val zoom = Prefs.getZoom(ctx, id)
            val delayMs = Prefs.getDelayMs(ctx, id)

            HtmlRenderer.render(ctx, uri, wPx, hPx, scrollX, scrollY, zoom, delayMs) { bmp ->
                val v2 = RemoteViews(ctx.packageName, R.layout.widget_layout)
                v2.setTextViewText(R.id.tv_name, name)
                v2.setTextViewText(R.id.tv_time, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
                v2.setOnClickPendingIntent(R.id.iv_content, pi)
                v2.setOnClickPendingIntent(R.id.ll_status, pi)

                if (bmp != null) {
                    v2.setImageViewBitmap(R.id.iv_content, bmp)
                    v2.setViewVisibility(R.id.iv_content, View.VISIBLE)
                    v2.setViewVisibility(R.id.ll_status, View.GONE)
                } else {
                    v2.setViewVisibility(R.id.iv_content, View.GONE)
                    v2.setViewVisibility(R.id.ll_status, View.VISIBLE)
                    v2.setTextViewText(R.id.tv_status, ctx.getString(R.string.error))
                }
                mgr.updateAppWidget(id, v2)
            }
        }

        fun scheduleAlarm(ctx: Context, id: Int) {
            val ms = Prefs.getIntervalMs(ctx, id)
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = refreshPendingIntent(ctx, id + 50000)
            am.cancel(pi)
            am.setRepeating(AlarmManager.RTC, System.currentTimeMillis() + ms, ms, pi)
        }

        fun cancelAlarm(ctx: Context, id: Int) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(refreshPendingIntent(ctx, id + 50000))
        }

        private fun refreshPendingIntent(ctx: Context, reqCode: Int): PendingIntent {
            val intent = Intent(ctx, HtmlWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                putExtra(EXTRA_ID, reqCode)
            }
            return PendingIntent.getBroadcast(
                ctx, reqCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            update(ctx, mgr, id)
            scheduleAlarm(ctx, id)
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_REFRESH) {
            val id = intent.getIntExtra(EXTRA_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                update(ctx, AppWidgetManager.getInstance(ctx), id)
            }
        }
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        ids.forEach { id ->
            cancelAlarm(ctx, id)
            Prefs.remove(ctx, id)
        }
    }
}
