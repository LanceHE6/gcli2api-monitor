package com.hycer.gcli2api.monitor.data

data class CredentialItem(
    val filename: String,
    val userEmail: String?,
    val isValid: Boolean = true
)

data class ModelQuota(
    val modelName: String,
    val remaining: Float, // 0.0 ~ 1.0
    val resetTime: String?,
    val resetTimeRaw: String?,
    val window: String? = null,
    val group: String? = null
)

data class AccountQuotaDetail(
    val filename: String,
    val email: String,
    // Gemini
    val geminiWeeklyRemaining: Float,
    val gemini5hRemaining: Float,
    val geminiWeeklyReset: String?,
    val gemini5hReset: String?,
    // Claude / 3P
    val claudeWeeklyRemaining: Float,
    val claude5hRemaining: Float,
    val claudeWeeklyReset: String?,
    val claude5hReset: String?,
    // 兼容原单值
    val geminiRemaining: Float = minOf(geminiWeeklyRemaining, gemini5hRemaining),
    val claudeRemaining: Float = minOf(claudeWeeklyRemaining, claude5hRemaining),
    val geminiReset: String? = geminiWeeklyReset ?: gemini5hReset,
    val claudeReset: String? = claudeWeeklyReset ?: claude5hReset,
    val allModels: List<ModelQuota> = emptyList()
)

data class MonitorSummary(
    val totalAccounts: Int,
    val validAccounts: Int,
    val avgGeminiWeeklyRemaining: Float,
    val avgGemini5hRemaining: Float,
    val avgClaudeWeeklyRemaining: Float,
    val avgClaude5hRemaining: Float,
    // 兼容旧字段
    val avgGeminiRemaining: Float = minOf(avgGeminiWeeklyRemaining, avgGemini5hRemaining),
    val avgClaudeRemaining: Float = minOf(avgClaudeWeeklyRemaining, avgClaude5hRemaining),
    val lastUpdated: String,
    val accountDetails: List<AccountQuotaDetail>
)
