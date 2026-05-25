package com.example.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import android.util.Log

class TelemetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val repo = AppRepository(db)
            
            // Background polling of battery and clock states
            val (batteryPct, isCharging) = ContextEngine.getRealBatteryLevel(applicationContext)
            val (timeLabel, dayLabel) = ContextEngine.getRealTimeOfDay()
            val isDark = ContextEngine.isDarkThemeActive(applicationContext)
            
            val totalRuns = repo.getString("total_runs", "0").toIntOrNull() ?: 0
            
            // Build the compressed local JSON behavioral profile
            val jsonProfile = ContextEngine.generateCompressedJsonProfile(
                context = applicationContext,
                batteryLevel = batteryPct,
                isCharging = isCharging,
                isDarkTheme = isDark,
                backgroundSample = true,
                totalRuns = totalRuns,
                isDoomscrolling = false,
                sessionDurationSeconds = 0,
                clicksCountInTwoMinutes = 0
            )
            
            repo.saveString("background_telemetry_json", jsonProfile)
            Log.d("TelemetryWorker", "Successfully stored background behavioral profile: $jsonProfile")
            
            Result.success()
        } catch (e: Exception) {
            Log.e("TelemetryWorker", "Error performing telemetry background sampling", e)
            Result.failure()
        }
    }

    companion object {
        fun schedule(context: Context) {
            try {
                val workRequest = PeriodicWorkRequestBuilder<TelemetryWorker>(15, TimeUnit.MINUTES).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "ContinuousBehavioralTracker",
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            } catch (e: Exception) {
                Log.e("TelemetryWorker", "WorkManager enqueue failure", e)
            }
        }
    }
}
