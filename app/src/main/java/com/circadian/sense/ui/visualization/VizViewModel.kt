package com.circadian.sense.ui.visualization

import android.app.Application
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.circadian.sense.utilities.AuthStateManager
import com.circadian.sense.utilities.Configuration
import com.circadian.sense.utilities.ObserverBasedFilter
import com.circadian.sense.utilities.Utils
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import okio.IOException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.util.concurrent.ExecutorService
import kotlin.system.measureTimeMillis

class VizViewModel(application: Application) : AndroidViewModel(application) {
    val mAuthStateManager: AuthStateManager
    private lateinit var mConfiguration: Configuration
    private lateinit var mAuthService: AuthorizationService
    private lateinit var mExecutor: ExecutorService
    private lateinit var mUtils: Utils
    private lateinit var mOBF: ObserverBasedFilter

    private val _chartData = MutableLiveData<ArrayList<ILineDataSet>>()
    val chartData: LiveData<ArrayList<ILineDataSet>> = _chartData

    init {
        mAuthStateManager = AuthStateManager.getInstance(application.applicationContext)
        mConfiguration = Configuration.getInstance(application.applicationContext)
        mAuthService = AuthorizationService(application.applicationContext)
        mOBF = ObserverBasedFilter()
    }

    fun runWorkflow(){
        if(mAuthStateManager.current.isAuthorized){
            viewModelScope.launch {
                withContext(Dispatchers.IO){

                    var userData: List<FloatArray>? = null

                    withContext(Dispatchers.Main) {
                        Log.i(TAG, "Before perform: ${mAuthStateManager.current.accessToken}")
                        mAuthStateManager.current.performActionWithFreshTokens(
                            mAuthService,
                            AuthState.AuthStateAction { accessToken, idToken, ex ->
//                                if (ex != null) {
//                                    // negotiation for fresh tokens failed, check ex for more details
//                                    Log.d(TAG, "Negotiation for fresh tokens failed: ${ex}")
//                                    return@AuthStateAction
//                                }
//                                Log.i(TAG, "Made it into request with fresh tokens")
                                Log.i(TAG, "${accessToken}, ${idToken}, $ex")
                                // Update the authState with
                                mAuthStateManager.replace(mAuthStateManager.current)

                                // Fetch user info on a different thread - no NetworkOnMain
                                viewModelScope.launch {
                                    withContext(Dispatchers.IO) {
                                        userData = fetchUserInfo(accessToken, ex)
                                    }
                                }
                            }
                        )
                        Log.i(TAG, "After perform: ${mAuthStateManager.current.accessToken}")
                    }
                    Log.i(TAG, "AuthState: ${mAuthStateManager.current.accessToken}")
                    Log.i(TAG, "${mAuthStateManager.current.accessTokenExpirationTime}")
//                    userData = fetchUserInfo(mAuthStateManager.current.accessToken)

                    val elapsed = measureTimeMillis {
                        if (userData != null) {
                            Log.i(TAG, "Non-null userData")
                            val t = userData!![0]
                            val y = userData!![1]

                            Log.i(TAG, "Optimizing filter")
                            val L = mOBF.optimizeFilter(t, y)

                            Log.i(TAG, "Simulating dynamics")
                            val filterOutput: MutableList<FloatArray>? =
                                mOBF.simulateDynamics(t, y, L!!)
                            Log.i(TAG, "yHat in vizModel: ${filterOutput!!.last().slice(0..4)}")
                            val yHat = filterOutput!!.last()

                            val rawDataEntries = mutableListOf<Entry>()
                            val filterDataEntries = mutableListOf<Entry>()
                            for(entry in t.indices){
                                rawDataEntries.add(Entry(t[entry], y[entry]))
                                filterDataEntries.add(Entry(t[entry], yHat[entry]))
                            }

                            val rawDataset = LineDataSet(rawDataEntries, "Heart Rate")
                            rawDataset.color = Color.MAGENTA
                            rawDataset.setDrawCircles(false)

                            val filterDataset = LineDataSet(filterDataEntries, "Filtered Output")
                            filterDataset.color = Color.RED
                            filterDataset.setDrawCircles(false)

                            val dataSets: ArrayList<ILineDataSet> = ArrayList()
                            dataSets.add(rawDataset)
                            dataSets.add(filterDataset)

                            withContext(Dispatchers.Main){
                                // main operation
                                _chartData.value = dataSets
                            }

                        }
                    }; Log.i(TAG, "Total time taken: $elapsed")
                }
            }
        }
    }

    private fun fetchUserInfo(accessToken: String?, ex: AuthorizationException?) : List<FloatArray>? {
        if(ex!=null){
            Log.d(TAG, "Negotiation for fresh tokens failed: ${ex}")
            return null
        }

        val userInfoEndpoint = mConfiguration.getUserInfoEndpointUri()
        Log.i(TAG, userInfoEndpoint.toString())
        val userId = getUserID()
        val userInfoRequestURL = Uri.parse("$userInfoEndpoint/${userId}/activities/heart/date/2021-12-01/2021-12-01/1min.json")
        Log.i(TAG, userInfoRequestURL.toString())


        try{
            val conn: HttpURLConnection =
                mConfiguration.connectionBuilder.openConnection(userInfoRequestURL)
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer ${accessToken}")
            conn.setRequestProperty("Accept", "application/json")
            conn.instanceFollowRedirects = false
            conn.doInput = true

            val responseCode = conn.responseCode
            Log.i(TAG, "GET Response Code :: $responseCode")

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


    companion object {
        private const val TAG = "VizViewModel"
        private const val mActivitiesIntradayKey = "activities-heart-intraday"
        private const val mDatasetKey = "dataset"
    }

}