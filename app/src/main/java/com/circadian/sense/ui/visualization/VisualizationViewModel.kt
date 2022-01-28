package com.circadian.sense.ui.visualization

import android.util.Log
import androidx.lifecycle.*
import com.circadian.sense.FilterData
import com.circadian.sense.FilterDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.Exception

class VisualizationViewModel(
    private val repository: FilterDataRepository
    ) : ViewModel() {

    init {
//        populateChartData()
    }
    // Using LiveData and caching what allWords returns has several benefits:
    // - We can put an observer on the data (instead of polling for changes) and only update the
    //   the UI when the data actually changes.
    // - Repository is completely separated from the UI through the ViewModel.
    val allData: LiveData<List<FilterData>> = repository.allData.asLiveData()

//    private val _chartData = drawChartData(allData.value)
//    val chartData: MutableLiveData<ArrayList<ILineDataSet>>? = _chartData

    private val _text = MutableLiveData<String>().apply {
        value = "Data Visualization"
    }
    val text: LiveData<String> = _text

//    fun populateChartData() = viewModelScope.launch {
//        withContext(Dispatchers.IO) {
//            val dataB = convertDataBaseToFloatArray(repository.allData)
//            val t = dataB?.get(0)
//            val y = dataB?.get(1)
////            val t = floatArrayOf(0f, 0.1f, 0.2f, 0.3f, .4f, .5f, .6f, .7f, .8f)
////            val y = floatArrayOf(65f, 62f, 67f, 65f, 62f, 67f, 65f, 62f, 67f)
//            val L = floatArrayOf(0f, 0.002f, 0.09f)
//            if (t != null) {
//                ObserverBasedFilterDummy().simulateDynamics(t, y!!, L)
//            }
//        }
//    }

    private fun convertDataBaseToFloatArray(allData: Flow<List<FilterData>>): List<FloatArray>? {
        return try {
            val allDataList = allData.asLiveData().value!!
            val t = FloatArray(allDataList.size)
            val y = FloatArray(allDataList.size)
            for (i in allDataList.indices){
                y[i] = allDataList[i].y
                t[i] = allDataList[i].t
            }
            listOf<FloatArray>(t, y)
        }
        catch (e: Exception){
            Log.i(TAG, "$e")
            null
        }
    }

    /**
     * Launching a new coroutine to insert the data in a non-blocking way
     */
    fun insert(filterData: FilterData) = viewModelScope.launch {
        repository.insert(filterData)
    }

//    private fun drawChartData(allData: List<FilterData>?): MutableLiveData<ArrayList<ILineDataSet>>? {
//        if(allData != null && allData.isNotEmpty()){
//            // Load raw user data
//            val rawDataEntries = mutableListOf<Entry>()
//            val filterDataEntries = mutableListOf<Entry>()
//            for(entry in allData.indices){
//                rawDataEntries.add(Entry(allData[entry].t, allData[entry].y))
//                filterDataEntries.add(Entry(allData[entry].t, allData[entry].yHat))
//            }
//
//            val rawDataset = LineDataSet(rawDataEntries, "Heart Rate")
//            rawDataset.color = Color.MAGENTA
//            rawDataset.setDrawCircles(false)
//
//            val filterDataset = LineDataSet(filterDataEntries, "Filtered Output")
//            filterDataset.color = Color.RED
//            filterDataset.setDrawCircles(false)
//
//            val dataSets: ArrayList<ILineDataSet> = ArrayList()
//            dataSets.add(rawDataset)
//            dataSets.add(filterDataset)
//
//            return MutableLiveData<ArrayList<ILineDataSet>>(dataSets)
//
//        }
//        return null
//    }


    class VisualizationViewModelFactory(
        private val repository: FilterDataRepository
        ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VisualizationViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return VisualizationViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "VizViewModel"
        private const val timesKey = "t"
        private const val valuesKey = "y"
        private const val mDatasetKey = "dataset"
        private const val mActivitiesIntradayKey = "activities-heart-intraday"
        data class DataSignal(var times: FloatArray, var values: FloatArray)
        private val mUserDataFile = "rawUserData.json"
    }
}
