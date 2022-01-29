package com.circadian.sense.utilities

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import net.openid.appauth.*
import okio.IOException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.Exception
import java.net.HttpURLConnection
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Data manager class that takes care of saving and loading data from local
 * Data is in encrypted key-value format
 */
class DataManager (private val context: Context, private val mAuthStateManager: AuthStateManager, private val mConfiguration: Configuration, private val mAuthService: AuthorizationService) {

    data class UserData(var times: MutableList<Float>, var values: MutableList<Float>)

    /**
     * Loads the filter data from disk
     * @return DataPack(t, y, yHat)
     */
    fun loadData(): DataPack? {
        return try {
            // Load the string into a JSONObject, then collect the components
            val file = File(context.filesDir, mDataFile)
            val jsonData = JSONObject(file.readText())

            val tJSON = jsonData.getJSONArray(timeKey)
            val yJSON = jsonData.getJSONArray(yKey)
            val yHatJSON = jsonData.getJSONArray(yHatKey)
            val n = tJSON.length()

            val t = FloatArray(n)
            val y = FloatArray(n)
            val yHat = FloatArray(n)

            for (i in 0 until n){
                t[i] = tJSON.getDouble(i).toFloat()
                y[i] = yJSON.getDouble(i).toFloat()
                yHat[i] = yHatJSON.getDouble(i).toFloat()
            }
            Log.i(TAG, "Successfully loaded data")
            DataPack(t, y, yHat)
        } catch (e: Exception) {
            Log.e(TAG, "$e")
            null
        }
    }

    /**
     * Writes the data to filename
     * @param [data] - DataPack to save
     */
    fun writeData(data: DataPack) {
        try {
            val tArray = JSONArray(data.t)
            val yArray = JSONArray(data.y)
            val yHatArray = JSONArray(data.yHat)
            val outputString = """{"t":${tArray}, "y":${yArray}, "yHat":${yHatArray}} """
            val file = File(context.filesDir, mDataFile)
            file.writeText(outputString)
            Log.i(TAG, "Successfully saved data")
        } catch (e: Exception) {
            Log.e(TAG, "Write data failed: $e")
        }
    }

    /**
     * Requests user data from the provided endpoint
     * Refreshes access token if necessary before making the request
     */
    fun fetchUserInfo(): List<FloatArray>? {
        // Refresh access token first to ensure no issues with revoking
        if (mAuthStateManager.current.needsTokenRefresh) {
            refreshAccessToken()
        }

        val userInfoEndpoint = mConfiguration.getUserInfoEndpointUri()
        val userId = getUserID()

        // TODO: Make this use current date
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern(datePattern)
        val formatted = current.minusDays(1).format(formatter)

        val userInfoRequestURL = Uri.parse("$userInfoEndpoint/${userId}/activities/heart/date/${formatted}/1d/1min.json")
        Log.i(TAG, userInfoRequestURL.toString())

        try{
            val conn: HttpURLConnection =
                mConfiguration.connectionBuilder.openConnection(userInfoRequestURL)
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer ${mAuthStateManager.current.accessToken}")
            conn.setRequestProperty("Accept", "application/json")
            conn.instanceFollowRedirects = false
            conn.doInput = true

            val responseCode = conn.responseCode
            Log.i(TAG, "Response Code :: $responseCode")
            Log.i(TAG, "Response message: ${conn.responseMessage}")

            if (responseCode == HttpURLConnection.HTTP_OK) { // success
                val `in` = BufferedReader(
                    InputStreamReader(conn.inputStream)
                )
                var inputLine: String?
                val response = StringBuffer()
                while (`in`.readLine().also { inputLine = it } != null) {
                    response.append(inputLine)
                }
                `in`.close()

                // print result
                Log.i(TAG, response.toString())

                return parseUserData(response.toString())
            } else {
                Log.i(TAG,"GET request failed")
                return null
            }
        }
        catch (ioEx: IOException) {
            Log.e(TAG, "IOException", ioEx)
            return null
        } catch (jsonEx: JSONException) {
            Log.e(TAG, "JSON Exception", jsonEx)
            return null
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
    // TODO: MAKE BETTER - time vector especially
    private fun parseUserData(jsonString: String): List<FloatArray> {
        val jsonData = JSONObject(jsonString)
        val activitiesIntradayValue = jsonData.getJSONObject(mActivitiesIntradayKey)
        val activitiesIntradayDataset = activitiesIntradayValue.getJSONArray(mDatasetKey)
        val n = activitiesIntradayDataset.length()

        val t = FloatArray(n)
        val y = FloatArray(n)

        for (i in 0 until n) {
            if (i == 0){
                t[i] = 0.0167F
            }
            else{
                t[i] = t[i-1]+0.0167F
            }
            y[i] = activitiesIntradayDataset.getJSONObject(i).getString("value").toFloat()
        }

        return listOf(t, y)
    }

    /**
     *
     */
    @MainThread
    private fun refreshAccessToken() {
        Log.i(TAG,  "Refreshing access token")
        performTokenRequest(
            mAuthStateManager.current.createTokenRefreshRequest()
        ) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
            handleAccessTokenResponse(
                tokenResponse,
                authException
            )
        }
    }

    /**
     *
     */
    @MainThread
    private fun performTokenRequest(
        request: TokenRequest,
        callback: AuthorizationService.TokenResponseCallback
    ) {
        val clientAuthentication: ClientAuthentication
        clientAuthentication = try {
            mAuthStateManager.current.clientAuthentication
        } catch (ex: ClientAuthentication.UnsupportedAuthenticationMethod) {
            Log.d(
                TAG,
                "Token request cannot be made, client authentication for the token "
                        + "endpoint could not be constructed (%s)",
                ex
            )
            Log.e(TAG, "Client authentication method is unsupported")
            return
        }
        mAuthService.performTokenRequest(
            request,
            clientAuthentication,
            callback
        )
    }

    /**
     *
     */
    @WorkerThread
    private fun handleAccessTokenResponse(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) {
        Log.i(TAG,  "Handling access token response")
        mAuthStateManager.updateAfterTokenResponse(tokenResponse, authException)
    }

    companion object {
        private const val mDataFile = "mLastDataRequestResponse.json"
        private const val timeKey = "t"
        private const val yKey = "y"
        private const val yHatKey = "yHat"
        private const val mActivitiesIntradayKey = "activities-heart-intraday"
        private const val mDatasetKey = "dataset"
        private const val datePattern = "yyyy-MM-dd"
        private const val TAG = "DataManager"
    }
}