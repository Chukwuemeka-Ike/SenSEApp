package com.circadian.sense.utilities

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.circadian.sense.*
import com.circadian.sense.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class DailyOptimizationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val TAG = "DailyOptimizationWorker"
    private val mOrchestrator: Orchestrator

    private val mNotificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        Log.i(TAG, "Optimization Worker created")
        mOrchestrator = Orchestrator(appContext)

        // Create a Notification channel if necessary
        createChannel()
    }

    /**
     * Creates an instance of ForegroundInfo which can be used to update the ongoing notification
     * @param [progress]
     */
    private fun createForegroundInfo(): ForegroundInfo {
        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val cancel = applicationContext.getString(R.string.cancel_optimization_button)
        val notification = createNotification(
            applicationContext.getString(R.string.optimization_ongoing_notification),
            true,
            NotificationCompat.Action(android.R.drawable.ic_delete, cancel, intent)
        )

        return ForegroundInfo(OPTIMIZATION_ONGOING_NOTIFICATION_ID, notification)
    }

    /**
     * Creates a notification with the message and action. It can be an ongoing notification,
     * but that's only set by the
     */
    private fun createNotification(
        message: String,
        isOngoing: Boolean,
        actions: NotificationCompat.Action?
    ): Notification {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP// or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.optimization_notification_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(LongArray(0))
            .setTicker(applicationContext.getString(R.string.optimization_notification_title))
            .setOngoing(isOngoing)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            // Add the cancel action to the notification which can
            // be used to cancel the worker
            .addAction(actions)
            .build()
    }

    /**
     * Shows the notification with the object's notification manager
     */
    private fun showNotification(notification: Notification) {
        mNotificationManager.notify(OPTIMIZATION_ENDED_NOTIFICATION_ID, notification)
    }

    /**
     * Create the NotificationChannel, but only on API 26+ because
     * the NotificationChannel class is new and not in the support library
     */
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = NOTIFICATION_CHANNEL_NAME
            val description = NOTIFICATION_CHANNEL_DESCRIPTION
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description

            //        // Add the channel
            //        val notificationManager =
            //            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            //                    as NotificationManager?

            mNotificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Sets the foreground, so the optimization can run longer than default 10 minutes
     * Gets fresh data, then schedules a run for the next day
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Notify the user that we're optimizing, and we'll notify once done
        setForeground(createForegroundInfo())
        Log.i(TAG, "Performing daily optimization")

        try {

            val elapsed = measureTimeMillis {
                // Get fresh data from the network
                val data = mOrchestrator.getFreshData()
                if (data != null) {
                    Log.i(TAG, "Successfully got fresh data with work manager")
                } else {
                    Log.d(TAG, "Null data received")
                }
            }; Log.i(TAG, "Total time taken using WorkManager: $elapsed")

            // Schedule the next run at 01:30am the next day
            val currentDate = Calendar.getInstance()
            val dueDate = Calendar.getInstance()

            dueDate.set(Calendar.HOUR_OF_DAY, DAILY_OPTIMIZATION_HOUR)
            dueDate.set(Calendar.MINUTE, DAILY_OPTIMIZATION_MINUTE)
            dueDate.set(Calendar.SECOND, DAILY_OPTIMIZATION_SECOND)

            // If we're already past the time today, schedule the next one for tomorrow
            if (dueDate.before(currentDate)) {
                dueDate.add(Calendar.HOUR_OF_DAY, 24)
            }
            val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis

            // Create next work request
            val dailyOptimizationWorkRequest = OneTimeWorkRequestBuilder<DailyOptimizationWorker>()
                .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
                .setConstraints(WORK_MANAGER_CONSTRAINTS)
                .addTag(DAILY_OPTIMIZATION_WORKER_TAG)
                .build()

            WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork(
                    DAILY_OPTIMIZATION_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    dailyOptimizationWorkRequest
                )

            // Notify the user of successful optimization
            showNotification(
                createNotification(
                    applicationContext.getString(R.string.optimization_succeeded_notification),
                    false,
                    null
                )
            )
            Result.success()
        } catch (e: Exception) {
            // Notify the user of failed optimization
            showNotification(
                createNotification(
                    applicationContext.getString(R.string.optimization_failed_notification),
                    false,
                    null
                )
            )
            Log.e(TAG, "Error updating user data", e)
            Result.failure()
        }
    }

}