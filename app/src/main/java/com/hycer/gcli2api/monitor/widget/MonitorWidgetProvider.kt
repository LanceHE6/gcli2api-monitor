package com.hycer.gcli2api.monitor.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.hycer.gcli2api.monitor.R
import com.hycer.gcli2api.monitor.data.ApiClient
import com.hycer.gcli2api.monitor.data.ConfigManager
import com.hycer.gcli2api.monitor.data.MonitorSummary
import com.hycer.gcli2api.monitor.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MonitorWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.hycer.gcli2api.monitor.ACTION_REFRESH"
        const val ACTION_ALARM_SYNC = "com.hycer.gcli2api.monitor.ACTION_ALARM_SYNC"
        private const val SYNC_INTERVAL_MS = 5 * 60 * 1000L // 5分钟精准同步

        var isRefreshing: Boolean = false

        fun startPeriodicAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, MonitorWidgetProvider::class.java).apply {
                action = ACTION_ALARM_SYNC
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 200, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAt = SystemClock.elapsedRealtime() + SYNC_INTERVAL_MS
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
        }

        fun updateAllWidgets(context: Context, appWidgetManager: AppWidgetManager) {
            val componentName = ComponentName(context, MonitorWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            // 1. 点击标题或摘要栏跳转到 App
            val appIntent = Intent(context, MainActivity::class.java)
            val appPendingIntent = PendingIntent.getActivity(
                context, 0, appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, appPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_summary_bar, appPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_empty_hint, appPendingIntent)

            // 2. 点击刷新按钮发送广播
            val refreshIntent = Intent(context, MonitorWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, 1, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

            // 3. 设置 ListView 适配器 Intent
            val serviceIntent = Intent(context, WidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_account_list, serviceIntent)
            views.setEmptyView(R.id.widget_account_list, R.id.widget_empty_hint)

            // 4. ListView 列表项点击也跳转到 App (PendingIntentTemplate)
            views.setPendingIntentTemplate(R.id.widget_account_list, appPendingIntent)

            // 5. 从持久化缓存读取数据
            val summary = ConfigManager.getCachedSummary(context)

            if (isRefreshing) {
                views.setTextViewText(R.id.widget_time, "刷新中...")
            } else {
                views.setTextViewText(R.id.widget_time, summary?.lastUpdated ?: "--:--")
            }

            if (summary != null) {
                views.setTextViewText(R.id.widget_total_accounts, "${summary.validAccounts} / ${summary.totalAccounts}")

                val gwPct = Math.round(summary.avgGeminiWeeklyRemaining * 100)
                val g5hPct = Math.round(summary.avgGemini5hRemaining * 100)
                val cwPct = Math.round(summary.avgClaudeWeeklyRemaining * 100)
                val c5hPct = Math.round(summary.avgClaude5hRemaining * 100)

                views.setTextViewText(R.id.widget_gemini_total, "$gwPct% / $g5hPct%")
                val gMin = minOf(gwPct, g5hPct)
                views.setTextColor(R.id.widget_gemini_total, if (gMin < 20) Color.parseColor("#F38BA8") else Color.parseColor("#A6E3A1"))

                views.setTextViewText(R.id.widget_claude_total, "$cwPct% / $c5hPct%")
                val cMin = minOf(cwPct, c5hPct)
                views.setTextColor(R.id.widget_claude_total, if (cMin < 20) Color.parseColor("#F38BA8") else Color.parseColor("#A6E3A1"))

                views.setViewVisibility(R.id.widget_account_list, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty_hint, View.GONE)
            } else {
                views.setTextViewText(R.id.widget_total_accounts, "- / -")
                views.setTextViewText(R.id.widget_gemini_total, "-%")
                views.setTextViewText(R.id.widget_claude_total, "-%")
                views.setViewVisibility(R.id.widget_account_list, View.GONE)
                views.setViewVisibility(R.id.widget_empty_hint, View.VISIBLE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_account_list)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        startPeriodicAlarm(context)
        SyncWorker.schedulePeriodicSync(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        startPeriodicAlarm(context)
        SyncWorker.schedulePeriodicSync(context)

        val summary = ConfigManager.getCachedSummary(context)
        if (summary == null && ConfigManager.isConfigured(context)) {
            fetchAndRefresh(context)
        } else {
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                fetchAndRefresh(context)
            }
            ACTION_ALARM_SYNC -> {
                fetchAndRefresh(context)
                // 安排下一次 5 分钟 alarm
                startPeriodicAlarm(context)
            }
        }
    }

    private fun fetchAndRefresh(context: Context) {
        if (!ConfigManager.isConfigured(context)) return

        isRefreshing = true
        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateAllWidgets(context, appWidgetManager)

        CoroutineScope(Dispatchers.IO).launch {
            val res = ApiClient.fetchSummary(context)
            res.onSuccess {
                ConfigManager.saveCachedSummary(context, it)
            }
            isRefreshing = false
            launch(Dispatchers.Main) {
                updateAllWidgets(context, appWidgetManager)
            }
        }
    }
}
