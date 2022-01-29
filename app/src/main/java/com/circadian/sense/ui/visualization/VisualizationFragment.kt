package com.circadian.sense.ui.visualization

import com.circadian.sense.utilities.Configuration
import android.content.res.Configuration as resConfiguration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.circadian.sense.FilterData
import com.circadian.sense.FilterDataRepository
import com.circadian.sense.MainApplication
import com.circadian.sense.R
import com.circadian.sense.databinding.FragmentVisualizationBinding
import com.circadian.sense.utilities.*
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.utils.Utils
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VisualizationFragment : Fragment() {

    val TAG = "VisualizationFragment"

    private lateinit var vizViewModel: VisualizationViewModel
    private var _binding: FragmentVisualizationBinding? = null

    // This property is only valid between onCreateView and onDestroyView
    private val binding get() = _binding!!

    private lateinit var mAuthStateManager: AuthStateManager
    private lateinit var mConfiguration: Configuration

    private lateinit var chart: LineChart
    private lateinit var loadingContainer: LinearLayout
    private lateinit var rawDataset: LineDataSet
    private lateinit var filterDataset: LineDataSet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Set the ViewModel and ViewBinding
        vizViewModel = VisualizationViewModel(requireActivity().application)
        _binding = FragmentVisualizationBinding.inflate(inflater, container, false)
        val root: View = binding.root

        mAuthStateManager = AuthStateManager.getInstance(requireContext().applicationContext)
        mConfiguration = Configuration.getInstance(requireContext().applicationContext)

        // The chart for data visualization
        chart = binding.dataVisualizer
//        chart.visibility = View.GONE
//        createVisualizationChart()

        loadingContainer = binding.loadingContainer
        loadingContainer.visibility = View.GONE

        // Checkboxes that allow user choose which data is visible on the graph
        val vizRawData = binding.vizRawData
        val vizFilterData = binding.vizFilterData

        vizRawData.isEnabled = false
        vizFilterData.isEnabled = false
        vizRawData.isChecked = true
        vizFilterData.isChecked = true
//        vizRawData.setOnClickListener { makeDataVisible(vizRawData) }
//        vizFilterData.setOnClickListener { makeDataVisible(vizFilterData) }

//        val textView: TextView = binding.textVisualization
//        visualizationViewModel.text.observe(viewLifecycleOwner, Observer {
//            textView.text = it
//        })

        val optimizeButton = binding.optimizeButton
        vizViewModel.chartData.observe(viewLifecycleOwner, { dataSets ->
            loadingContainer.visibility = View.GONE
            chart.visibility = View.VISIBLE
            vizRawData.isEnabled = true
            vizFilterData.isEnabled = true
            chart.data = LineData(dataSets)
            chart.invalidate()
        })

        optimizeButton.setOnClickListener {
            loadingContainer.visibility = View.VISIBLE
            chart.visibility = View.GONE
            vizViewModel.runWorkflow()
        }

        if (mAuthStateManager.current.isAuthorized && !mConfiguration.hasConfigurationChanged()){
            displayAuthorized()
        }
        else {
            displayNotAuthorized()
        }

//        val job = Job()
//        val uiScope = CoroutineScope(Dispatchers.Main + job)
//
//        visualizationViewModel.allData.observe(viewLifecycleOwner, { allData ->
//            textView.text = allData?.toString()
////            if(allData != null) {
////                Log.i(TAG, "${allData[0]!!.t} ${allData[0]!!.y} ${allData[0]!!.yHat}")
////            }
////            drawChartData(allData)
//            if(allData != null && allData.isNotEmpty()) {
//                Log.i(TAG, "allData populated!")
//                uiScope.launch(Dispatchers.IO){
//                    //asyncOperation
//                    populateChartData(allData)
//                    withContext(Dispatchers.Main){
//                        //ui operation
//                        chart.visibility = View.VISIBLE
//                        loadingContainer.visibility = View.GONE
//                    }
//
//                }
//            }
//        })

//            val job = Job()
//            val uiScope = CoroutineScope(Dispatchers.IO + job)
//            uiScope.launch(Dispatchers.IO){
//                displayLoading()
////                optimizeButton.isEnabled = false
//                updateChartData()
////                optimizeButton.isEnabled = true
//            }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun displayAuthorized() {
        binding.authorizedViz.visibility = View.VISIBLE
        binding.notAuthorizedViz.visibility = View.GONE

        createVisualizationChart()
    }

    private fun displayNotAuthorized() {
        binding.authorizedViz.visibility = View.GONE
        binding.notAuthorizedViz.visibility = View.VISIBLE
        Toast.makeText(requireContext().applicationContext, "User is not authorized", Toast.LENGTH_SHORT)
    }

    /**
     *
     */
    private fun createVisualizationChart(){
        chart.description.isEnabled = false
        chart.setDrawGridBackground(false)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(false)

        val tf = Typeface.SANS_SERIF
        val l = chart.legend
        l.typeface = tf

        val leftAxis = chart.axisLeft
        leftAxis.typeface = tf

        chart.axisRight.isEnabled = false

        val xAxis = chart.xAxis
        xAxis.isEnabled = true
        xAxis.typeface = tf

        val nightMod =
            requireActivity().resources.configuration.uiMode and resConfiguration.UI_MODE_NIGHT_MASK
        if (nightMod == resConfiguration.UI_MODE_NIGHT_YES) {
            chart.setBackgroundColor(Color.BLACK)
            chart.axisLeft.textColor = Color.WHITE
            chart.xAxis.textColor = Color.WHITE
            chart.legend.textColor = Color.WHITE
        } else {
            chart.setBackgroundColor(Color.WHITE)
            chart.axisLeft.textColor = Color.BLACK
            chart.xAxis.textColor = Color.BLACK
            chart.legend.textColor = Color.BLACK
        }
    }

