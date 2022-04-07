package com.circadian.sense.utilities

import android.content.Context
import android.util.Log
import com.circadian.sense.DATE_PATTERN
import com.circadian.sense.NUM_DAYS
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
 * Orchestrates getting fresh data by combining the OBF, AuthStateManager, and UserDataManager
 */
class Orchestrator(
    mContext: Context
) {

    private val TAG = "Orchestrator"

    private val mAuthStateManager: AuthStateManager = AuthStateManager.getInstance(mContext)
    private val mConfiguration: Configuration = Configuration.getInstance(mContext)
    private val mAuthService: AuthorizationService = AuthorizationService(mContext)
    private val mOBF: ObserverBasedFilter = ObserverBasedFilter()
    private val mUserDataManager: UserDataManager = UserDataManager(mContext)

    /**
     * Returns saved data if it is current, or requests data, runs the filter and
     * returns the new data
     * @return DataPack(t, y, yHat, dataTimestamp, gains, gainsTimestamp)
     */
    suspend fun getFreshData(): DataPack? {
        if (!mAuthStateManager.current.isAuthorized) {
            Log.d(TAG, "App not authorized! Should not be requesting data")
            return null
        }

        // Date strings to allow us check whether data is up to date
        val formatter = DateTimeFormatter.ofPattern(DATE_PATTERN)
        val today = LocalDateTime.now()
        val todayString = today.format(formatter)
        val yesterdayString = today.minusDays(1).format(formatter)
        val weekAgoString = today.minusDays(7).format(formatter)

        // Attempt to load previously saved user data
        val filterData = mUserDataManager.loadUserData()
        var newData: List<FloatArray>?

        // If we have no data at all, request and save new data
        if (filterData == null) {
            Log.i(TAG, "No saved data, requesting new data")
            newData = performActionWithFreshTokensSuspend()

//            Log.i(TAG, "Received t: ${newData!![0].asList()}")
//            Log.i(TAG, "Received y: ${newData!![1].asList()}")

            // Optimize the filter on the new data, then simulate dynamics with the optimal filter
            val L = mOBF.optimizeFilter(newData!![0], newData!![1])
            val filterOutput = mOBF.simulateDynamics(newData!![0], newData!![1], L!!)!!
            val yHat = filterOutput.last()
            val xHat1 = filterOutput[0]
            val xHat2 = filterOutput[1]

            return finishUp(
                newData!![0],
                newData!![1],
                yHat,
                xHat1,
                xHat2,
                yesterdayString,
                L!!,
                todayString
            )
        }
        // If the data is not up to date, request new data
        else if (filterData.dataTimestamp < yesterdayString) {
            Log.i(TAG, "Stale saved data, requesting new data")
            Log.i(TAG, "Data timestamp: ${filterData.dataTimestamp}")
            Log.i(TAG, "Gains timestamp: ${filterData.gainsTimestamp}")

            newData = performActionWithFreshTokensSuspend()

            // If gains are fresh, finishUp
            if (filterData.gainsTimestamp > weekAgoString) {
                Log.i(TAG, "Fresh gains, finishing up")
                val filterOutput =
                    mOBF.simulateDynamics(newData!![0], newData!![1], filterData.gains)!!
                val yHat = filterOutput.last()
                val xHat1 = filterOutput[0]
                val xHat2 = filterOutput[1]
                return finishUp(
                    newData!![0],
                    newData!![1],
                    yHat,
                    xHat1,
                    xHat2,
                    yesterdayString,
                    filterData.gains,
                    filterData.gainsTimestamp
                )
            } else {
                Log.i(TAG, "Stale gains, optimizing filter first")
                val L = mOBF.optimizeFilter(newData!![0], newData!![1])
                val filterOutput = mOBF.simulateDynamics(newData!![0], newData!![1], L!!)!!
                val yHat = filterOutput.last()
                val xHat1 = filterOutput[0]
                val xHat2 = filterOutput[1]
                return finishUp(
                    newData!![0],
                    newData!![1],
                    yHat,
                    xHat1,
                    xHat2,
                    yesterdayString,
                    L!!,
                    todayString
                )
            }
        } else {
            Log.i(TAG, "Fresh saved data.")
            Log.i(TAG, "Data timestamp: ${filterData.dataTimestamp}")
            Log.i(TAG, "Gains timestamp: ${filterData.gainsTimestamp}")

            // If gains are fresh, return the DataPack directly. No need to re-save what's current
            if (filterData.gainsTimestamp > weekAgoString) {
                Log.i(TAG, "Fresh gains, finishing up")
                return DataPack(
                    filterData.t,
                    filterData.y,
                    filterData.yHat,
                    filterData.xHat1,
                    filterData.xHat2,
                    filterData.dataTimestamp,
                    filterData.gains,
                    filterData.gainsTimestamp
                )
            } else {
                Log.i(TAG, "Stale gains, optimizing filter first")
                val L = mOBF.optimizeFilter(filterData.t, filterData.y)
                val filterOutput = mOBF.simulateDynamics(filterData.t, filterData.y, L!!)!!
                val yHat = filterOutput.last()
                val xHat1 = filterOutput[0]
                val xHat2 = filterOutput[1]
                return finishUp(
                    filterData.t,
                    filterData.y,
                    yHat,
                    xHat1,
                    xHat2,
                    filterData.dataTimestamp,
                    L!!,
                    todayString
                )
            }
        }

    }

    /**
     * Gets user id from mAuthState
     * @return user_id from mLastTokenResponse or null if there is none
     */
    // TODO: Complete this, make better
    private fun getUserID(): String? {
        return try {
            val tokenRequestResult =
                mAuthStateManager.current.jsonSerialize().getJSONObject("mLastTokenResponse")
            val dataSet = tokenRequestResult.getJSONObject("additionalParameters")
            dataSet.getString("user_id")
        } catch (e: Exception) {
            Log.e(TAG, "User ID unavailable: $e")
            null
        }
    }

    /**
     * Wraps performActionWithFreshTokens as a suspendCoroutine function to allow calling
     * suspend functions to wait for its output
     * @return multiDayData if everything goes well
     */
    private suspend fun performActionWithFreshTokensSuspend(): List<FloatArray>? =
        suspendCoroutine {
            mAuthStateManager.current.performActionWithFreshTokens(
                mAuthService
            ) { accessToken, idToken, ex ->
//                Log.i(TAG, "${accessToken}, ${idToken}, $ex")
                mAuthStateManager.replace(mAuthStateManager.current) // Update the state
                val userId = getUserID()
                CoroutineScope(it.context).launch(Dispatchers.IO) {
                    it.resume(
                        mUserDataManager.fetchMultiDayUserData(
                            NUM_DAYS,
                            userId!!,
                            accessToken!!,
                            mConfiguration
                        )
                    )
                }
            }
        }


    /**
     * Creates a DataPack from the inputs
     * @param [t] - FloatArray of time in hours from first timestamp
     * @param [y] - raw data array
     * @param [yHat] - filter data array
     * @param [dataTimestamp] - timestamp of when data was fetched
     * @param [L] - optimal gain array
     * @param [gainsTimestamp] - timestamp of when filter was last optimized
     * @return data - DataPack packaging all the data
     */
    private suspend fun finishUp(
        t: FloatArray,
        y: FloatArray,
        yHat: FloatArray,
        xHat1: FloatArray,
        xHat2: FloatArray,
        dataTimestamp: String,
        L: FloatArray,
        gainsTimestamp: String
    ): DataPack? {
        return try {
            //
            val data = DataPack(t, y, yHat, xHat1, xHat2, dataTimestamp, L, gainsTimestamp)
            withContext(Dispatchers.IO) {
                // Save the data to file
                mUserDataManager.writeUserData(data)
            }
            data
        } catch (e: Exception) {
            Log.e(TAG, "Unable to return fresh data: $e")
            null
        }
    }

}