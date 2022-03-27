package com.circadian.sense.utilities

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Orchestrates the entire OBF, AuthStateManager, DataManager process to
 * deliver fresh input and output data whenever requested
 */
class Orchestrator(
    mContext: Context
    ) {

    private val TAG = "Orchestrator"
    private val numDays = 8
    private val mAuthStateManager: AuthStateManager
    private val mConfiguration: Configuration
    private val mAuthService: AuthorizationService
    private val mOBF: ObserverBasedFilter
    private val mDataManager: DataManager

    init {
        Log.i(TAG, "Optimization Worker created")
        mAuthStateManager = AuthStateManager.getInstance(mContext)
        mConfiguration = Configuration.getInstance(mContext)
        mAuthService = AuthorizationService(mContext)
        mOBF = ObserverBasedFilter()
        mDataManager = DataManager(mContext)
    }

    suspend fun getFreshData(): DataPack? {
        if (!mAuthStateManager.current.isAuthorized){
            return null
        }

        // Date strings to allow us check whether stuff is up to date
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val today = LocalDateTime.now()
        val todayString = today.format(formatter)
        val yesterdayString = today.minusDays(1).format(formatter)
        val weekAgoString = today.minusDays(7).format(formatter)

        // Load previously saved data
        val filterData = mDataManager.loadData()
        var newData: List<FloatArray>? = null

        Log.i(TAG,"AuthState: ${mAuthStateManager.current.jsonSerializeString()}")

        // If we have no data at all, request and save new data
        if (filterData == null){
            Log.i(TAG, "No saved data, requesting new data")

            newData = performActionWithFreshTokensSuspend()

            Log.i(TAG, "Received t: ${newData!![0].asList()}")
            Log.i(TAG, "Received y: ${newData!![1].asList()}")

            val L = mOBF.optimizeFilter(newData!![0], newData!![1])
            val yHat = mOBF.simulateDynamics(newData!![0], newData!![1], L!!)!!.last()
            return finishUp(newData!![0], newData!![1], yHat, yesterdayString, L!!, todayString)
        }
        // If the data is not up to date, request new data
        else if (filterData.dataTimestamp < yesterdayString){
            Log.i(TAG, "Stale saved data, requesting new data")
            Log.i(TAG, "Data timestamp: ${filterData.dataTimestamp}")
            Log.i(TAG, "Gains timestamp: ${filterData.gainsTimestamp}")

            newData = performActionWithFreshTokensSuspend()

            // If gains are fresh, finishUp
            if (filterData.gainsTimestamp > weekAgoString){
                Log.i(TAG, "Fresh gains, finishing up")
                val yHat = mOBF.simulateDynamics(newData!![0], newData!![1], filterData.gains)!!.last()
                return finishUp(newData!![0], newData!![1], yHat, yesterdayString, filterData.gains, filterData.gainsTimestamp)
            }
            else {
                Log.i(TAG, "Stale gains, optimizing filter first")
                val L = mOBF.optimizeFilter(newData!![0], newData!![1])
                val yHat = mOBF.simulateDynamics(newData!![0], newData!![1], L!!)!!.last()
                return finishUp(newData!![0], newData!![1], yHat, yesterdayString, L!!, todayString)
            }
        }
        else {
            Log.i(TAG, "Fresh saved data.")
            Log.i(TAG, "Data timestamp: ${filterData.dataTimestamp}")
            Log.i(TAG, "Gains timestamp: ${filterData.gainsTimestamp}")

            // If gains are fresh, finishUp
            if (filterData.gainsTimestamp > weekAgoString){
                Log.i(TAG, "Fresh gains, finishing up")
                return finishUp(filterData.t, filterData.y, filterData.yHat, filterData.dataTimestamp, filterData.gains, filterData.gainsTimestamp)
            }
            else {
                Log.i(TAG, "Stale gains, optimizing filter first")
                val L = mOBF.optimizeFilter(filterData.t, filterData.y)
                val yHat = mOBF.simulateDynamics(filterData.t, filterData.y, L!!)!!.last()
                return finishUp(filterData.t, filterData.y, yHat, filterData.dataTimestamp, L!!, todayString)
            }
        }

    }

    /**
     * Gets user id from mAuthState
     * @return user_id from mLastTokenResponse or null if there is none
     */
    // TODO: Complete this, make better
    private fun getUserID(): String? {
        return try{
            val tokenRequestResult =
                mAuthStateManager.current.jsonSerialize().getJSONObject("mLastTokenResponse")
            val dataSet = tokenRequestResult.getJSONObject("additionalParameters")
            dataSet.getString("user_id")
        }
        catch (e: Exception) {
            Log.e(TAG, "User ID unavailable: ${e}")
            null
        }
    }

    /**
     *
     */
    private suspend fun performActionWithFreshTokensSuspend(): List<FloatArray>? =
        suspendCoroutine {
            Log.i(TAG, "Orchestrator thread: ${Thread.currentThread().name}")
            Log.i(TAG,"Suspend AuthState: ${mAuthStateManager.current.jsonSerializeString()}")
            mAuthStateManager.current.performActionWithFreshTokens(
                mAuthService
            ){ accessToken, idToken, ex ->
                Log.i(TAG, "${accessToken}, ${idToken}, $ex")
                mAuthStateManager.replace(mAuthStateManager.current) // Update the state
                val userId = getUserID()
                CoroutineScope(it.context).launch(Dispatchers.IO) {
                    it.resume(
                        mDataManager.fetchMultiDayData(
                            numDays,
                            userId!!,
                            accessToken!!,
                            mConfiguration
                        )
                    )
                }
            }
        }


    /**
     *
     */
    private suspend fun finishUp(t: FloatArray, y: FloatArray, yHat: FloatArray, dataTimestamp: String, L: FloatArray, gainsTimestamp: String): DataPack? {
        Log.i(TAG, "Finishing up")
        return try {
            val data = DataPack(t, y, yHat, dataTimestamp, L, gainsTimestamp)
            // Save the data to file
            withContext(Dispatchers.IO) {
                mDataManager.writeData(data)
            }
            data
        }
        catch (e: Exception){
            Log.e(TAG, "Unable to return fresh data: $e")
            null
        }

    }

}