//    /**
//     * Makes the data visible according to whether each checkbox is checked
//     * params:
//     * [v]
//     */
//    private fun makeDataVisible(v:View){
//        when (v.id){
//            R.id.vizFilterData -> {
//                filterDataset!!.isVisible = binding.vizFilterData.isChecked
//            }
//            R.id.vizRawData ->{
//                rawDataset!!.isVisible = binding.vizRawData.isChecked
//            }
//        }
//        chart.invalidate()
//    }


//    suspend fun populateChartData(allData: List<FilterData>) = withContext(Dispatchers.IO) {
//        val dataB = convertDataBaseToFloatArray(allData)
//        val t = dataB?.get(0)
//        val y = dataB?.get(1)
////            val t = floatArrayOf(0f, 0.1f, 0.2f, 0.3f, .4f, .5f, .6f, .7f, .8f)
////            val y = floatArrayOf(65f, 62f, 67f, 65f, 62f, 67f, 65f, 62f, 67f)
////        val L = floatArrayOf(0f, 0.002f, 0.09f)
//        if (t != null && y != null) {
//            val L = mOBF.optimizeFilter(t, y)
//            val filterOutput = mOBF.simulateDynamics(t, y, L!!)
//            val yHat = filterOutput!!.last()
//            val chartData = mutableListOf<FilterData>()
//            for (i in allData.indices){
//                chartData.add(FilterData(t[i], y[i], yHat[i]))
//            }
//            drawChartData(chartData)
//        }
//    }
//
//    private fun convertDataBaseToFloatArray(allData: List<FilterData>): List<FloatArray>? {
//        return try {
//            val t =  FloatArray(allData.size)
//            val y = FloatArray(allData.size)
//            for (i in allData.indices){
//                y[i] = allData[i].y
//                t[i] = allData[i].t
//            }
//            listOf<FloatArray>(t, y)
//        }
//        catch (e: Exception){
//            Log.i(TAG, "$e")
//            null
//        }
//    }
//
//    private fun drawChartData(allData: List<FilterData>?) {
//        if(allData != null && allData.isNotEmpty()){
//            // Load raw user data
//            val rawDataEntries = mutableListOf<Entry>()
//            val filterDataEntries = mutableListOf<Entry>()
//            for(entry in allData.indices){
//                rawDataEntries.add(Entry(allData[entry].t, allData[entry].y))
//                filterDataEntries.add(Entry(allData[entry].t, allData[entry].yHat))
//            }
//
//            rawDataset = LineDataSet(rawDataEntries, "Heart Rate")
//            rawDataset.color = Color.MAGENTA
//            rawDataset.setDrawCircles(false)
//
//            filterDataset = LineDataSet(filterDataEntries, "Filtered Output")
//            filterDataset.color = Color.RED
//            filterDataset.setDrawCircles(false)
//
//            val dataSets: ArrayList<ILineDataSet> = ArrayList()
//            dataSets.add(rawDataset)
//            dataSets.add(filterDataset)
//            chart.data = LineData(dataSets)
//            // Refresh the drawing
//            chart.invalidate()
//
//        }
//    }
//
//    private fun displayLoading() {
//
//    }
//
////    suspend fun updateChartData(){
////        Log.i(TAG, "Optimizing filter")
////        mOBF.optimizeFilter()
////        Log.i(TAG, "Simulating dynamics")
////        val yHat = mOBF.simulateDynamics().last()
////
////        // Load raw user data
////        val rawData = parseUserData(mUtils.loadJSONData(mUserDataFile))
////
////        val rawDataEntries = mutableListOf<Entry>()
////        val filterDataEntries = mutableListOf<Entry>()
////        for (i in 0 until rawData.times.size) {
////            rawDataEntries.add(Entry(rawData.times[i], rawData.values[i]))
////            filterDataEntries.add(Entry(rawData.times[i], yHat[i]))
////        }
////        rawDataset = LineDataSet(rawDataEntries, "Heart Rate")
////        rawDataset.color = Color.MAGENTA
////        rawDataset.setDrawCircles(false)
////
////        filterDataset = LineDataSet(filterDataEntries, "Filtered Output")
////        filterDataset.color = Color.RED
////        filterDataset.setDrawCircles(false)
////
////        val dataSets: ArrayList<ILineDataSet> = ArrayList()
////        dataSets.add(rawDataset)
////        dataSets.add(filterDataset)
////        chart.data = LineData(dataSets)
////
////        // Refresh the drawing
////        chart.invalidate()
////    }


    companion object {
        private const val timesKey = "t"
        private const val valuesKey = "y"
        private const val mDatasetKey = "dataset"
        private const val mActivitiesIntradayKey = "activities-heart-intraday"
        data class DataSignal(var times: FloatArray, var values: FloatArray)
        private val mUserDataFile = "rawUserData.json"
    }

}



