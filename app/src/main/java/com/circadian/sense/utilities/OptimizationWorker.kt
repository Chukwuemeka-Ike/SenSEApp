package com.circadian.sense.utilities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.circadian.sense.*
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
//            val elapsed = measureTimeMillis {
            // Call getFreshData which automatically saves
            val data = mOrchestrator.getFreshData()
            Log.i(TAG, "Successfully got fresh data with work manager: ${data}")
//            }; Log.i(TAG, "Total time taken using WorkManager: $elapsed")
            Result.success()
        }
        catch (e: Exception) {
            Log.e(TAG, "Error updating user data", e)
            Result.failure()
        }
    }

    /**
     * Creates a Notification that is shown as a heads-up notification if possible.
     *
     * For this codelab, this is used to show a notification so that you know when different steps
     * of the background work chain are starting
     *
     * @param message Message shown on the notification
     * @param context Context needed to create Toast
     */
    private fun makeStatusNotification(message: String, context: Context) {

        // Make a channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            val name = VERBOSE_NOTIFICATION_CHANNEL_NAME
            val description = VERBOSE_NOTIFICATION_CHANNEL_DESCRIPTION
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description

            // Add the channel
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?

            notificationManager?.createNotificationChannel(channel)
        }

        // Create the notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(LongArray(0))

        // Show the notification
        NotificationManagerCompat.from(context).notify(OPTIMIZATION_START_NOTIFICATION_ID, builder.build())
    }

}