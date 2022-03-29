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

class DailyOptimizationWorker (appContext: Context, workerParams: WorkerParameters):
    CoroutineWorker(appContext, workerParams) {

    private val TAG = "PeriodicOptimizationWorker"
    private val mOrchestrator: Orchestrator

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        Log.i(TAG, "Optimization Worker created")
        mOrchestrator = Orchestrator(appContext)

        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel()
        }
    }

    /**
     * Creates an instance of ForegroundInfo which can be used to update the ongoing notification
     * @param [progress]
     */
    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val cancel = "Cancel optimization"
        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)

//        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
//            .setSmallIcon(R.mipmap.ic_launcher)
//            .setContentTitle(NOTIFICATION_TITLE)
//            .setContentText(progress)
//            .setStyle(NotificationCompat.BigTextStyle().bigText(progress))
//            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
//            .setVibrate(LongArray(0))
//            .setTicker(NOTIFICATION_TITLE)
//            .setOngoing(true)
//            // Add the cancel action to the notification which can
//            // be used to cancel the worker
//            .addAction(android.R.drawable.ic_delete, cancel, intent)
//            .build()

        val notification = createNotification(progress, true, NotificationCompat.Action(android.R.drawable.ic_delete, cancel, intent))

        return ForegroundInfo(OPTIMIZATION_START_NOTIFICATION_ID, notification)
    }

    private fun createNotification(progress: String, isOngoing: Boolean, actions: NotificationCompat.Action?) : Notification {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP// or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(progress)
            .setStyle(NotificationCompat.BigTextStyle().bigText(progress))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(LongArray(0))
            .setTicker(NOTIFICATION_TITLE)
            .setOngoing(isOngoing)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            // Add the cancel action to the notification which can
            // be used to cancel the worker
            .addAction(actions)
            .build()
    }

    private fun showNotification(notification: Notification){
//        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        notificationManager.notify(OPTIMIZATION_FINISHED_NOTIFICATION_ID, notification)
    }

    /**
     * Create the NotificationChannel, but only on API 26+ because
     * the NotificationChannel class is new and not in the support library
     */
    private fun createChannel() {
        val name = VERBOSE_NOTIFICATION_CHANNEL_NAME
        val description = VERBOSE_NOTIFICATION_CHANNEL_DESCRIPTION
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance)
        channel.description = description

//        // Add the channel
//        val notificationManager =
//            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
//                    as NotificationManager?

        notificationManager.createNotificationChannel(channel)
    }

    /**
     *
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        // Notify the user that we're optimizing, and we'll notify once done
        setForeground(createForegroundInfo(OPTIMIZATION_NOTIFICATION_START))
        Log.i(TAG, "Performing daily optimization")

        try {

//            val elapsed = measureTimeMillis {
            // Call getFreshData which automatically saves
            val data = mOrchestrator.getFreshData()
            if (data != null){
                Log.i(TAG, "Successfully got fresh data with work manager")
            }
            else {
                Log.d(TAG, "Null data received")
            }
//            }; Log.i(TAG, "Total time taken using WorkManager: $elapsed")

            // Schedule the next run at 01:30am the next day
            val currentDate = Calendar.getInstance()
            val dueDate = Calendar.getInstance()

            dueDate.set(Calendar.HOUR_OF_DAY, PERIODIC_OPTIMIZATION_HOUR)
            dueDate.set(Calendar.MINUTE, PERIODIC_OPTIMIZATION_MINUTE)
            dueDate.set(Calendar.SECOND, PERIODIC_OPTIMIZATION_SECOND)

            // If we're already past the time today, schedule the next one for tomorrow
            if (dueDate.before(currentDate)) {
                dueDate.add(Calendar.HOUR_OF_DAY, 24)
            }
            val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis

            val dailyOptimizationWorkRequest = OneTimeWorkRequestBuilder<DailyOptimizationWorker>()
                .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
                .setConstraints(WORK_MANAGER_CONSTRAINTS)
                .addTag(TAG_OUTPUT)
                .build()
            WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork(
                    PERIODIC_OPTIMIZATION_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    dailyOptimizationWorkRequest)

//            makeStatusNotification(OPTIMIZATION_NOTIFICATION_SUCCEEDED)
            showNotification(
                createNotification(OPTIMIZATION_NOTIFICATION_SUCCEEDED, false, null)
            )
            Result.success()
        }
        catch (e: Exception) {
//            makeStatusNotification(OPTIMIZATION_NOTIFICATION_FAILED)
            showNotification(
                createNotification(OPTIMIZATION_NOTIFICATION_FAILED, false, null)
            )
            Log.e(TAG, "Error updating user data", e)
            Result.failure()
        }
    }

//    /**
//     * Create a Notification that is shown as a heads-up notification if possible.
//     *
//     * For this codelab, this is used to show a notification so that you know when different steps
//     * of the background work chain are starting
//     *
//     * @param message Message shown on the notification
//     * @param context Context needed to create Toast
//     */
//    private fun makeStatusNotification(message: String) {
//
//        // Make a channel if necessary
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            // Create the NotificationChannel, but only on API 26+ because
//            // the NotificationChannel class is new and not in the support library
//            val name = VERBOSE_NOTIFICATION_CHANNEL_NAME
//            val description = VERBOSE_NOTIFICATION_CHANNEL_DESCRIPTION
//            val importance = NotificationManager.IMPORTANCE_HIGH
//            val channel = NotificationChannel(CHANNEL_ID, name, importance)
//            channel.description = description
//
//            // Add the channel
//            val notificationManager =
//                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
//
//            notificationManager?.createNotificationChannel(channel)
//        }
//
//        // Create the notification
//        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
//            .setSmallIcon(R.mipmap.ic_launcher)
//            .setContentTitle(NOTIFICATION_TITLE)
//            .setContentText(message)
//            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
//            .setVibrate(LongArray(0))
//            .setTimeoutAfter(NOTIFICATION_DURATION)
//            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
//            .setAutoCancel(true)
//
//
//
//        // Show the notification
//        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, builder.build())
//    }

}