//////***********************************************************************************************
///// RECYCLING


////        val rawData = mOBF.loadUserData()
//////        val L = floatArrayOf(0.0001242618548789415f, 0.0019148682768328732f, 0.09530636024613613f)
//////        val filterOutput = mOBF.simulateDynamics(L)
//////        val y = filterOutput.last()
//////        Log.i(tag, "y: $y")
////
////        // Testing Utils
////        val utils = Utils(requireActivity().applicationContext)
////        val y = utils.loadJSONData("filterOutput.json").getJSONArray("y")
//////        val y = filterOutput.getJSONArray("y")
//////        val y1 = y.getDouble(0)
////        Log.i(TAG, "Saved output: ${y}")
//////        Log.i(TAG, "Output type: ${y1}")
////
//val rawDataEntries = mutableListOf<Entry>()
////        val filterDataEntries = mutableListOf<Entry>()
//for (i in 0 until rawData.times.size) {
//    rawDataEntries.add(Entry(UserDataManager.rawData.times[i], UserDataManager.rawData.values[i]))
////            filterDataEntries.add(Entry(rawData.times[i], y.getDouble(i).toFloat()))
//}
//rawDataset = LineDataSet(rawDataEntries, "Heart Rate")
//rawDataset.color = Color.MAGENTA
//rawDataset.setDrawCircles(false)
//
////        filterDataset = LineDataSet(filterDataEntries, "Filtered Output")
////        filterDataset.color = Color.RED
////        filterDataset.setDrawCircles(false)
//
//val dataSets: ArrayList<ILineDataSet> = ArrayList()
//dataSets.add(rawDataset)
////        dataSets.add(filterDataset)
//chart.data = LineData(dataSets)
//
//// Refresh the drawing
//chart.invalidate()