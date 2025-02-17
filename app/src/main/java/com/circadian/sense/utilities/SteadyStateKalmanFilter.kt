package com.circadian.sense.utilities

import android.icu.lang.UCharacter.GraphemeClusterBreak.L
import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.circadian.sense.NUM_DATA_POINTS_PER_DAY
import com.circadian.sense.NUM_DAYS
import com.circadian.sense.NUM_DAYS_OFFSET

/**
 * SteadyStateKalmanFilter class to organize the filter's functionalities
 */
class SteadyStateKalmanFilter {
    /**
     * Calls the simulateDynamics python function to filter the input y and
     * returns the filter states and output as a list of FloatArrays
     * @param [t] - times array
     * @param [y] - raw data array
     * @param [L] - optimal parameter array
     * @return filterOutput if successful or null otherwise
     */
    fun simulateDynamics(t: FloatArray, y: FloatArray, filterParams: FloatArray): MutableList<FloatArray>? {
        Log.i(TAG, "Simulating system dynamics")
        return try {
            val module = getPythonModule(PYTHON_MAIN_KEY)
            // Call simulateDynamics function which returns a list of PyObjects arranged:
            // [x1, x2, x3, ..., yHat]
            val stateDynamics = module.callAttr(SIMULATE_DYNAMICS_KEY, t, y, filterParams).asList()

            // Convert each row of the output to FloatArrays and add to filterOutput
            val filterOutput = mutableListOf<FloatArray>()
            for (i in 0 until stateDynamics.size) {
                filterOutput.add(stateDynamics[i].toJava(FloatArray::class.java))
            }

            filterOutput
        } catch (e: Exception) {
            Log.e(TAG, "$SIMULATE_DYNAMICS_KEY failed: $e")
            null
        }
    }

    /**
     * Calls the python function that optimizes the filter's params and
     * returns the optimalParams as a FloatArray
     * @param [t] - times array
     * @param [y] - raw data array
     * @return optimalParams if successful or null otherwise
     */
    fun optimizeFilter(t: FloatArray, y: FloatArray): FloatArray? {
        Log.i(TAG, "Optimizing filter")
        val module = getPythonModule(PYTHON_MAIN_KEY)
        return try {
            // Call optimizeFilter function which returns a "list" of PyObjects arranged:
            // [[Q1, Q2, Q3, ..., R]]
            val optimalParams = module.callAttr(OPTIMIZE_FILTER_KEY, t, y).asList()
            Log.i(TAG, "Optimal Parameters: $optimalParams")

            // Convert optimalParams to a FloatArray and return
            optimalParams[0].toJava(FloatArray::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Exception: $e")
            null
        }
    }

    /**
     * Calls the python function that estimates the average daily phase over the NUM_DAYS
     * @param [xHat1] - first filter state
     * @param [xHat2] - second filter state
     * @return [averageDailyPhase] if successful or null otherwise
     */
    fun estimateAverageDailyPhase(xHat1: FloatArray, xHat2: FloatArray): MutableList<FloatArray>? {
        Log.i(TAG, "Estimating average daily phase")
        val module = getPythonModule(PYTHON_MAIN_KEY)
        return try {
            // Call optimizeFilter function which returns a "list" of PyObjects arranged:
            // [[phase1, phase2, phase3, ...],
            // [sorted indices]]
            val averageDailyPhase = module.callAttr(
                ESTIMATE_AVG_DAILY_PHASE_KEY,
                xHat1,
                xHat2,
                NUM_DAYS,
                NUM_DAYS_OFFSET,
                NUM_DATA_POINTS_PER_DAY
            ).asList()
            Log.i(TAG, "Average daily phase: ${averageDailyPhase[0]}")
            Log.i(TAG, "Sort indices: ${averageDailyPhase[1]}")

            // Convert each row of the output to FloatArrays and add to filterOutput
            val sortOutput = mutableListOf<FloatArray>()
            for (i in 0 until averageDailyPhase.size) {
                sortOutput.add(averageDailyPhase[i].toJava(FloatArray::class.java))
            }
            sortOutput
        } catch (e: Exception) {
            Log.e(TAG, "Exception: $e")
            null
        }
    }


    /**
     * Gets the Python module specified by the moduleKey
     * @param [moduleKey] - should be set to the Python filename where
     *                      the target function is defined
     */
    private fun getPythonModule(moduleKey: String): PyObject {
        val py = Python.getInstance()
        return py.getModule(moduleKey)
    }
    companion object {
        private const val TAG = "SteadyStateKalmanFilter"
        private const val SIMULATE_DYNAMICS_KEY = "simulateDynamics"
        private const val OPTIMIZE_FILTER_KEY = "optimizeFilter"
        private const val ESTIMATE_AVG_DAILY_PHASE_KEY = "estimateAverageDailyPhase"
        private const val PYTHON_MAIN_KEY = "main_sskf"
    }
}