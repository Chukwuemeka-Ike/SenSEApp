@file:JvmName("Constants")
package com.circadian.sense

import androidx.work.Constraints
import androidx.work.NetworkType

// Notification Channel constants
const val CHANNEL_ID = "SENSE_NOTIFICATIONS"
const val NOTIFICATION_CHANNEL_NAME = "SenSE Notifications Channel"
const val NOTIFICATION_CHANNEL_DESCRIPTION = "Shows SenSE notifications whenever work starts"
const val OPTIMIZATION_ONGOING_NOTIFICATION_ID = 1
const val OPTIMIZATION_ENDED_NOTIFICATION_ID = 2
const val NOTIFICATION_DURATION = 30000L

const val DAILY_OPTIMIZATION_WORKER_TAG = "OUTPUT"

// Data request constants
const val NUM_DAYS = 14
const val NUM_DAYS_OFFSET = 6
const val NUM_DATA_POINTS_PER_DAY = 1440
const val DATE_PATTERN = "yyyy-MM-dd"

const val Y_LABEL = "Heart Rate (BPM)"
const val YHAT_LABEL = "Filtered Output"
const val AVERAGE_DAILY_PHASE_LABEL = "Average Daily Circadian Phase Relative to Day 1 (hours)"

const val DAILY_OPTIMIZATION_WORK_NAME = "PERIODIC_FILTER_OPTIMIZATION"
const val DAILY_OPTIMIZATION_HOUR = 0
const val DAILY_OPTIMIZATION_MINUTE = 30
const val DAILY_OPTIMIZATION_SECOND = 0

val WORK_MANAGER_CONSTRAINTS = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()