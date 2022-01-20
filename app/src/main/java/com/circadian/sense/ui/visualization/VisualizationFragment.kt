package com.circadian.sense.ui.visualization

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.circadian.sense.R
import com.circadian.sense.databinding.FragmentVisualizationBinding
import com.circadian.sense.utilities.ObserverBasedFilter
import com.circadian.sense.utilities.Utils
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet

class VisualizationFragment : Fragment() {

    private lateinit var visualizationViewModel: VisualizationViewModel
    private var _binding: FragmentVisualizationBinding? = null

    // This property is only valid between onCreateView and onDestroyView
    private val binding get() = _binding!!

    private lateinit var chart: LineChart
    private lateinit var rawDataset: LineDataSet
    private lateinit var filterDataset: LineDataSet

    val TAG = "SenSE Debug"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Get the VisualizationViewModel object
        visualizationViewModel =
            ViewModelProvider(this).get(VisualizationViewModel::class.java)

        // Get the ViewBinding
        _binding = FragmentVisualizationBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textVisualization
        visualizationViewModel.text.observe(viewLifecycleOwner, Observer {
            textView.text = it
        })

        // The chart for data visualization
        chart = binding.dataVisualizer
        createVisualizationChart()

        // Checkboxes that allow user choose which data is visible on the graph
        val vizRawData = binding.vizRawData
        val vizFilterData = binding.vizFilterData

        vizRawData.isChecked = true
        vizFilterData.isChecked = true
        vizRawData.setOnClickListener { makeDataVisible(vizRawData) }
        vizFilterData.setOnClickListener { makeDataVisible(vizFilterData) }

//        authManager = context?.let { AuthManager(it) }
//        authManager?.requestUserData()
//        Log.i(TAG, "Request User Data Response: ${authManager?.dataRequestResponse}")

        // Load raw user data
        val OBF = ObserverBasedFilter(requireActivity().applicationContext)
        val rawData = OBF.loadUserData()
//        val L = floatArrayOf(0.0001242618548789415f, 0.0019148682768328732f, 0.09530636024613613f)
//        val filterOutput = OBF.simulateDynamics(L)
//        val y = filterOutput.last()
//        Log.i(tag, "y: $y")

        // Testing Utils
        val utils = Utils(requireActivity().applicationContext)
        val y = utils.loadJSONData("filterOutput.json").getJSONArray("y")
//        val y = filterOutput.getJSONArray("y")
//        val y1 = y.getDouble(0)
        Log.i(TAG, "Saved output: ${y}")
//        Log.i(TAG, "Output type: ${y1}")

        val rawDataEntries = mutableListOf<Entry>()
        val filterDataEntries = mutableListOf<Entry>()
        for (i in 0 until rawData.times.size) {
            rawDataEntries.add(Entry(rawData.times[i], rawData.values[i]))
            filterDataEntries.add(Entry(rawData.times[i], y.getDouble(i).toFloat()))
        }
        rawDataset = LineDataSet(rawDataEntries, "Heart Rate")
        rawDataset.color = Color.MAGENTA
        rawDataset.setDrawCircles(false)

        filterDataset = LineDataSet(filterDataEntries, "Filtered Output")
        filterDataset.color = Color.RED
        filterDataset.setDrawCircles(false)

////        val utils = Utils(requireActivity().applicationContext)
////        val filterOutputJson = utils.loadJSONData("filterOutput.json")
////        val dataSet = filterOutputJson.getJSONArray("y")
////        Log.i(tag, "dataset: $filterOutputJson")

        val dataSets: ArrayList<ILineDataSet> = ArrayList()
        dataSets.add(rawDataset)
        dataSets.add(filterDataset)
        chart.data = LineData(dataSets)

        // Refresh the drawing
        chart.invalidate()

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
            requireActivity().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (nightMod == Configuration.UI_MODE_NIGHT_YES) {
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

    /**
     * Makes the data visible according to whether each checkbox is checked
     * params:
     * [v]
     */
    private fun makeDataVisible(v:View){
        when (v.id){
            R.id.vizFilterData -> {
                filterDataset!!.isVisible = binding.vizFilterData.isChecked
            }
            R.id.vizRawData ->{
                rawDataset!!.isVisible = binding.vizRawData.isChecked
            }
        }
        chart.invalidate()
    }

}


