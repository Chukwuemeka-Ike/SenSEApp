package com.circadian.sense.ui.visualization

import android.app.Application
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.circadian.sense.utilities.*
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

class VisualizationViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "VizViewModel"
    val mAuthStateManager: AuthStateManager
    private lateinit var mConfiguration: Configuration
    private lateinit var mAuthService: AuthorizationService
    private lateinit var mOBF: ObserverBasedFilter
    private lateinit var mDataManager: DataManager

    private val _chartData = MutableLiveData<ArrayList<ILineDataSet>>()
    val chartData: LiveData<ArrayList<ILineDataSet>> = _chartData

    private val _text = MutableLiveData<String>().apply {
        value = "Data Visualization"
    }
    val text: LiveData<String> = _text

    init {
        mAuthStateManager = AuthStateManager.getInstance(application.applicationContext)
        mConfiguration = Configuration.getInstance(application.applicationContext)
        mAuthService = AuthorizationService(application.applicationContext)
        mOBF = ObserverBasedFilter()
        mDataManager = DataManager(
            application.applicationContext,
            mAuthStateManager,
            mConfiguration,
            mAuthService
        )
    }

    fun runWorkflow(){
        viewModelScope.launch {
        withContext(Dispatchers.IO){
            val userData = mDataManager.fetchUserInfo()
            if (userData != null) {
                Log.i(TAG, "Times: ${userData!![0].slice(0..9)}")
                Log.i(TAG, "Value: ${userData!![1].slice(0..9)}")
            }
            else{
                Log.i(TAG, "User data: $userData")
            }

            val elapsed = measureTimeMillis {
                if (userData != null) {
                    Log.i(TAG, "Non-null userData")
                    val t = userData[0]
                    val y = userData[1]

                    Log.i(TAG, "Optimizing filter")
                    val L = mOBF.optimizeFilter(t, y)

                    Log.i(TAG, "Simulating dynamics")
                    val filterOutput: MutableList<FloatArray>? =
                        mOBF.simulateDynamics(t, y, L!!)
                    val yHat = filterOutput!!.last()
                    Log.i(TAG, "yHat in vizModel: ${yHat.slice(0..4)}")

                    val dataSets: ArrayList<ILineDataSet> = createChartDataset(t, y, yHat)

                    withContext(Dispatchers.Main){
                        // main operation
                        _chartData.value = dataSets
                    }

                }
            }; Log.i(TAG, "Total time taken: $elapsed")
        }
        }
    }

    /**
     * Creates a the chart dataset we'll use
     */
    private fun createChartDataset(
        t: FloatArray,
        y: FloatArray,
        yHat: FloatArray
    ): ArrayList<ILineDataSet> {
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

        return dataSets
    }

}