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
        json.put("avgGeminiRemaining", summary.avgGeminiRemaining.toDouble())
        json.put("avgClaudeRemaining", summary.avgClaudeRemaining.toDouble())
        json.put("lastUpdated", summary.lastUpdated)

        val arr = JSONArray()
        for (acc in summary.accountDetails) {
            val item = JSONObject()
            item.put("filename", acc.filename)
            item.put("email", acc.email)
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
            val avgGemini = json.optDouble("avgGeminiRemaining", 1.0).toFloat()
            val avgClaude = json.optDouble("avgClaudeRemaining", 1.0).toFloat()
            val lastUpdated = json.optString("lastUpdated", "--:--")

            val arr = json.optJSONArray("accountDetails") ?: JSONArray()
            val details = mutableListOf<AccountQuotaDetail>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                details.add(
                    AccountQuotaDetail(
                        filename = item.optString("filename"),
                        email = item.optString("email"),
                        geminiRemaining = item.optDouble("geminiRemaining", 1.0).toFloat(),
                        claudeRemaining = item.optDouble("claudeRemaining", 1.0).toFloat(),
                        geminiReset = item.optString("geminiReset", null),
                        claudeReset = item.optString("claudeReset", null),
                        allModels = emptyList()
                    )
                )
            }
            MonitorSummary(totalAccounts, validAccounts, avgGemini, avgClaude, lastUpdated, details)
        } catch (e: Exception) {
            null
        }
    }
}
