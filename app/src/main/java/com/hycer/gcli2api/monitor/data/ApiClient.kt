package com.hycer.gcli2api.monitor.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetchSummary(context: Context): Result<MonitorSummary> = withContext(Dispatchers.IO) {
        val baseUrl = ConfigManager.getBaseUrl(context)
        val apiKey = ConfigManager.getApiKey(context)

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            return@withContext Result.failure(Exception("请先在应用内配置服务器地址和 API Key"))
        }

        try {
            // 1. 获取账号状态列表
            val statusUrl = "$baseUrl/creds/status?mode=antigravity"
            val statusRequest = Request.Builder()
                .url(statusUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            val statusResponse = client.newCall(statusRequest).execute()
            if (!statusResponse.isSuccessful) {
                return@withContext Result.failure(Exception("获取账号列表失败 HTTP ${statusResponse.code}"))
            }

            val statusBody = statusResponse.body?.string() ?: ""
            val statusJson = JSONObject(statusBody)
            val itemsArray = statusJson.optJSONArray("items") ?: org.json.JSONArray()

            val credList = mutableListOf<CredentialItem>()
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.getJSONObject(i)
                val fname = item.optString("filename", "")
                val email = item.optString("user_email", null)
                val disabled = item.optBoolean("disabled", false)
                if (fname.isNotEmpty()) {
                    credList.add(CredentialItem(fname, email, !disabled))
                }
            }

            val nowStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            if (credList.isEmpty()) {
                return@withContext Result.success(
                    MonitorSummary(0, 0, 1.0f, 1.0f, nowStr, emptyList())
                )
            }

            // 2. 查询各账号额度
            val details = mutableListOf<AccountQuotaDetail>()
            var totalGemini = 0.0f
            var countGemini = 0
            var totalClaude = 0.0f
            var countClaude = 0

            for (cred in credList) {
                val quotaUrl = "$baseUrl/creds/quota/${cred.filename}?mode=antigravity"
                val quotaReq = Request.Builder()
                    .url(quotaUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()

                var minGemini: Float? = null
                var minClaude: Float? = null
                var geminiReset: String? = null
                var claudeReset: String? = null
                val modelList = mutableListOf<ModelQuota>()

                try {
                    val quotaResp = client.newCall(quotaReq).execute()
                    if (quotaResp.isSuccessful) {
                        val qBody = quotaResp.body?.string() ?: "{}"
                        val qJson = JSONObject(qBody)
                        val modelsObj = if (qJson.has("models")) qJson.optJSONObject("models") else qJson

                        if (modelsObj != null) {
                            val keys = modelsObj.keys()
                            while (keys.hasNext()) {
                                val modelKey = keys.next()
                                val mObj = modelsObj.optJSONObject(modelKey)
                                if (mObj != null) {
                                    val rem = mObj.optDouble("remaining", 1.0).toFloat()
                                    val resetTime = mObj.optString("resetTime", null)
                                    val resetTimeRaw = mObj.optString("resetTimeRaw", null)
                                    modelList.add(ModelQuota(modelKey, rem, resetTime, resetTimeRaw))

                                    val lowerKey = modelKey.lowercase()
                                    if (lowerKey.contains("claude")) {
                                        if (minClaude == null || rem < minClaude) {
                                            minClaude = rem
                                            claudeReset = resetTime
                                        }
                                    } else if (lowerKey.contains("gemini")) {
                                        if (minGemini == null || rem < minGemini) {
                                            minGemini = rem
                                            geminiReset = resetTime
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        throw Exception("HTTP ${quotaResp.code}")
                    }
                } catch (e: Exception) {
                    // 若获取该账号额度失败，沿用上一次持久化缓存中的该账号数据，避免误置为 100%
                    val lastSaved = ConfigManager.getCachedSummary(context)?.accountDetails?.find { it.filename == cred.filename }
                    if (lastSaved != null) {
                        minGemini = lastSaved.geminiRemaining
                        minClaude = lastSaved.claudeRemaining
                        geminiReset = lastSaved.geminiReset
                        claudeReset = lastSaved.claudeReset
                    }
                }

                val finalGemini = minGemini ?: 1.0f
                val finalClaude = minClaude ?: 1.0f

                totalGemini += finalGemini
                countGemini++
                totalClaude += finalClaude
                countClaude++

                details.add(
                    AccountQuotaDetail(
                        filename = cred.filename,
                        email = cred.userEmail ?: cred.filename,
                        geminiRemaining = finalGemini,
                        claudeRemaining = finalClaude,
                        geminiReset = geminiReset,
                        claudeReset = claudeReset,
                        allModels = modelList
                    )
                )
            }

            val avgGemini = if (countGemini > 0) totalGemini / countGemini else 1.0f
            val avgClaude = if (countClaude > 0) totalClaude / countClaude else 1.0f
            val validCount = credList.count { it.isValid }

            Result.success(
                MonitorSummary(
                    totalAccounts = credList.size,
                    validAccounts = validCount,
                    avgGeminiRemaining = avgGemini,
                    avgClaudeRemaining = avgClaude,
                    lastUpdated = nowStr,
                    accountDetails = details
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
