package com.hycer.gcli2api.monitor.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object ConfigManager {
    private const val PREFS_NAME = "gcli2api_monitor_config"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_CACHED_SUMMARY = "cached_summary_json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getBaseUrl(context: Context): String {
        return getPrefs(context).getString(KEY_BASE_URL, "") ?: ""
    }

    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }

    fun saveConfig(context: Context, baseUrl: String, apiKey: String) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        getPrefs(context).edit()
            .putString(KEY_BASE_URL, cleanUrl)
            .putString(KEY_API_KEY, apiKey.trim())
            .apply()
    }

    fun isConfigured(context: Context): Boolean {
        return getBaseUrl(context).isNotEmpty() && getApiKey(context).isNotEmpty()
    }

    fun saveCachedSummary(context: Context, summary: MonitorSummary) {
        val json = JSONObject()
        json.put("totalAccounts", summary.totalAccounts)
        json.put("validAccounts", summary.validAccounts)
        json.put("avgGeminiWeeklyRemaining", summary.avgGeminiWeeklyRemaining.toDouble())
        json.put("avgGemini5hRemaining", summary.avgGemini5hRemaining.toDouble())
        json.put("avgClaudeWeeklyRemaining", summary.avgClaudeWeeklyRemaining.toDouble())
        json.put("avgClaude5hRemaining", summary.avgClaude5hRemaining.toDouble())
        json.put("avgGeminiRemaining", summary.avgGeminiRemaining.toDouble())
        json.put("avgClaudeRemaining", summary.avgClaudeRemaining.toDouble())
        json.put("lastUpdated", summary.lastUpdated)

        val arr = JSONArray()
        for (acc in summary.accountDetails) {
            val item = JSONObject()
            item.put("filename", acc.filename)
            item.put("email", acc.email)
            item.put("geminiWeeklyRemaining", acc.geminiWeeklyRemaining.toDouble())
            item.put("gemini5hRemaining", acc.gemini5hRemaining.toDouble())
            if (acc.geminiWeeklyReset != null) item.put("geminiWeeklyReset", acc.geminiWeeklyReset)
            if (acc.gemini5hReset != null) item.put("gemini5hReset", acc.gemini5hReset)

            item.put("claudeWeeklyRemaining", acc.claudeWeeklyRemaining.toDouble())
            item.put("claude5hRemaining", acc.claude5hRemaining.toDouble())
            if (acc.claudeWeeklyReset != null) item.put("claudeWeeklyReset", acc.claudeWeeklyReset)
            if (acc.claude5hReset != null) item.put("claude5hReset", acc.claude5hReset)

            // 兼容旧字段
            item.put("geminiRemaining", acc.geminiRemaining.toDouble())
            item.put("claudeRemaining", acc.claudeRemaining.toDouble())
            if (acc.geminiReset != null) item.put("geminiReset", acc.geminiReset)
            if (acc.claudeReset != null) item.put("claudeReset", acc.claudeReset)
            arr.put(item)
        }
        json.put("accountDetails", arr)

        getPrefs(context).edit().putString(KEY_CACHED_SUMMARY, json.toString()).apply()
    }

    fun getCachedSummary(context: Context): MonitorSummary? {
        val str = getPrefs(context).getString(KEY_CACHED_SUMMARY, null) ?: return null
        return try {
            val json = JSONObject(str)
            val totalAccounts = json.optInt("totalAccounts", 0)
            val validAccounts = json.optInt("validAccounts", 0)
            val avgGWeekly = json.optDouble("avgGeminiWeeklyRemaining", json.optDouble("avgGeminiRemaining", 1.0)).toFloat()
            val avgG5h = json.optDouble("avgGemini5hRemaining", json.optDouble("avgGeminiRemaining", 1.0)).toFloat()
            val avgCWeekly = json.optDouble("avgClaudeWeeklyRemaining", json.optDouble("avgClaudeRemaining", 1.0)).toFloat()
            val avgC5h = json.optDouble("avgClaude5hRemaining", json.optDouble("avgClaudeRemaining", 1.0)).toFloat()
            val lastUpdated = json.optString("lastUpdated", "--:--")

            val arr = json.optJSONArray("accountDetails") ?: JSONArray()
            val details = mutableListOf<AccountQuotaDetail>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val gOld = item.optDouble("geminiRemaining", 1.0).toFloat()
                val cOld = item.optDouble("claudeRemaining", 1.0).toFloat()
                val gWeekly = item.optDouble("geminiWeeklyRemaining", gOld.toDouble()).toFloat()
                val g5h = item.optDouble("gemini5hRemaining", gOld.toDouble()).toFloat()
                val cWeekly = item.optDouble("claudeWeeklyRemaining", cOld.toDouble()).toFloat()
                val c5h = item.optDouble("claude5hRemaining", cOld.toDouble()).toFloat()
                val gReset = item.optString("geminiReset", null)
                val cReset = item.optString("claudeReset", null)

                details.add(
                    AccountQuotaDetail(
                        filename = item.optString("filename"),
                        email = item.optString("email"),
                        geminiWeeklyRemaining = gWeekly,
                        gemini5hRemaining = g5h,
                        geminiWeeklyReset = item.optString("geminiWeeklyReset", gReset),
                        gemini5hReset = item.optString("gemini5hReset", gReset),
                        claudeWeeklyRemaining = cWeekly,
                        claude5hRemaining = c5h,
                        claudeWeeklyReset = item.optString("claudeWeeklyReset", cReset),
                        claude5hReset = item.optString("claude5hReset", cReset),
                        allModels = emptyList()
                    )
                )
            }
            MonitorSummary(
                totalAccounts = totalAccounts,
                validAccounts = validAccounts,
                avgGeminiWeeklyRemaining = avgGWeekly,
                avgGemini5hRemaining = avgG5h,
                avgClaudeWeeklyRemaining = avgCWeekly,
                avgClaude5hRemaining = avgC5h,
                lastUpdated = lastUpdated,
                accountDetails = details
            )
        } catch (e: Exception) {
            null
        }
    }
}
