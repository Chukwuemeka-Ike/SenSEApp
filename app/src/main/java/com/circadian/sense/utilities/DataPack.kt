package com.circadian.sense.utilities

/**
 * Data class to package the most useful data
 */
data class DataPack(val t: FloatArray, val y: FloatArray, val yHat: FloatArray, val dataTimestamp: String, var gains: FloatArray, val gainsTimestamp: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DataPack

        if (!t.contentEquals(other.t)) return false
        if (!y.contentEquals(other.y)) return false
        if (!yHat.contentEquals(other.yHat)) return false
        if (dataTimestamp != other.dataTimestamp) return false
        if (!gains.contentEquals(other.gains)) return false
        if (gainsTimestamp != other.gainsTimestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = t.contentHashCode()
        result = 31 * result + y.contentHashCode()
        result = 31 * result + yHat.contentHashCode()
        result = 31 * result + dataTimestamp.hashCode()
        result = 31 * result + gains.contentHashCode()
        result = 31 * result + gainsTimestamp.hashCode()
        return result
    }
}
