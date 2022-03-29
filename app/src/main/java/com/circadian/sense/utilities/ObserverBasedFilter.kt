package com.circadian.sense.utilities

import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python

/**
 * ObserverBasedFilter class to organize the filter's two main functionalities
 */
class ObserverBasedFilter {
    /**
     * Calls the simulateDynamics python function to filter the input y and
     * returns the filter states and output as a list of FloatArrays
     * @param [t] - times array
     * @param [y] - raw data array
     * @param [L] - optimal gain array
     * @return filterOutput if successful or null otherwise
     */
    fun simulateDynamics(t: FloatArray, y: FloatArray, L: FloatArray): MutableList<FloatArray>? {
        Log.i(TAG, "Simulating system dynamics")
        return try {
            val module = getPythonModule(PYTHON_MAIN_KEY)
            // Call simulateDynamics function which returns a list of PyObjects arranged:
            // [x1, x2, x3, ..., yHat]
            val stateDynamics = module.callAttr(SIMULATE_DYNAMICS_KEY, t, y, L).asList()

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
     * Calls the python function that optimizes the filter's gains and
     * returns the optimalGains as a FloatArray
     * @param [t] - times array
     * @param [y] - raw data array
     * @return optimalGains if successful or null otherwise
     */
    fun optimizeFilter(t: FloatArray, y: FloatArray): FloatArray? {
        Log.i(TAG, "Optimizing filter")
        val module = getPythonModule(PYTHON_MAIN_KEY)
        return try {
            // Call optimizeFilter function which returns a "list" of PyObjects arranged:
            // [[L1, L2, L3, ...]]
            val optimalGains = module.callAttr(OPTIMIZE_FILTER_KEY, t, y).asList()
            Log.i(TAG, "Optimal Gains: $optimalGains")

            // Convert optimalGains to a FloatArray and return
            optimalGains[0].toJava(FloatArray::class.java)
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
        private const val TAG = "ObserverBasedFilter"
        private const val SIMULATE_DYNAMICS_KEY = "simulateDynamics"
        private const val OPTIMIZE_FILTER_KEY = "optimizeFilter"
        private const val PYTHON_MAIN_KEY = "main"
    }

}