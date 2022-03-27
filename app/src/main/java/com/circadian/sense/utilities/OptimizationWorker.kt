package com.circadian.sense.utilities

import android.app.Notification
import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OptimizationWorker(appContext: Context, workerParams: WorkerParameters):
    CoroutineWorker(appContext, workerParams) {

    private val TAG = "OptimizationWorker"
    private val mOrchestrator: Orchestrator

    init {
        Log.i(TAG, "Optimization Worker created")
        mOrchestrator = Orchestrator(appContext)
    }

//    override suspend fun getForegroundInfo(): ForegroundInfo {
//        return ForegroundInfo(
//            NOTIFICATION_ID, createNotification()
//        )
//    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Call getFreshData which automatically saves
            val data = mOrchestrator.getFreshData()
            Log.i(TAG, "Successfully got fresh data with work manager: ${data}")
            Result.success()
        }
        catch (e: Exception) {
            Log.e(TAG, "Error updating user data", e)
            Result.failure()
        }
    }

    private fun createNotification() : Notification {
        TODO()
    }

}