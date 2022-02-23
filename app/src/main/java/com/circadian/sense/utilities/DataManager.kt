package com.circadian.sense.utilities

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
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
 * Data manager class that takes care of saving and loading data from local
 * Data is in encrypted key-value format
 */
class DataManager (private val context: Context) {

    /**
     * Clears user data
     */
    fun clearData() {
        try {
            context.deleteFile(mDataFile)
            Log.i(TAG, "Successfully deleted user data")
        }
        catch (e: Exception){
            Log.e(TAG, "Failed to delete with exception: $e")
        }
    }

    /**
     * Loads the filter data from disk
     * @return DataPack(t, y, yHat)
     */
    fun loadData(): DataPack? {
        return try {

            val b = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val encryptedFile = EncryptedFile.Builder(
                context,
                File(context.filesDir, mDataFile),
                b,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            val bufferReader = encryptedFile.openFileInput().bufferedReader()
            val jsonData = JSONObject(bufferReader.readText())
            bufferReader.close()



//            // Load the string into a JSONObject, then collect the components
//            val file = File(context.filesDir, mDataFile)
//            val jsonData = JSONObject(file.readText())

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
        }
        catch (exception: IOException) {
            Log.d(TAG, exception.message ?: "")
            return null
        }
        catch (e: Exception) {
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

            // Overwriting wasn't working, so manually deleting the value first
            val file = File(context.filesDir, mDataFile)
            if (file.exists()) { file.delete() }

            val b = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val encryptedFile = EncryptedFile.Builder(
                context,
                File(context.filesDir, mDataFile),
                b,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            encryptedFile.openFileOutput().apply {
                write(outputString.toByteArray(StandardCharsets.UTF_8))
                flush()
                close()
            }

//            file.writeText(outputString)
            Log.i(TAG, "Successfully saved data")
        } catch (e: Exception) {
            Log.e(TAG, "Write data failed: $e")
        }
    }

    /**
     * Fetches numDays of data by chaining together numDays Fitbit API calls (AFAIK, Fitbit doesn't
     * allow any other way to do this)
     *
     */
    fun fetchMultiDayData(numDays: Int, userId: String, accessToken: String, configuration: Configuration): List<FloatArray>? {
        val userInfoEndpoint = configuration.getUserInfoEndpointUri()

        // Chain together 14 API calls
        val today = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern(datePattern)
        val twoWeeks = 1..numDays
        val twoWeekData = mutableListOf<List<FloatArray>?>()
//        val t = mutableListOf<Float>()
        val y = mutableListOf<Float>()

        for (i in twoWeeks){
            val date = today.minusDays(i.toLong()).format(formatter)
            val userDataRequestURL = Uri.parse("$userInfoEndpoint/${userId}/activities/heart/date/${date}/1d/1min.json")
            Log.i(TAG, userDataRequestURL.toString())
            val oneDayData = fetchSingleDayData(configuration.connectionBuilder, userDataRequestURL, accessToken)
            Log.i(TAG, "Received single day data")
            twoWeekData.add(oneDayData)
            Log.i(TAG, "Added data to list")
//            oneDayData!![0].forEach {
//                t.add(it)
//            }
            oneDayData!![1].forEach {
                y.add(it)
            }
            Log.i(TAG, "Copied data into y and t. Next iteration")
        }
        val dataLength = y.size

        // Create a continuous sequence of t values. newData currently returns numDays
        // repetitions of 00:00 to 23:59. This messes with graphing
        val t = FloatArray(dataLength)
        for (i in 0 until dataLength) {
            if (i == 0){
                t[i] = 0F
            }
            else{
                t[i] = t[i-1]+0.0167F
            }
        }

        return listOf(t,y.toFloatArray())
    }

    /**
     *
     */
    private fun fetchSingleDayData(connectionBuilder: ConnectionBuilder, userDataRequestURL: Uri, accessToken: String): List<FloatArray>? {
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
                Log.i(TAG, "Closed connection")

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
     *
     */
    // TODO: MAKE BETTER - time vector especially
    private fun parseUserData(jsonString: String): List<FloatArray> {
        val jsonData = JSONObject(jsonString)
        val activitiesIntradayValue = jsonData.getJSONObject(mActivitiesIntradayKey)
        val activitiesIntradayDataset = activitiesIntradayValue.getJSONArray(mDatasetKey)
        val dataLength = activitiesIntradayDataset.length()

        val minuit = LocalTime.parse("00:00:00")
        val firstEntry = activitiesIntradayDataset.getJSONObject(0).getString("time")
        val firstTimestamp = LocalTime.parse(firstEntry)

        val numPointsPerDay = 1440
//        val numPointsTwoWeeks = numPointsPerDay*14

        val t = FloatArray(numPointsPerDay)
        val y = FloatArray(numPointsPerDay)

        var startIdx = 0
        var realIdx = 0
        var wantIdx = 0
        val timeIncrement = (1F/60F)

        // Check first timestamp
        if (firstTimestamp != minuit) {
            startIdx = Duration.between(minuit, firstTimestamp).toMinutes().toInt()
            Log.i(TAG, "$startIdx")
        }

        while (wantIdx < startIdx){
            if (wantIdx > 0) {
                t[wantIdx] = t[wantIdx - 1] + timeIncrement
            }
            wantIdx += 1
        }

        while (wantIdx < t.size && realIdx < dataLength){
            val dataPoint = activitiesIntradayDataset.getJSONObject(realIdx)

            if (wantIdx == startIdx){
                y[wantIdx] = dataPoint.getDouble("value").toFloat()
            }
            else if (wantIdx > startIdx){
                val prevTime = LocalTime.parse(
                    activitiesIntradayDataset.getJSONObject(realIdx-1).getString("time")
                )
                val curTime = LocalTime.parse(dataPoint.getString("time"))
                val timeDiff = Duration.between(prevTime,curTime).toMinutes().toInt()

                if (timeDiff > 1 ){
                    val futureWantIdx = wantIdx + timeDiff-1
                    while (wantIdx < futureWantIdx){
                        t[wantIdx] = t[wantIdx-1] + timeIncrement
                        wantIdx += 1
                    }
                }
                y[wantIdx] = dataPoint.getDouble("value").toFloat()
            }

            if (wantIdx > 0){
                t[wantIdx] = t[wantIdx-1] + timeIncrement
            }
            realIdx += 1
            wantIdx += 1
        }

        while (wantIdx < t.size){
            t[wantIdx] = t[wantIdx-1] + timeIncrement
            wantIdx += 1
        }

        return listOf(t, y)
    }

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