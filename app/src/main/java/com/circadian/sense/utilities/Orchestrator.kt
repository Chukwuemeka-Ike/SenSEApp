package com.circadian.sense.utilities

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Orchestrates the entire OBF, AuthStateManager, DataManager process to
 * deliver fresh input and output data whenever requested
 */
class Orchestrator(
    private val mAuthStateManager: AuthStateManager,
    private val mConfiguration: Configuration,
    private val mAuthService: AuthorizationService,
    private val mDataManager: DataManager,
    private val mOBF: ObserverBasedFilter
    ) {

    private val mUserInfoJson = AtomicReference<List<FloatArray>?>()

    suspend fun getFreshData(): DataPack? {
        if (!mAuthStateManager.current.isAuthorized){
            return null
        }

        // Load previously saved data
        val filterData = mDataManager.loadData()
        val today = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val todayString = today.format(formatter)
        val yesterday = today.minusDays(1).format(formatter)
        val weekAgo = today.minusDays(7).format(formatter)
        var newData: List<FloatArray>? = null

        // If we have no data at all or old data, request and save raw data
        if (filterData == null){
            Log.i(TAG, "No saved data, requesting new data")
            newData = performActionWithFreshTokensSuspend()
            val dataLength = newData!![0].size
            val t = FloatArray(dataLength)

            for (i in 0 until dataLength) {
                if (i == 0){
                    t[i] = 0.0167F
                }
                else{
                    t[i] = t[i-1]+0.0167F
                }
            }
            Log.i(TAG, "Received data: ${t.asList()}")
            Log.i(TAG, "Received data: ${newData!![1].asList()}")
            val L = mOBF.optimizeFilter(t, newData!![1])
            return finishUp(t, newData!![1], todayString, L!!, todayString)
        }
        // If the data is not up to date, request new data
        else if (filterData.dataTimestamp < yesterday){
            Log.i(TAG, "Stale saved data, requesting new data")
            newData = performActionWithFreshTokensSuspend()
            val dataLength = newData!![0].size
            val t = FloatArray(dataLength)

            for (i in 0 until dataLength) {
                if (i == 0){
                    t[i] = 0.0167F
                }
                else{
                    t[i] = t[i-1]+0.0167F
                }
            }

            // If gains are fresh, finishUp
            if (filterData.gainsTimestamp > weekAgo){
                Log.i(TAG, "Fresh gains, finishing up")
                return finishUp(t, newData!![1], todayString, filterData.gains, filterData.gainsTimestamp)
            }
            else {
                Log.i(TAG, "Stale gains, optimizing filter first")
                val L = mOBF.optimizeFilter(t, newData!![1])
                return finishUp(t, newData!![1], todayString, L!!, todayString)
            }
        }
        else {
            Log.i(TAG, "Fresh saved data.")
            // If gains are fresh, finishUp
            if (filterData.gainsTimestamp > weekAgo){
                Log.i(TAG, "Fresh gains, finishing up")
                return finishUp(filterData.t, filterData.y, filterData.dataTimestamp, filterData.gains, filterData.gainsTimestamp)
            }
            else {
                Log.i(TAG, "Stale gains, optimizing filter first")
                val L = mOBF.optimizeFilter(newData!![0], newData!![1])
                return finishUp(filterData.t, filterData.y, filterData.dataTimestamp, L!!, todayString)
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
            mAuthStateManager.current.performActionWithFreshTokens(
                mAuthService
            ){ accessToken, idToken, ex ->
                Log.i(TAG, "${accessToken}, ${idToken}, $ex")
                val userId = getUserID()
                CoroutineScope(it.context).launch(Dispatchers.IO) {
                    it.resume(
                        mDataManager.fetchMultiDayData(
                            5,
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
    private fun finishUp(t: FloatArray, y: FloatArray, dataTimestamp: String, L: FloatArray, gainsTimestamp: String): DataPack? {
        Log.i(TAG, "Finishing up")
        return try {
//            val today = LocalDateTime.now()
//            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
//            val yesterday = today.minusDays(1).format(formatter)

            val yHat = mOBF.simulateDynamics(t, y, L)!!.last()

            val data = DataPack(t, y, yHat, dataTimestamp, L, gainsTimestamp)

            // Save the data to file
            mDataManager.writeData(data)
            data
        }
        catch (e: Exception){
            Log.e(TAG, "Unable to return fresh data: $e")
            null
        }

    }

    companion object{
        private const val TAG = "Orchestrator"
    }
}