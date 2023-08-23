package com.circadian.sense.ui.visualization

import android.app.Application
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.circadian.sense.*
import com.circadian.sense.utilities.*
import com.github.mikephil.charting.data.*
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
    private val mUserDataManager: UserDataManager = UserDataManager(application.applicationContext)
    private val mObserverBasedFilter: ObserverBasedFilter = ObserverBasedFilter()
    private val mOrchestrator = Orchestrator(application.applicationContext)

    private val _dailyDataChartDataset = MutableLiveData<ArrayList<ILineDataSet>>()
    val dailyDataChartDataset: LiveData<ArrayList<ILineDataSet>> = _dailyDataChartDataset
    private val _averagePhaseDataset = MutableLiveData<BubbleData>()
    val averagePhaseDataset: LiveData<BubbleData> = _averagePhaseDataset

    init {
        // Attempt to create the dataset immediately
//        createChartDataset()
    }

    /**
     * Creates a chart dataset from the currently available local data.
     * Provided that the daily worker has already run, this data will be up to date.
     * The only time there might be no data is if this is the first time running the app
     *
     * Updates the chartDataset LiveData which is subscribed to by Visualization UI components
     */
    fun createChartDataset() {
        Log.i(TAG, "Creating chart dataset")

        // Still timing to see how long this takes
            viewModelScope.launch (Dispatchers.IO) {
//                mOrchestrator.getFreshData()
                val data = mUserDataManager.loadUserData()
                if (data != null) {
//                    Log.i(TAG, "Data: ${data.y.asList()}")
//                    Log.i(TAG,"${data.yHat.asList()}")
//                    Log.i(TAG,"${data.xHat1.asList()}")
//                    Log.i(TAG,"${data.xHat2.asList()}")
                    val elapsed = measureTimeMillis {
                    val avgPhase = mObserverBasedFilter.estimateAverageDailyPhase(data.xHat1, data.xHat2)
                    withContext(Dispatchers.Main){
                        _dailyDataChartDataset.value = createChartDataset(data.y, data.yHat)
                        if (avgPhase != null){
                        _averagePhaseDataset.value = createAveragePhaseChartDataset(avgPhase)
                        }
                    }
                    }; Log.i(TAG, "Total time taken creating chart data: $elapsed")
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
     * Creates the chart dataset given t, y, and yHat
     * @param [t] - vector of times in hours from first time point
     * @param [y] - vector of raw biometric values
     * @param [yHat] - vector of filtered biometric values
     * @return [dataSets] - pair of ILineDataSets that can be plotted by MPAndroidChart
     */
    private fun createChartDataset(
        y: FloatArray,
        yHat: FloatArray
    ): ArrayList<ILineDataSet> {

        val zeroFreeY = eliminateZeros(y)

        // Get NUM_DAYS ago in Epoch Minutes
        val day1InMinutes = TimeUnit.SECONDS.toMinutes(
            LocalDate.now()
                .minusDays(NUM_DAYS.toLong())
                .atStartOfDay(ZoneId.systemDefault())
                .toEpochSecond()
        )

        // Create lists of entries for the raw and filtered data
        val rawDataEntries = mutableListOf<Entry>()
        val filterDataEntries = mutableListOf<Entry>()

        val startIdx = 1440*NUM_DAYS_OFFSET
        for (entry in startIdx..y.lastIndex) {
            val x = (day1InMinutes + entry).toFloat()
            rawDataEntries.add(Entry(x, zeroFreeY[entry]))
            filterDataEntries.add(Entry(x, yHat[entry]))
        }

        // Create the chart datasets from both lists
        val rawDataset = LineDataSet(rawDataEntries, Y_LABEL)
        rawDataset.color = Color.rgb(37, 137, 245)
        rawDataset.setDrawCircles(false)
        rawDataset.lineWidth = 0.5f
        rawDataset.isHighlightEnabled = false

        val filterDataset = LineDataSet(filterDataEntries, YHAT_LABEL)
        filterDataset.color = Color.rgb(207, 19, 19)
        filterDataset.setDrawCircles(false)
        filterDataset.lineWidth = 3f
        filterDataset.isHighlightEnabled = false

        // Create a list of datasets which the chart in the Visualization UI components can plot
        val dataSets: ArrayList<ILineDataSet> = ArrayList()
        dataSets.add(0, rawDataset)
        dataSets.add(1, filterDataset)

        return dataSets
    }

    /**
     *
     */
    private fun createAveragePhaseChartDataset(
        averagePhaseData: MutableList<FloatArray>
    ): BubbleData {

//        Log.i(TAG, "Average daily phase: ${averagePhaseData[0].asList()}")
        val averagePhaseEntries = mutableListOf<BubbleEntry>()
        val sortIndices = averagePhaseData[1]
        val averagePhases = averagePhaseData[0]
//        val top = averagePhases.first()

//        val startIdx = NUM_DAYS_OFFSET
        for (entry in sortIndices.indices) {
            val x = sortIndices[entry]
            averagePhaseEntries.add(BubbleEntry(averagePhases[x.toInt()], x+1, 5f))
//            Log.i(TAG, "Entries: ${averagePhases[entry]}, $x")
        }
        val bData = BubbleDataSet(
            averagePhaseEntries,
            AVERAGE_DAILY_PHASE_LABEL
        )
        bData.setDrawValues(false)
        bData.setDrawIcons(true)
        bData.color = Color.rgb(37, 137, 245)
        bData.isVisible = true
        bData.isHighlightEnabled = false

        return BubbleData(bData)
    }
}