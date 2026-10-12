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

        val gPct = Math.round(acc.geminiRemaining * 100)
        val cPct = Math.round(acc.claudeRemaining * 100)

        val gReset = if (!acc.geminiReset.isNullOrEmpty() && acc.geminiReset != "N/A") " (${acc.geminiReset.takeLast(5)})" else ""
        val cReset = if (!acc.claudeReset.isNullOrEmpty() && acc.claudeReset != "N/A") " (${acc.claudeReset.takeLast(5)})" else ""

        views.setTextViewText(R.id.item_gemini_quota, "Gemini: $gPct%$gReset")
        views.setTextColor(R.id.item_gemini_quota, if (gPct < 20) Color.parseColor("#F38BA8") else Color.parseColor("#A6E3A1"))

        views.setTextViewText(R.id.item_claude_quota, "Claude: $cPct%$cReset")
        views.setTextColor(R.id.item_claude_quota, if (cPct < 20) Color.parseColor("#F38BA8") else Color.parseColor("#A6E3A1"))

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
