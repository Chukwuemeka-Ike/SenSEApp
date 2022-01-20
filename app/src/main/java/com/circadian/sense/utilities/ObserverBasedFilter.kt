package com.circadian.sense.utilities

import android.content.Context
import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.circadian.sense.R
import org.json.JSONArray
import org.json.JSONObject
import java.lang.Exception
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

/**
 * ObserverBasedFilter class to organize the filter's functionalities
 */
class ObserverBasedFilter (private val context: Context) {

    data class UserData(var times: MutableList<Float>, var values: MutableList<Float>)
    data class FilterOutput(var states: MutableList<Float>, var output: MutableList<Float>)

    private var mUtils : Utils
//    private var mFilterState: String
    private var mUserData: JSONObject

    init {
        mUtils = Utils(context)
//        mFilterState = getCurrentFilterState()
        mUserData = loadRawUserData()
    }

    /**
     *
     */
    fun runMain() : PyObject? {

        var bytes : PyObject? = null
        // Testing out Python from Chaquopy
        thread{
            // Start Python
            if (! Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }

            val py = Python.getInstance()
            val module = py.getModule("main") // Python filename
            try {
                val dataRequestResponse = mUtils.loadJSONData(
                    context.getString(R.string.data_request_response_file)
                )
                val L = floatArrayOf(0.02f, 0.03f, 0.004f)
//                val bytes = module.callAttr("main", dataRequestResponse.toString(), L)
                bytes = module.callAttr("simulateDynamics", dataRequestResponse.toString(), L)
//                Log.i(tag, "Output: ${bytes.asList()[0]}")
            }
            catch(e: Exception){
                Log.i(tag, "Exception: ${e}")
            }
        }
        return bytes
    }

    /**
     * Calls the python function that simulates the system's dynamics and
     * returns the filter states and output
     * @param [L] - optimal gain matrix
     * @return [] -
     */
    fun simulateDynamics(L: FloatArray): MutableList<FloatArray> {
        var filterOutput = mutableListOf<FloatArray>()
        thread{
            // Start Python if not started
            if (! Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }

            val py = Python.getInstance()
            val module = py.getModule("main")           // Python filename
            try {
                // Simulate the dynamics and return a FloatArray
                val d = module.callAttr("simulateDynamics", mUserData.toString(), L).asList()
                //.toJava(FloatArray::class.java)

                // Convert each row of the output to float arrays (max 7) and add to output List<FloatArray>
//                val output = mutableListOf<FloatArray>()
                for (i in 0 until d.size){
                    filterOutput.add(d[i].toJava(FloatArray::class.java))
                }
                Log.i(tag, "filterOutput: ${filterOutput[0].slice(0..5)}")

                val yHat = filterOutput.last()
                Log.i(tag, "Output size: ${yHat.asList()}")
                Log.i(tag, "d: ${d[3].toJava(FloatArray::class.java).slice(0..30)}")
//                Log.i(tag, "d size: ${d.size}")

                val yHatArray = JSONArray(yHat.asList())

                // Write the filteredOutput to a JSON file
                val filterOutputString : String = """{"y":${yHatArray}}"""
                Log.i(tag, filterOutputString)
                mUtils.writeData(filterOutputString, "filterOutput.json")

                // Save the system dynamics to a JSON file


            }
            catch(e: Exception){
                Log.i(tag, "Exception: ${e}")
            }
        }
        return filterOutput
    }

    /**
     * Calls the python function that optimizes the filter's gains and saves
     * the optimalGains to file
     */
    fun optimizeFilter(){
        thread{
            // Start Python if not started
            if (! Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }

            val py = Python.getInstance()
            val module = py.getModule("main")           // Python filename
            try {
                val elapsed = measureTimeMillis {

                // Return
                val optimalGains = module.callAttr("optimizeFilter", mUserData.toString()).asList()
                Log.i(tag, "Optimal Gains: ${optimalGains}")
//                .toJava(FloatArray::class.java)

                // Create a list of floats for the gains
                val d = optimalGains[0].toJava(FloatArray::class.java).asList()
//                Log.i(tag, "d: ${d}")
//                val optGainsList = mutableListOf<Float>()
//                for (i in 0 until optimalGains.size){
//                    Log.i(tag, "D: ${optimalGains[i].toJava(FloatArray::class.java)[0]}")
////                    optGainsList.add()
//                }
//                Log.i(tag, "After conversion: ${optGainsList}")
                val optGainsArray = JSONArray(d)

                // Write the filteredOutput to a JSON file
                val filterGainsString : String = """{"L":${optGainsArray}}"""
                Log.i(tag, filterGainsString)
                mUtils.writeData(filterGainsString, "optimalGains.json")
                }
                Log.i(tag, "Time elapsed: $elapsed")

            }
            catch(e: Exception){
                Log.i(tag, "Exception: ${e}")
            }
        }
    }

    /**
    * Loads the user data from the user data specific file
    */
    private fun loadRawUserData() : JSONObject {
        var jsonData = JSONObject()
        jsonData = mUtils.loadJSONData(
            context.getString(R.string.data_request_response_file)
        )
        return jsonData
    }

//    /**
//     * Loads the current filter state or creates a placeholder
//     *
//     */
//    private fun getCurrentFilterState(): String {
//        var jsonData = JSONObject()
//        val filename = "filterState.json"
//        try {
//            with(File(context.filesDir, filename)){
//                jsonData = JSONObject(this.readText())
//            }
//            Log.i(tag, "Successfully loaded from $filename")
//        } catch (e: Exception) {
//            Log.w(tag, "$e")
//        }
//        return jsonData.toString()
//    }

    /**
     * Loads the user data from the user data specific file
     */
    // TODO: Streamline this, rename the function and variables to be
    //  explicit that it's the data from Fitbit, not anything we've already worked on
    //  Also, just have this load the JSON object, which we pass to Python
    fun loadUserData() : UserData {
        val times = mutableListOf<Float>()
        val values = mutableListOf<Float>()

        // Read the UserData JSONObject from file
        try {
            val dataRequestResponse = mUtils.loadJSONData(context.getString(R.string.data_request_response_file))
            val dataSet = dataRequestResponse.getJSONObject(context.getString(R.string.data_key))
            val dataArray = dataSet.getJSONArray(context.getString(R.string.dataset_key))

            for (i in 0 until dataArray.length()) {
                val x = dataArray.getJSONObject(i).getString("value").toFloat()
                if (i == 0){
                    times.add(0.0167F)
                }
                else{
                    times.add(times[i-1]+0.0167F)
                }
                values.add(x)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error loading user data: $e")
            e.printStackTrace()
        }
        return UserData(times, values)
    }


    companion object {
        private const val tag = "SenSE Debug"
    }


}