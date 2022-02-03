package com.circadian.sense.utilities

import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python

/**
 * ObserverBasedFilter class to organize the filter's main functionalities
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
    suspend fun simulateDynamics(t: FloatArray, y: FloatArray, L: FloatArray): MutableList<FloatArray>? {
        Log.i(TAG, "Simulating system dynamics")
        val module = getPythonModule(pythonMainKey)
        return try {
            // Call simulateDynamics function which returns a list of PyObjects arranged:
            // [x1, x2, x3, ..., yHat]
            val outputResults = module.callAttr(simulateDynamicsKey, t, y, L).asList()
            Log.i(TAG, "Output results: $outputResults")

            // Convert each row of the output to FloatArrays and add to filterOutput
            val filterOutput = mutableListOf<FloatArray>()
            for (i in 0 until outputResults.size){
                filterOutput.add(outputResults[i].toJava(FloatArray::class.java))
            }

            val yHat = filterOutput.last()
            Log.i(TAG, "Filter Output: ${yHat.asList().slice(0..4)}")
            filterOutput
        }
        catch(e: Exception){
            Log.e(TAG, "${simulateDynamicsKey} failed: ${e}")
            null
        }
//        return filterOutput
    }

    /**
     * Calls the python function that optimizes the filter's gains and and
     * returns the optimalGains as a FloatArray
     * @param [t] - times array
     * @param [y] - raw data array
     * @return [optimalGains] if successful or null otherwise
     */
    suspend fun optimizeFilter(t: FloatArray, y: FloatArray): FloatArray? {
        Log.i(TAG, "Optimizing filter")
        val module = getPythonModule(pythonMainKey)
        return try {
            // Call optimizeFilter function which returns a "list" of PyObjects arranged:
            // [[L1, L2, L3, ...]]
            val optimalGains = module.callAttr(optimizeFilterKey, t, y).asList()
            Log.i(TAG, "Optimal Gains: ${optimalGains}")

            // Convert optimalGains to a FloatArray and return
            optimalGains[0].toJava(FloatArray::class.java)
        }
        catch(e: Exception){
            Log.e(TAG, "Exception: ${e}")
            null
        }
    }

    private fun getPythonModule(moduleKey: String): PyObject {
        val py = Python.getInstance()
        return py.getModule(moduleKey)           // Python filename where function is defined
    }

    companion object {
        private const val TAG = "ObserverBasedFilter"
        private const val simulateDynamicsKey = "simulateDynamics"
        private const val optimizeFilterKey = "optimizeFilter"
        private const val pythonMainKey = "main"
    }

}