package com.circadian.sense.utilities

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.circadian.sense.DATE_PATTERN
import net.openid.appauth.connectivity.ConnectionBuilder
import okio.IOException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Data manager class that takes care of saving, loading, and clearing data from local.
 * It also implements the data request from Fitbit servers and formats the received data
 * for the rest of the app
 * Data is saved in encrypted key-value format
 */
class UserDataManager(private val context: Context) {

    /**
     * Clears user data from app-specific storage
     */
    fun clearUserData() {
        try {
            context.deleteFile(mDataFile)
            Log.i(TAG, "Successfully deleted user data")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete user data with exception: $e")
        }
    }

    /**
     * Loads the filter data from app-specific storage. Returns null if there's nothing saved
     * @return DataPack(t, y, yHat, dataTimestamp, gains, gainsTimestamp)
     */
    fun loadUserData(): DataPack? {
        return try {

            // Build a masterKey and use it to decrypt the user data file
            val masterKey =
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val encryptedDataFile = EncryptedFile.Builder(
                context,
                File(context.filesDir, mDataFile),
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            // Read the data into a buffer and create a JSONObject of it
            val bufferReader = encryptedDataFile.openFileInput().bufferedReader()
            val jsonData = JSONObject(bufferReader.readText())
            bufferReader.close()

            // Collect all the data using their respective keys
            val tJSON = jsonData.getJSONArray(timeKey)
            val yJSON = jsonData.getJSONArray(yKey)
            val yHatJSON = jsonData.getJSONArray(yHatKey)
            val gainsJSON = jsonData.getJSONArray(gainsKey)
            val dataTimestamp = jsonData.getString(dataTimestampKey)
            val gainsTimestamp = jsonData.getString(gainsTimestampKey)
            val dataLength = tJSON.length()
            val gainsLength = gainsJSON.length()

            // Create arrays for t, y, yHat, and the gains
            val t = FloatArray(dataLength)
            val y = FloatArray(dataLength)
            val yHat = FloatArray(dataLength)

            for (i in 0 until dataLength) {
                t[i] = tJSON.getDouble(i).toFloat()
                y[i] = yJSON.getDouble(i).toFloat()
                yHat[i] = yHatJSON.getDouble(i).toFloat()
            }

            val gains = FloatArray(gainsLength)
            for (i in 0 until gainsLength) {
                gains[i] = gainsJSON.getDouble(i).toFloat()
            }

            Log.i(TAG, "Successfully loaded data")
            DataPack(t, y, yHat, dataTimestamp, gains, gainsTimestamp)
        } catch (exception: IOException) {
            Log.e(TAG, exception.message ?: "IOException")
            null
        } catch (e: Exception) {
            Log.e(TAG, "General Exception: $e")
            null
        }
    }

    /**
     * Writes the data to encrypted app-specific storage
     * @param [data] - DataPack to save
     */
    fun writeUserData(data: DataPack) {
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

            // Overwriting wasn't working, so manually deleting the value first
            val file = File(context.filesDir, mDataFile)
            if (file.exists()) {
                file.delete()
            }

            // Create masterKey and use it to open an encrypted file
            val masterKey =
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val encryptedDataFile = EncryptedFile.Builder(
                context,
                File(context.filesDir, mDataFile),
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            encryptedDataFile.openFileOutput().apply {
                write(outputString.toByteArray(StandardCharsets.UTF_8))
                flush()
                close()
            }

            Log.i(TAG, "Successfully saved data")
        } catch (e: Exception) {
            Log.e(TAG, "Write data failed: $e")
        }
    }

    /**
     * Fetches numDays of data by chaining together numDays Fitbit API calls (AFAIK, Fitbit doesn't
     * allow any other way to do this)
     *
     * @param [numDays] - number of days to go back for the data
     * @param [userId] - userId that gets appended to the request
     * @param [accessToken] - accessToken to use in requesting
     * @param [configuration] - configuration to use for the HTTP requests. Contains user info endpoint and connection builder
     * @return listOf(t,y) - the time and heart rate data in 1-minute intervals
     */
    fun fetchMultiDayUserData(
        numDays: Int,
        userId: String,
        accessToken: String,
        configuration: Configuration
    ): List<FloatArray>? {
        val userInfoEndpoint = configuration.getUserInfoEndpointUri()

        // Chain together numDays API calls
        val today = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern(DATE_PATTERN)
        val multiDays = (numDays downTo 1)
        val numPointsPerDay = 1440
        val y = FloatArray(numPointsPerDay * numDays)

        for (i in multiDays) {
            // Get the date for the request
            val requestDate = today.minusDays(i.toLong()).format(formatter)
            val userDataRequestURL =
                Uri.parse("$userInfoEndpoint/${userId}/activities/heart/date/${requestDate}/1d/1min.json")

            val singleDayData =
                fetchSingleDayUserData(
                    configuration.connectionBuilder,
                    userDataRequestURL,
                    accessToken
                )

            // Add the singleDayData to y if it's not null. If it is, return null and end the process
            // Fetching failed for some reason that'll be in the logs
            if (singleDayData != null) {
                singleDayData.forEachIndexed { index, fl ->
                    y[index + ((numDays - i) * numPointsPerDay)] = fl
                }
            } else {
                return null
            }
            Log.i(TAG, "Copied day $i into y and t. Starting next iteration")
        }

        // Create a continuous sequence of t values. newData currently returns numDays
        // repetitions of 00:00 to 23:59. This messes with graphing
        val t = FloatArray(numPointsPerDay * numDays)
        t.forEachIndexed { i, _ ->
            if (i != 0) {
                t[i] = t[i - 1] + (1 / 60F)
            }
        }

        return listOf(t, y)
    }

    /**
     * Utility function used by fetchMultiDayData to get a 24-hour period of data for the
     * date specified in userDataRequestURL
     * @param [connectionBuilder] - ConnectionBuilder to use in opening the URL connection
     * @param [userDataRequestURL] - Fitbit specific URL to request data
     * @param [accessToken] - user's access token to get their data
     * @return FloatArray containing heart rate signal
     */
    private fun fetchSingleDayUserData(
        connectionBuilder: ConnectionBuilder,
        userDataRequestURL: Uri,
        accessToken: String
    ): FloatArray? {
        try {
            val conn: HttpURLConnection =
                connectionBuilder.openConnection(userDataRequestURL)
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer ${accessToken}")
            conn.setRequestProperty("Accept", "application/json")
            conn.instanceFollowRedirects = false
            conn.doInput = true

            val responseCode = conn.responseCode
//            Log.i(TAG, "Response Code :: $responseCode")
//            Log.i(TAG, "Response message: ${conn.responseMessage}")

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
//                Log.i(TAG, response.toString())
                conn.disconnect()
                return parseUserData(response.toString())
            } else {
                Log.i(TAG, "GET request failed")
                conn.disconnect()
                return null
            }
        } catch (ioEx: IOException) {
            Log.e(TAG, "IOException: ", ioEx)
            return null
        } catch (jsonEx: JSONException) {
            Log.e(TAG, "JSON Exception: ", jsonEx)
            return null
        }
    }

