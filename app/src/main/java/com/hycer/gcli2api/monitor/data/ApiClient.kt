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
                    MonitorSummary(0, 0, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, nowStr, emptyList())
                )
            }

            // 2. 查询各账号额度
            val details = mutableListOf<AccountQuotaDetail>()
            var totalGeminiWeekly = 0.0f
            var totalGemini5h = 0.0f
            var countGemini = 0

            var totalClaudeWeekly = 0.0f
            var totalClaude5h = 0.0f
            var countClaude = 0

            for (cred in credList) {
                val quotaUrl = "$baseUrl/creds/quota/${cred.filename}?mode=antigravity"
                val quotaReq = Request.Builder()
                    .url(quotaUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()

                var geminiWeekly: Float? = null
                var geminiWeeklyReset: String? = null
                var gemini5h: Float? = null
                var gemini5hReset: String? = null

                var claudeWeekly: Float? = null
                var claudeWeeklyReset: String? = null
                var claude5h: Float? = null
                var claude5hReset: String? = null

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
                                    val window = mObj.optString("window", null)
                                    val group = mObj.optString("group", null)
                                    modelList.add(ModelQuota(modelKey, rem, resetTime, resetTimeRaw, window, group))

                                    val lowerKey = modelKey.lowercase()
                                    val isWeekly = window.equals("weekly", ignoreCase = true) || lowerKey.contains("weekly")
                                    val is5h = window.equals("5h", ignoreCase = true) || lowerKey.contains("5h")

                                    // 分辨 Gemini 还是 Claude/3P
                                    val isGemini = lowerKey.contains("gemini")
                                    val isClaude = lowerKey.contains("claude") || lowerKey.contains("3p") || lowerKey.contains("gpt")

                                    if (isGemini) {
                                        if (isWeekly) {
                                            if (geminiWeekly == null || rem < geminiWeekly) {
                                                geminiWeekly = rem
                                                geminiWeeklyReset = resetTime
                                            }
                                        } else if (is5h) {
                                            if (gemini5h == null || rem < gemini5h) {
                                                gemini5h = rem
                                                gemini5hReset = resetTime
                                            }
                                        } else {
                                            // 旧格式兼容，无 weekly/5h 区分，默认当作 5h 与 weekly 的保守值
                                            if (geminiWeekly == null || rem < geminiWeekly) {
                                                geminiWeekly = rem
                                                geminiWeeklyReset = resetTime
                                            }
                                            if (gemini5h == null || rem < gemini5h) {
                                                gemini5h = rem
                                                gemini5hReset = resetTime
                                            }
                                        }
                                    } else if (isClaude) {
                                        if (isWeekly) {
                                            if (claudeWeekly == null || rem < claudeWeekly) {
                                                claudeWeekly = rem
                                                claudeWeeklyReset = resetTime
                                            }
                                        } else if (is5h) {
                                            if (claude5h == null || rem < claude5h) {
                                                claude5h = rem
                                                claude5hReset = resetTime
                                            }
                                        } else {
                                            if (claudeWeekly == null || rem < claudeWeekly) {
                                                claudeWeekly = rem
                                                claudeWeeklyReset = resetTime
                                            }
                                            if (claude5h == null || rem < claude5h) {
                                                claude5h = rem
                                                claude5hReset = resetTime
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        throw Exception("HTTP ${quotaResp.code}")
                    }
                } catch (e: Exception) {
                    // 若获取该账号额度失败，沿用上一次持久化缓存中的该账号数据
                    val lastSaved = ConfigManager.getCachedSummary(context)?.accountDetails?.find { it.filename == cred.filename }
                    if (lastSaved != null) {
                        geminiWeekly = lastSaved.geminiWeeklyRemaining
                        geminiWeeklyReset = lastSaved.geminiWeeklyReset
                        gemini5h = lastSaved.gemini5hRemaining
                        gemini5hReset = lastSaved.gemini5hReset

                        claudeWeekly = lastSaved.claudeWeeklyRemaining
                        claudeWeeklyReset = lastSaved.claudeWeeklyReset
                        claude5h = lastSaved.claude5hRemaining
                        claude5hReset = lastSaved.claude5hReset
                    }
                }

                val finalGWeekly = geminiWeekly ?: 1.0f
                val finalG5h = gemini5h ?: 1.0f
                val finalCWeekly = claudeWeekly ?: 1.0f
                val finalC5h = claude5h ?: 1.0f

                totalGeminiWeekly += finalGWeekly
                totalGemini5h += finalG5h
                countGemini++

                totalClaudeWeekly += finalCWeekly
                totalClaude5h += finalC5h
                countClaude++

                details.add(
                    AccountQuotaDetail(
                        filename = cred.filename,
                        email = cred.userEmail ?: cred.filename,
                        geminiWeeklyRemaining = finalGWeekly,
                        gemini5hRemaining = finalG5h,
                        geminiWeeklyReset = geminiWeeklyReset,
                        gemini5hReset = gemini5hReset,
                        claudeWeeklyRemaining = finalCWeekly,
                        claude5hRemaining = finalC5h,
                        claudeWeeklyReset = claudeWeeklyReset,
                        claude5hReset = claude5hReset,
                        allModels = modelList
                    )
                )
            }

            val avgGWeekly = if (countGemini > 0) totalGeminiWeekly / countGemini else 1.0f
            val avgG5h = if (countGemini > 0) totalGemini5h / countGemini else 1.0f
            val avgCWeekly = if (countClaude > 0) totalClaudeWeekly / countClaude else 1.0f
            val avgC5h = if (countClaude > 0) totalClaude5h / countClaude else 1.0f
            val validCount = credList.count { it.isValid }

            Result.success(
                MonitorSummary(
                    totalAccounts = credList.size,
                    validAccounts = validCount,
                    avgGeminiWeeklyRemaining = avgGWeekly,
                    avgGemini5hRemaining = avgG5h,
                    avgClaudeWeeklyRemaining = avgCWeekly,
                    avgClaude5hRemaining = avgC5h,
                    lastUpdated = nowStr,
                    accountDetails = details
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
