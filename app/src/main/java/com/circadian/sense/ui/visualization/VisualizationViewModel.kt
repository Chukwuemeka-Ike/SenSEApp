package com.circadian.sense.ui.visualization

import android.app.Application
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.circadian.sense.NUM_DAYS
import com.circadian.sense.TAG_OUTPUT
import com.circadian.sense.YHAT_LABEL
import com.circadian.sense.Y_LABEL
import com.circadian.sense.utilities.*
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class VisualizationViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "VizViewModel"
    private val mOrchestrator: Orchestrator
    private val mDataManager: DataManager

    private val _chartDataset = MutableLiveData<ArrayList<ILineDataSet>>()
    val chartData: LiveData<ArrayList<ILineDataSet>> = _chartDataset

//    private val workManager = WorkManager.getInstance(application)
//    private val constraints = Constraints.Builder()
//        .setRequiredNetworkType(NetworkType.CONNECTED)
//        .build()
//    private val _optimizeWorkRequest: WorkRequest =
//        OneTimeWorkRequestBuilder<OptimizationWorker>()
//            .setConstraints(constraints)
//            .build()
//    val optimizeWorkRequest: WorkRequest get() = _optimizeWorkRequest
//
//    private val ddWR: WorkRequest =
//        PeriodicWorkRequestBuilder<OptimizationWorker>(19, TimeUnit.HOURS)
//            .build()

    init {
        Log.i(TAG, "Creating VizViewModel")
        mOrchestrator = Orchestrator(application.applicationContext)
        mDataManager = DataManager(application.applicationContext)
//        getFreshData()
        createChartDataset()
    }

//    fun getFreshData() {
//        Log.i(TAG, "Getting fresh data")
//        workManager.enqueue(optimizeWorkRequest)
//    }

    fun createChartDataset() {
        Log.i(TAG, "Creating chart dataset")
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
//            val elapsed = measureTimeMillis {
//                val data = mOrchestrator.getFreshData()
                val data = mDataManager.loadData()
                if (data != null) {
                    withContext(Dispatchers.Main) {
                        _chartDataset.value = createChartDataset(data.t, data.y, data.yHat)
                    }
                }
//            }; Log.i(TAG, "Total time taken creating chart data: $elapsed")
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

        val day1InMinutes = TimeUnit.SECONDS.toMinutes(
            LocalDate.now()
                .minusDays(NUM_DAYS.toLong())
                .atStartOfDay(ZoneId.systemDefault())
                .toEpochSecond()
        )
        Log.i(TAG, "$NUM_DAYS days ago in minutes: $day1InMinutes")

        val rawDataEntries = mutableListOf<Entry>()
        val filterDataEntries = mutableListOf<Entry>()

        for (entry in t.indices) {
            val x = (day1InMinutes + entry).toFloat()
            rawDataEntries.add(Entry(x, zeroFreeY[entry]))
            filterDataEntries.add(Entry(x, yHat[entry]))
        }

        val rawDataset = LineDataSet(
            rawDataEntries,
            Y_LABEL
        )
        rawDataset.color = Color.rgb(37, 137, 245)
        rawDataset.setDrawCircles(false)
        rawDataset.lineWidth = 0.5f

        val filterDataset = LineDataSet(
            filterDataEntries,
            YHAT_LABEL
        )
        filterDataset.color = Color.rgb(207, 19, 19)
        filterDataset.setDrawCircles(false)
        filterDataset.lineWidth = 3f

        val dataSets: ArrayList<ILineDataSet> = ArrayList()
        dataSets.add(0, rawDataset)
        dataSets.add(1, filterDataset)

        return dataSets
    }

//    fun clearChartData() {
//        _chartDataset.
//    }


}