    /**
     * Parses the received data and pads the heart rate with zeros wherever there's
     * no data available.
     *
     * Basic explanation - the function creates a properly sized y array, then places the
     * received data in the correct indices, leaving zeros wherever there was data dropout
     *
     * @param [jsonString] - the string received from Fitbit server
     * @return y - FloatArray containing the heart rate signal
     */
    private fun parseUserData(jsonString: String): FloatArray {
        val jsonData = JSONObject(jsonString)
        val activitiesIntradayValue = jsonData.getJSONObject(mActivitiesIntradayKey)
        val activitiesIntradayDataset = activitiesIntradayValue.getJSONArray(mDatasetKey)
        val dataLength = activitiesIntradayDataset.length()

        val numPointsPerDay = 1440
        val y = FloatArray(numPointsPerDay)

        var startIdx = 0    // Index corresponding to timestamp of first data value
        var realIdx = 0     // Idx of where we are in the received data
        var wantIdx = 0     // Idx of where we want to insert data into y

        // Check first timestamp. If the data is not at midnight, set startIdx appropriately
        val midnight = LocalTime.parse("00:00:00")
        val firstEntry = activitiesIntradayDataset.getJSONObject(0).getString(timestampKey)
        val firstTimestamp = LocalTime.parse(firstEntry)
        if (firstTimestamp != midnight) {
            startIdx = Duration.between(midnight, firstTimestamp).toMinutes().toInt()
            Log.i(TAG, "$startIdx")
        }
        wantIdx = startIdx

        // Iterate through y and received data, skipping y indices wherever there's
        // no corresponding data
        while (wantIdx < numPointsPerDay && realIdx < dataLength) {
            val dataPoint = activitiesIntradayDataset.getJSONObject(realIdx)

            if (wantIdx > startIdx) {
                val prevTime = LocalTime.parse(
                    activitiesIntradayDataset.getJSONObject(realIdx - 1).getString(timestampKey)
                )
                val curTime = LocalTime.parse(dataPoint.getString(timestampKey))
                val timeDiff = Duration.between(prevTime, curTime).toMinutes().toInt()

                if (timeDiff > 1) {
                    wantIdx = wantIdx + timeDiff - 1
                }
            }
            y[wantIdx] = dataPoint.getDouble(valueKey).toFloat()

            realIdx += 1
            wantIdx += 1
        }
        return y
    }

    companion object {
        private const val timestampKey = "time"
        private const val valueKey = "value"
        private const val mDataFile = "appData.json"
        private const val timeKey = "t"
        private const val yKey = "y"
        private const val yHatKey = "yHat"
        private const val dataTimestampKey = "dataTimestamp"
        private const val gainsKey = "L"
        private const val gainsTimestampKey = "gainsTimestamp"
        private const val mActivitiesIntradayKey = "activities-heart-intraday"
        private const val mDatasetKey = "dataset"
        private const val TAG = "DataManager"
    }
}