package com.circadian.sense.utilities

/**
 * Data class to package the most useful data
 */
data class DataPack(val t: FloatArray, val y: FloatArray, val yHat: FloatArray, val dataTimestamp: String, var gains: FloatArray, val gainsTimestamp: String)
