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

/**
 * DailyOptimizationWorker that takes care of the daily user data and filter updates using
 * WorkManager. It's used in SettingsFragment to do the initial filter optimization, then sets
 * up the next update whenever it completes
 */
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
     * Creates an instance of ForegroundInfo which can be used to update the ongoing optimization
     * notification
     */
    private fun createForegroundInfo(): ForegroundInfo {
        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val cancel = applicationContext.getString(R.string.cancel_optimization_button)
        val notification = createNotification(
            applicationContext.getString(R.string.optimization_notification_title),
            applicationContext.getString(R.string.optimization_ongoing_notification),
            true,
            NotificationCompat.Action(android.R.drawable.ic_delete, cancel, intent)
        )
        return ForegroundInfo(OPTIMIZATION_ONGOING_NOTIFICATION_ID, notification)
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
     * Creates a notification with the given parameters. It can be an ongoing notification,
     * but that's only set by the
     * @param [title] - title of the notification
     * @param [message] - message to display
     * @param [isOngoing] - boolean to make the notification an ongoing one. Only used when the optimization is ongoing
     * @param [actions] - actions to add to the notification if any. e.g. a cancel button
     */
    private fun createNotification(
        title: String,
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
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(LongArray(0))
            .setTicker(title)
            .setOngoing(isOngoing)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            // Add the cancel action to the notification which can
            // be used to cancel the worker
//            .addAction(actions)
            .build()
    }

    /**
     * Shows the notification with the object's notification manager. Only used for notifications
     * at the end of the optimization. TODO: Simplify the overall logic
     */
    private fun showNotification(notification: Notification) {
        mNotificationManager.notify(OPTIMIZATION_ENDED_NOTIFICATION_ID, notification)
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
            // Get fresh data over the network
            val data = mOrchestrator.getFreshData()
            if (data != null) {
                Log.i(TAG, "Successfully got fresh data with work manager")
            } else {
                Log.d(TAG, "Null data received")
            }

            // Schedule the next run at 02:45am the next day
            val currentDate = Calendar.getInstance()
            val dueDate = Calendar.getInstance()

            dueDate.set(Calendar.HOUR_OF_DAY, 2)
            dueDate.set(Calendar.MINUTE, 45)
            dueDate.set(Calendar.SECOND, 0)

            // If we're already past the time today, schedule the next one for tomorrow
            if (dueDate.before(currentDate)) {
                dueDate.add(Calendar.HOUR_OF_DAY, 24)
            }
            val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis
            Log.i(TAG, "Calculated timeDiff: $timeDiff")

            // Create next work request
            val dailyOptimizationWorkRequest =
                OneTimeWorkRequestBuilder<DailyOptimizationWorker>()
                    .setConstraints(WORK_MANAGER_CONSTRAINTS)
                    .addTag(DAILY_OPTIMIZATION_WORKER_TAG)
                    .setInputData(workDataOf(Pair(applicationContext.getString(R.string.initial_optimization_input_data), "true")))
                    .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
                    .build()

            WorkManager.getInstance(applicationContext).enqueue(dailyOptimizationWorkRequest)

            // Notify the user of successful optimization only on the first optimization
            val isFirstOptimization = inputData.getString(applicationContext.getString(R.string.initial_optimization_input_data))
            if (isFirstOptimization == "true") {
                showNotification(
                    createNotification(
                        applicationContext.getString(R.string.optimization_successful_notification_title),
                        applicationContext.getString(R.string.optimization_succeeded_notification),
                        false,
                        null
                    )
                )
            }
            Log.i(TAG, "Successfully scheduled next filter update")
            Result.success()
        } catch (e: Exception) {
            // Notify the user of failed optimization
            showNotification(
                createNotification(
                    applicationContext.getString(R.string.optimization_failed_notification_title),
                    applicationContext.getString(R.string.optimization_failed_notification),
                    false,
                    null
                )
            )
            Log.e(TAG, "Error updating user data", e)

//            // Create another work request to run the very next time the app is opened
//            val dailyOptimizationWorkRequest =
//                OneTimeWorkRequestBuilder<DailyOptimizationWorker>()
//                    .setConstraints(WORK_MANAGER_CONSTRAINTS)
//                    .addTag(DAILY_OPTIMIZATION_WORKER_TAG)
//                    .setInputData(workDataOf(Pair(applicationContext.getString(R.string.initial_optimization_input_data), "true")))
//                    .build()
//            WorkManager.getInstance(applicationContext).enqueue(dailyOptimizationWorkRequest)

            Result.retry()
        }
    }

}