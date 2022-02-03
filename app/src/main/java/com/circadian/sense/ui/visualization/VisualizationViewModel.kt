package com.circadian.sense.ui.visualization

import android.app.Application
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.circadian.sense.R
import com.circadian.sense.utilities.*
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationService
import kotlin.system.measureTimeMillis

class VisualizationViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "VizViewModel"
    private val mAuthStateManager: AuthStateManager
    private val mConfiguration: Configuration
    private val mAuthService: AuthorizationService
    private val mOBF: ObserverBasedFilter
    private val mDataManager: DataManager
    private val mOrchestrator: Orchestrator

    private val _chartData = MutableLiveData<ArrayList<ILineDataSet>>()
    val chartData: LiveData<ArrayList<ILineDataSet>> = _chartData

    private val _filterData = MutableLiveData<DataPack>()
    val filterData: LiveData<DataPack> = _filterData

    init {
        Log.i(TAG, "Creating VisualizationViewModel")
        mAuthStateManager = AuthStateManager.getInstance(application.applicationContext)
        mConfiguration = Configuration.getInstance(application.applicationContext)
        mAuthService = AuthorizationService(application.applicationContext)
        mOBF = ObserverBasedFilter()
        mDataManager = DataManager(application.applicationContext)
        mOrchestrator = Orchestrator(
            mAuthStateManager,
            mConfiguration,
            mAuthService,
            mDataManager,
            mOBF
        )
    }

    fun runWorkflow(){
        viewModelScope.launch {
            withContext(Dispatchers.IO){
                val elapsed = measureTimeMillis {
                    Log.i(TAG, "ViewModel thread: ${Thread.currentThread().name}")
                    Log.i(TAG, "AuthState: ${mAuthStateManager.current.jsonSerializeString()}")
                    val data = mOrchestrator.getFreshData()
                    Log.i(TAG, "It didn't all blow up")
                    Log.i(TAG, "${data}")
                    if (data != null) {
                        withContext(Dispatchers.Main) {
                            _filterData.value = data!!
                        }
//                        val dataSets: ArrayList<ILineDataSet> =
//                            createChartDataset(data.t, data.y, data.yHat)
//                            withContext(Dispatchers.Main){
//                                // main operation
//                                _chartData.value = dataSets
//                            }
                    }
                }; Log.i(TAG, "Total time taken: $elapsed")
            }
        }
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
        val rawDataEntries = mutableListOf<Entry>()
        val filterDataEntries = mutableListOf<Entry>()
        for(entry in t.indices){
            rawDataEntries.add(Entry(t[entry], y[entry]))
            filterDataEntries.add(Entry(t[entry], yHat[entry]))
        }

        val rawDataset = LineDataSet(
            rawDataEntries,
            // TODO: fix this
            getApplication<Application>().resources.getString(R.string.y_label)
        )
        rawDataset.color = Color.RED
        rawDataset.setDrawCircles(false)

        val filterDataset = LineDataSet(
            filterDataEntries,
            // TODO: fix this
            getApplication<Application>().resources.getString(R.string.yHat_label)
        )
        filterDataset.color = Color.MAGENTA
        filterDataset.setDrawCircles(false)

        val dataSets: ArrayList<ILineDataSet> = ArrayList()
        dataSets.add(0, rawDataset)
        dataSets.add(1, filterDataset)

        return dataSets
    }

}