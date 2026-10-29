package com.hycer.gcli2api.monitor.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.hycer.gcli2api.monitor.R
import com.hycer.gcli2api.monitor.data.AccountQuotaDetail
import com.hycer.gcli2api.monitor.data.ConfigManager

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WidgetItemFactory(applicationContext)
    }
}

class WidgetItemFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var accountList: List<AccountQuotaDetail> = emptyList()

    override fun onCreate() {
        accountList = ConfigManager.getCachedSummary(context)?.accountDetails ?: emptyList()
    }

    override fun onDataSetChanged() {
        accountList = ConfigManager.getCachedSummary(context)?.accountDetails ?: emptyList()
    }

    override fun onDestroy() {}

    override fun getCount(): Int = accountList.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_item)
        if (position >= accountList.size) return views

        val acc = accountList[position]
        // 只显示账号名，去除邮箱后缀及多余字符
        val displayName = if (acc.email.contains("@")) {
            acc.email.substringBefore("@")
        } else {
            acc.email.substringBefore(".")
        }

        views.setTextViewText(R.id.item_email, displayName)

        val gwPct = Math.round(acc.geminiWeeklyRemaining * 100)
        val g5hPct = Math.round(acc.gemini5hRemaining * 100)
        val cwPct = Math.round(acc.claudeWeeklyRemaining * 100)
        val c5hPct = Math.round(acc.claude5hRemaining * 100)

        val gwReset = if (!acc.geminiWeeklyReset.isNullOrEmpty() && acc.geminiWeeklyReset != "N/A") " (${acc.geminiWeeklyReset.takeLast(5)})" else ""
        val g5hReset = if (!acc.gemini5hReset.isNullOrEmpty() && acc.gemini5hReset != "N/A") " (${acc.gemini5hReset.takeLast(5)})" else ""
        val cwReset = if (!acc.claudeWeeklyReset.isNullOrEmpty() && acc.claudeWeeklyReset != "N/A") " (${acc.claudeWeeklyReset.takeLast(5)})" else ""
        val c5hReset = if (!acc.claude5hReset.isNullOrEmpty() && acc.claude5hReset != "N/A") " (${acc.claude5hReset.takeLast(5)})" else ""

        views.setTextViewText(R.id.item_gemini_weekly, "G周: $gwPct%$gwReset")
        views.setTextColor(R.id.item_gemini_weekly, if (gwPct < 20) Color.parseColor("#F38BA8") else Color.parseColor("#A6E3A1"))

        views.setTextViewText(R.id.item_gemini_5h, "G 5h: $g5hPct%$g5hReset")
        views.setTextColor(R.id.item_gemini_5h, if (g5hPct < 20) Color.parseColor("#F38BA8") else Color.parseColor("#A6E3A1"))

        views.setTextViewText(R.id.item_claude_weekly, "C周: $cwPct%$cwReset")
        views.setTextColor(R.id.item_claude_weekly, if (cwPct < 20) Color.parseColor("#F38BA8") else Color.parseColor("#A6E3A1"))

        views.setTextViewText(R.id.item_claude_5h, "C 5h: $c5hPct%$c5hReset")
        views.setTextColor(R.id.item_claude_5h, if (c5hPct < 20) Color.parseColor("#F38BA8") else Color.parseColor("#A6E3A1"))

        // 点击卡片直接唤起 App
        val fillInIntent = Intent()
        views.setOnClickFillInIntent(R.id.item_root, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
