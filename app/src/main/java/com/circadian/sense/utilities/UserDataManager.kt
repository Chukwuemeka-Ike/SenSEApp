package com.circadian.sense.utilities

import android.app.Application
import android.content.Context
import android.util.Log
import com.chaquo.python.android.AndroidPlatform
import com.circadian.sense.R
import org.json.JSONException
import org.json.JSONObject
import java.lang.Exception

class UserDataManager (private val context: Context) {
    data class rawData(var t: FloatArray, var y: FloatArray)
    data class filteredData(var t: FloatArray, var yHat: FloatArray)
    data class UserData(var times: MutableList<Float>, var values: MutableList<Float>)

    private val mUtils: Utils

    init {
        mUtils = Utils(context)
    }

    companion object {
        private const val mUserDataFile = "mLastDataRequestResponse.json"
        private const val mActivitiesIntradayKey = "activities-heart-intraday"
        private const val mDatasetKey = "dataset"
        private const val TAG = "UserDataManager"
    }

    fun fetchUserInfo(authStateManager: AuthStateManager){
//        AndroidPlatform(applicationContext)

    }

    /**
     * Loads the user data from the user data specific file
     */
    private fun loadRawUserData() : JSONObject? {
        return try{
            mUtils.loadJSONData(mUserDataFile)
        }
        catch (ex: JSONException){
            Log.i(TAG, "$ex")
            null
        }
    }

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
            Log.w(TAG, "Error loading user data: $e")
            e.printStackTrace()
        }
        return UserData(times, values)
    }
}