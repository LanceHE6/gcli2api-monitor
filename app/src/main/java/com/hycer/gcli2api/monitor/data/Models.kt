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
    val resetTimeRaw: String?
)

data class AccountQuotaDetail(
    val filename: String,
    val email: String,
    val geminiRemaining: Float, // 0.0 ~ 1.0
    val claudeRemaining: Float, // 0.0 ~ 1.0
    val geminiReset: String?,
    val claudeReset: String?,
    val allModels: List<ModelQuota>
)

data class MonitorSummary(
    val totalAccounts: Int,
    val validAccounts: Int,
    val avgGeminiRemaining: Float,
    val avgClaudeRemaining: Float,
    val lastUpdated: String,
    val accountDetails: List<AccountQuotaDetail>
)
