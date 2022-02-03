package com.circadian.sense.utilities

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import net.openid.appauth.*
import net.openid.appauth.connectivity.ConnectionBuilder
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
class DataManager (private val context: Context) {

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
            val gainsJSON = jsonData.getJSONArray(gainsKey)
            val dataTimestamp = jsonData.getString(dataTimestampKey)
            val gainsTimestamp = jsonData.getString(gainsTimestampKey)
            val dataLength = tJSON.length()
            val gainsLength = gainsJSON.length()

            val t = FloatArray(dataLength)
            val y = FloatArray(dataLength)
            val yHat = FloatArray(dataLength)

            for (i in 0 until dataLength){
                t[i] = tJSON.getDouble(i).toFloat()
                y[i] = yJSON.getDouble(i).toFloat()
                yHat[i] = yHatJSON.getDouble(i).toFloat()
            }

            val gains = FloatArray(gainsLength)
            for ( i in 0 until gainsLength){
                gains[i] = gainsJSON.getDouble(i).toFloat()
            }

            Log.i(TAG, "Successfully loaded data")
            DataPack(t, y, yHat,dataTimestamp,gains,gainsTimestamp)
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
            val gainsArray = JSONArray(data.gains)
            val dataTimestamp = data.dataTimestamp
            val gainsTimestamp = data.gainsTimestamp
            val outputString = """{
                |${timeKey}:${tArray}, 
                |${yKey}:${yArray}, 
                |${yHatKey}:${yHatArray},
                |${gainsKey}:${gainsArray},
                |${dataTimestampKey}: ${dataTimestamp},
                |${gainsTimestampKey}: ${gainsTimestamp}
            }""".trimMargin()
            val file = File(context.filesDir, mDataFile)
            file.writeText(outputString)
            Log.i(TAG, "Successfully saved data")
        } catch (e: Exception) {
            Log.e(TAG, "Write data failed: $e")
        }
    }

    /**
     * Fetches 2 weeks of data by chaining together 14 Fitbit API calls (AFAIK, Fitbit doesn't
     * allow any other way to do this)
     *
     */
    fun fetchTwoWeekData(userId: String, accessToken: String, configuration: Configuration): List<FloatArray>? {
        if(userId == null || accessToken == null){
            return null
        }
        val userInfoEndpoint = configuration.getUserInfoEndpointUri()

        // Chain together 14 API calls
        val today = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern(datePattern)
        val twoWeeks = 1..2
        val twoWeekData = mutableListOf<List<FloatArray>?>()
        val nPoints = 2*60*24
        val t = FloatArray(nPoints)
        val y = FloatArray(nPoints)

        for (i in twoWeeks){
            val date = today.minusDays(i.toLong()).format(formatter)
            val userDataRequestURL = Uri.parse("$userInfoEndpoint/${userId}/activities/heart/date/${date}/1d/1min.json")
            Log.i(TAG, userDataRequestURL.toString())
            val oneDayData = fetchDailyData(configuration.connectionBuilder, userDataRequestURL, accessToken)
            Log.i(TAG, "Received single day data")
            twoWeekData.add(oneDayData)
            Log.i(TAG, "Added data to list")
//            val idx = (i*nPoints-nPoints)..(i*nPoints)
//            oneDayData!![0].copyInto(t.slice(idx).toFloatArray())
//            oneDayData!![1].copyInto(y.slice(idx).toFloatArray())
//            Log.i(TAG, "Copied data into y and t. Next iteration")
        }

//        for (i in twoWeeks){
//            val idx = (i*nPoints-nPoints)..(i*nPoints)
//            t.add(twoWeekData[i]?.get(0)!!.)
//
//        }

//        twoWeekData[1]?.slice(1..2)
//        val dd = mutableListOf<FloatArray>()


        return listOf(t,y)
    }

    private fun fetchDailyData(connectionBuilder: ConnectionBuilder, userDataRequestURL: Uri, accessToken: String): List<FloatArray>? {
        try{
            val conn: HttpURLConnection =
                connectionBuilder.openConnection(userDataRequestURL)
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer ${accessToken}")
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
                conn.disconnect()

                return parseUserData(response.toString())
            } else {
                Log.i(TAG,"GET request failed")
                conn.disconnect()
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
     * Requests user data from the provided endpoint
     * Refreshes access token if necessary before making the request
     */
    fun fetchUserInfo(authState: AuthState, configuration: Configuration): List<FloatArray>? {
        // If we need a token refresh, return null
        if (authState.needsTokenRefresh) {
            Log.e(TAG, "Access token expired")
            return null
        }

        val userInfoEndpoint = configuration.getUserInfoEndpointUri()
        val userId = getUserID(authState)

        // TODO: Make this use current date
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern(datePattern)
        val formatted = current.minusDays(1).format(formatter)

        val userInfoRequestURL = Uri.parse("$userInfoEndpoint/${userId}/activities/heart/date/${formatted}/1d/1min.json")
        Log.i(TAG, userInfoRequestURL.toString())

        try{
            val conn: HttpURLConnection =
                configuration.connectionBuilder.openConnection(userInfoRequestURL)
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer ${authState.accessToken}")
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
    private fun getUserID(authState: AuthState): String? {
        return try{
            val tokenRequestResult =
                authState.jsonSerialize().getJSONObject("mLastTokenResponse")
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
        val dataLength = activitiesIntradayDataset.length()

        val t = FloatArray(dataLength)
        val y = FloatArray(dataLength)

        for (i in 0 until dataLength) {
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

//    /**
//     *
//     */
//    @MainThread
//    private fun refreshAccessToken() {
//        Log.i(TAG,  "Refreshing access token")
//        performTokenRequest(
//            mAuthStateManager.current.createTokenRefreshRequest()
//        ) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
//            handleAccessTokenResponse(
//                tokenResponse,
//                authException
//            )
//        }
//    }
//
//    /**
//     *
//     */
//    @MainThread
//    private fun performTokenRequest(
//        request: TokenRequest,
//        callback: AuthorizationService.TokenResponseCallback
//    ) {
//        val clientAuthentication: ClientAuthentication
//        clientAuthentication = try {
//            mAuthStateManager.current.clientAuthentication
//        } catch (ex: ClientAuthentication.UnsupportedAuthenticationMethod) {
//            Log.d(
//                TAG,
//                "Token request cannot be made, client authentication for the token "
//                        + "endpoint could not be constructed (%s)",
//                ex
//            )
//            Log.e(TAG, "Client authentication method is unsupported")
//            return
//        }
//        mAuthService.performTokenRequest(
//            request,
//            clientAuthentication,
//            callback
//        )
//    }
//
//    /**
//     *
//     */
//    @WorkerThread
//    private fun handleAccessTokenResponse(
//        tokenResponse: TokenResponse?,
//        authException: AuthorizationException?
//    ) {
//        Log.i(TAG,  "Handling access token response")
//        mAuthStateManager.updateAfterTokenResponse(tokenResponse, authException)
//    }

    companion object {
        private const val mDataFile = "appData.json"
        private const val timeKey = "t"
        private const val yKey = "y"
        private const val yHatKey = "yHat"
        private const val dataTimestampKey = "dataTimestamp"
        private const val gainsKey = "L"
        private const val gainsTimestampKey = "gainsTimestamp"
        private const val mActivitiesIntradayKey = "activities-heart-intraday"
        private const val mDatasetKey = "dataset"
        private const val datePattern = "yyyy-MM-dd"
        private const val TAG = "DataManager"
    }
}