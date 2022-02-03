package com.circadian.sense.utilities

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import net.openid.appauth.AuthState
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
    private val context: Context,
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
        val twoWeeks = 1..14
        val twoWeekData = mutableListOf<List<FloatArray>>()

        val yesterday = today.minusDays(1).format(formatter)
        val weekAgo = today.minusDays(7).format(formatter)
        var newData: List<FloatArray>? = null

        // If we have no data at all or old data, request and save raw data
        if (filterData == null){
            Log.i(TAG, "No saved data, requesting new data")

//            // Updates newData
//            withContext(Dispatchers.Main){
//                mAuthStateManager.current.performActionWithFreshTokens(
//                    mAuthService,
//                    AuthState.AuthStateAction { accessToken, idToken, ex ->
//                        Log.i(TAG, "${accessToken}, ${idToken}, $ex")
//                        // Update the authState with
//                        Log.i(TAG, "Updating current authState")
//                        mAuthStateManager.replace(mAuthStateManager.current)
//
//                        // Fetch user info on a different thread - no NetworkOnMain
//                        launch {
//                            withContext(Dispatchers.IO) {
//                                val userId = getUserID()
//                                if (userId != null && accessToken != null) {
//                                    Log.i(TAG, "Requesting new data with accessToken and userId")
//                                    newData = mDataManager.fetchTwoWeekData(
//                                        userId,
//                                        accessToken,
//                                        mConfiguration
//                                    )
//                                }
//                            }
//                        }
//                    })
//            }
//            withContext(Dispatchers.IO){
//
//            newData = requestData()
//            }
            Log.i(TAG, "Orchestrator thread: ${Thread.currentThread().name}")
            newData = performActionWithFreshTokensSuspend()

//            newData = suspendCoroutine {
//                mAuthStateManager.current.performActionWithFreshTokens(
//                    mAuthService,
//                    AuthState.AuthStateAction { accessToken, idToken, ex ->
//                        if (ex != null) {
//                            // negotiation for fresh tokens failed, check ex for more details
//                            Log.d(TAG, "Negotiation for fresh tokens failed: ${ex}")
//                            return@AuthStateAction
//                        }
//
//                        val userId = getUserID()
//                        if (userId != null && accessToken != null) {
//                            Log.i(TAG, "Requesting fresh data")
//                            launch {withContext(Dispatchers.IO){
//                             newData = mDataManager.fetchTwoWeekData(
//                                userId,
//                                accessToken,
//                                mConfiguration
//                            )}}
////                            mUserInfoJson.set(newData)
//                            it.resume(
//                                mDataManager.fetchTwoWeekData(
//                                    userId,
//                                    accessToken,
//                                    mConfiguration
//                                )
//                            )
//                        } else {
//                            Log.e(TAG, "No user id or access token available")
//                        }
//                    })
//            }

            Log.i(TAG, "Received data: ${newData!![0]}")
            val L = mOBF.optimizeFilter(newData!![0], newData!![1])
            return finishUp(newData!![0], newData!![1], L!!)
        }
        else if (filterData.dataTimestamp < yesterday){
            Log.i(TAG, "Stale saved data, requesting new data")
            newData = performActionWithFreshTokensSuspend()
            if (filterData.gainsTimestamp > weekAgo){
                Log.i(TAG, "Fresh gains, finishing up")
                return finishUp(newData!![0], newData!![1], filterData.gains)
            }
            else {
                Log.i(TAG, "Stale gains, optimizing filter first")
                val L = mOBF.optimizeFilter(newData!![0], newData!![1])
                return finishUp(newData!![0], newData!![1], L!!)
            }
        }
        else {
            if (filterData.gainsTimestamp > weekAgo){
                Log.i(TAG, "Fresh gains, finishing up")
                return finishUp(filterData.t, filterData.y, filterData.gains)
            }
            else {
                Log.i(TAG, "Stale gains, optimizing filter first")
                val L = mOBF.optimizeFilter(newData!![0], newData!![1])
                return finishUp(filterData.t, filterData.y, L!!)
            }
        }

//        return if (newData != null) {
//            Log.i(TAG, "Received new data")
//            val t = newData!![0]
//            val y = newData!![1]
//            if (filterData!!.gainsTimestamp < today.minusDays(7).format(formatter)){
//                filterData!!.gains = mOBF.optimizeFilter(filterData.t, filterData.y)!!
//            }
//            val gains = mOBF.optimizeFilter(t, y)
//            val filterOutput: MutableList<FloatArray>? =
//                mOBF.simulateDynamics(t, y, gains!!)
//            val yHat = filterOutput!!.last()
//            DataPack(t,y,yHat, today.format(formatter),gains, today.format(formatter))
//        } else {
//            Log.e(TAG, "Data request failed")
//            null
//        }
//
//
//
////        if (filterData.gainsTimestamp == weekAgo){
////
////        }
//        return null

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
    suspend fun performActionWithFreshTokensSuspend(): List<FloatArray>? =
        suspendCoroutine {
            Log.i(TAG, "Orchestrator thread: ${Thread.currentThread().name}")
            mAuthStateManager.current.performActionWithFreshTokens(
                mAuthService
            ){ accessToken, idToken, ex ->
                Log.i(TAG, "${accessToken}, ${idToken}, $ex")
                val userId = getUserID()
                CoroutineScope(it.context).launch(Dispatchers.IO) {
                    it.resume(
                        mDataManager.fetchTwoWeekData(
                            userId!!,
                            accessToken!!,
                            mConfiguration
                        )
                    )
                }
            }
        }


    private suspend fun finishUp(t: FloatArray, y: FloatArray, L: FloatArray): DataPack? {
        Log.i(TAG, "Finishing up")
        return try {
            val today = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val yesterday = today.minusDays(1).format(formatter)

            val yHat = mOBF.simulateDynamics(t, y, L)!!.last()

            val data = DataPack(t, y, yHat, yesterday, L, today. format(formatter))

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