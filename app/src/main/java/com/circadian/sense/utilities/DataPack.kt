package com.circadian.sense.utilities

/**
 * Data class to package the most useful filter data for the app.
 */
data class DataPack(
    val t: FloatArray,                  // Time-steps of user data in hours from first.
    val y: FloatArray,                  // User signal data.
    val yHat: FloatArray,               // Filter output.
    val xHat1: FloatArray,              // Filter state estimate xHat1. For calculating avg phase.
    val xHat2: FloatArray,              // Filter state estimate xHat2.
    val dataTimestamp: String,          // Time the data was obtained.
    var filterParams: FloatArray,       // Filter parameters.
    val filterParamsTimestamp: String   // When the params were obtained. i.e. last optimization.
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DataPack

        if (!t.contentEquals(other.t)) return false
        if (!y.contentEquals(other.y)) return false
        if (!yHat.contentEquals(other.yHat)) return false
        if (dataTimestamp != other.dataTimestamp) return false
        if (!filterParams.contentEquals(other.filterParams)) return false
        if (filterParamsTimestamp != other.filterParamsTimestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = t.contentHashCode()
        result = 31 * result + y.contentHashCode()
        result = 31 * result + yHat.contentHashCode()
        result = 31 * result + dataTimestamp.hashCode()
        result = 31 * result + filterParams.contentHashCode()
        result = 31 * result + filterParamsTimestamp.hashCode()
        return result
    }
}
