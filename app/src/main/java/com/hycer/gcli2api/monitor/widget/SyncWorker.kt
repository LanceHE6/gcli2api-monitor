package com.hycer.gcli2api.monitor.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.*
import com.hycer.gcli2api.monitor.data.ApiClient
import com.hycer.gcli2api.monitor.data.ConfigManager
import java.util.concurrent.TimeUnit

class SyncWorker(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!ConfigManager.isConfigured(context)) {
            return Result.success()
        }

        val res = ApiClient.fetchSummary(context)
        return if (res.isSuccess) {
            val summary = res.getOrNull()
            if (summary != null) {
                ConfigManager.saveCachedSummary(context, summary)
                val appWidgetManager = AppWidgetManager.getInstance(context)
                MonitorWidgetProvider.updateAllWidgets(context, appWidgetManager)
            }
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "gcli2api_periodic_sync"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // 周期任务 15 分钟（系统 WorkManager 最小限制）
            val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }
    }
}
