@file:JvmName("Constants")
package com.circadian.sense

import androidx.work.Constraints
import androidx.work.NetworkType

// Notification Channel constants
const val CHANNEL_ID = "SENSE_NOTIFICATIONS"
const val VERBOSE_NOTIFICATION_CHANNEL_NAME = "SenSE Notifications Channel"
const val VERBOSE_NOTIFICATION_CHANNEL_DESCRIPTION = "Shows SenSE notifications whenever work starts"
const val NOTIFICATION_TITLE = "SenSE"
const val OPTIMIZATION_START_NOTIFICATION_ID = 1
const val OPTIMIZATION_FINISHED_NOTIFICATION_ID = 2
const val NOTIFICATION_DURATION = 30000L

const val OPTIMIZATION_NOTIFICATION_START = "We're optimizing the filter on your data. This can take up to 15 minutes, so we'll notify you once it's done."
const val OPTIMIZATION_NOTIFICATION_SUCCEEDED = "Successfully updated the filter with new data"
const val OPTIMIZATION_NOTIFICATION_FAILED = "The filter update failed"

const val TAG_OUTPUT = "OUTPUT"


//
const val NUM_DAYS = 1

const val Y_LABEL = "Heart Rate (BPM)"
const val YHAT_LABEL = "Filtered Output"

const val PERIODIC_OPTIMIZATION_WORK_NAME = "PERIODIC_FILTER_OPTIMIZATION"
const val PERIODIC_OPTIMIZATION_HOUR = 1
const val PERIODIC_OPTIMIZATION_MINUTE = 30
const val PERIODIC_OPTIMIZATION_SECOND = 0

val WORK_MANAGER_CONSTRAINTS = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()