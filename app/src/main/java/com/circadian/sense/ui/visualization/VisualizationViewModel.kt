package com.circadian.sense.ui.visualization

import android.app.Application
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.circadian.sense.utilities.*
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationService
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class VisualizationViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "VizViewModel"
    private val mAuthStateManager: AuthStateManager
    private val mConfiguration: Configuration
    private val mAuthService: AuthorizationService
    private val mOBF: ObserverBasedFilter
    private val mDataManager: DataManager
    private val mOrchestrator: Orchestrator

    private val _chartDataset = MutableLiveData<ArrayList<ILineDataSet>>()
    val chartData: LiveData<ArrayList<ILineDataSet>> = _chartDataset

    private val workManager = WorkManager.getInstance(application)
    val optimizeWorkRequest: WorkRequest =
        OneTimeWorkRequestBuilder<OptimizationWorker>().build()


    init {
        Log.i(TAG, "Creating VisualizationViewModel")
        mAuthStateManager = AuthStateManager.getInstance(application.applicationContext)
        mConfiguration = Configuration.getInstance(application.applicationContext)
        mAuthService = AuthorizationService(application.applicationContext)
        mOBF = ObserverBasedFilter()
        mDataManager = DataManager(application.applicationContext)
//        mOrchestrator = Orchestrator(
//            mAuthStateManager,
//            mConfiguration,
//            mAuthService,
//            mDataManager,
//            mOBF
//        )
        mOrchestrator = Orchestrator(application.applicationContext)
//        runWorkflow()
        workManager.enqueue(optimizeWorkRequest)
    }

    fun runWorkflow(){
        viewModelScope.launch {
            withContext(Dispatchers.IO){
                val elapsed = measureTimeMillis {
                    Log.i(TAG, "ViewModel thread: ${Thread.currentThread().name}")
                    Log.i(TAG, "AuthState: ${mAuthStateManager.current.jsonSerializeString()}")
                    val data = mOrchestrator.getFreshData()
                    Log.i(TAG, "It didn't all blow up!")
                    if (data != null) {
                            withContext(Dispatchers.Main){
                                _chartDataset.value = createChartDataset(data.t, data.y, data.yHat)
                            }
                    }
                }; Log.i(TAG, "Total time taken: $elapsed")
            }
        }
    }

    /**
     * This function removes zeros from the raw y array. Zeros in y
     * are because of data dropout and make the graph horrible to look at.
     * We instead replace zeros with the last non-zero value (or 70 if they're at the beginning)
     * @param [y]
     * @return [zeroFreeY]
     */
    private fun eliminateZeros(y: FloatArray): FloatArray {
        val zeroFreeY = FloatArray(y.size)
        var holder = 70f
        for (i in y.indices) {
            if (y[i] != 0f) {
                holder = y[i]
            }
            zeroFreeY[i] = holder
        }
        return zeroFreeY
    }

    /**
     * Creates a the chart dataset given t, y, and yHat
     * @param [t] - vector of times in hours from first time point
     * @param [y] - vector of raw biometric values
     * @param [yHat] - vector of filtered biometric values
     * @return [dataSets] - pair of ILineDataSets that can be plotted by MPAndroidChart
     */
    private fun createChartDataset(
        t: FloatArray,
        y: FloatArray,
        yHat: FloatArray
    ): ArrayList<ILineDataSet> {

        val zeroFreeY = eliminateZeros(y)
        Log.i(TAG, "End t: ${t.last()}, ${t.size}")

        val day1EpochInMillis = LocalDate.now().minusDays(numDays).toEpochDay()*24*60*60*1000 // TODO: Hacky. In millis
        Log.i(TAG, "14 Days ago millis: ${day1EpochInMillis}")

        val day1EpochInMinutes = TimeUnit.MILLISECONDS.toMinutes(day1EpochInMillis)
        Log.i(TAG, "14 Days ago mins: ${day1EpochInMinutes}")

        val rawDataEntries = mutableListOf<Entry>()
        val filterDataEntries = mutableListOf<Entry>()

        for(entry in t.indices){
            val x = (day1EpochInMinutes+entry).toFloat()
//            if(entry<10){
//                Log.i(TAG, "x: ${x}")
//            }
            rawDataEntries.add(Entry(x, zeroFreeY[entry]))
            filterDataEntries.add(Entry(x, yHat[entry]))
//            rawDataEntries.add(Entry(t[entry], zeroFreeY[entry]))
//            filterDataEntries.add(Entry(t[entry], yHat[entry]))
        }

        val rawDataset = LineDataSet(
            rawDataEntries,
            y_label
        )
        rawDataset.color = Color.rgb(37, 137, 245)
        rawDataset.setDrawCircles(false)
        rawDataset.lineWidth = 0.5f

        val filterDataset = LineDataSet(
            filterDataEntries,
            yHat_label
        )
        filterDataset.color = Color.rgb(207, 19, 19)
        filterDataset.setDrawCircles(false)
        filterDataset.lineWidth = 3f

        val dataSets: ArrayList<ILineDataSet> = ArrayList()
        dataSets.add(0, rawDataset)
        dataSets.add(1, filterDataset)

        return dataSets
    }

    companion object {
        private const val y_label = "Heart Rate (BPM)"
        private const val yHat_label = "Filtered Output"
        private const val numDays = 8L
    }

}