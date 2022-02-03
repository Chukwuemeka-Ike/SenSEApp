package com.circadian.sense.ui.visualization

import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.circadian.sense.R
import com.circadian.sense.databinding.FragmentVisualizationBinding
import com.circadian.sense.utilities.AuthStateManager
import com.circadian.sense.utilities.Configuration
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import android.content.res.Configuration as resConfiguration

class VisualizationFragment : Fragment() {

    private val TAG = "VisualizationFragment"

    private val vizViewModel: VisualizationViewModel by activityViewModels()
    private var _binding: FragmentVisualizationBinding? = null

    // This property is only valid between onCreateView and onDestroyView
    private val binding get() = _binding!!

    private lateinit var mAuthStateManager: AuthStateManager
    private lateinit var mConfiguration: Configuration

    private lateinit var mVizChart: LineChart
    private lateinit var mChartDataSets: ArrayList<ILineDataSet>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

//        vizViewModel = VisualizationViewModel(requireActivity().application)
        Log.i(TAG, vizViewModel.filterData.value.toString())

        // Set the ViewBinding
        _binding = FragmentVisualizationBinding.inflate(inflater, container, false)
        val root: View = binding.root

        mAuthStateManager = AuthStateManager.getInstance(requireContext().applicationContext)
        mConfiguration = Configuration.getInstance(requireContext().applicationContext)

        // Checkboxes that allow user choose which data is visible on the graph
        val rawDataCheckBox = binding.rawDataCheckBox
        val filteredDataCheckBox = binding.filteredDataCheckBox
        val optimizeButton = binding.optimizeButton     // Optimize button
        val loadingContainer = binding.loadingContainer     // Loading container with progress bar
        val maximizeButton = binding.maximizeButton
        mVizChart = binding.visualizationChart           // The chart for data visualization

        if (mAuthStateManager.current.isAuthorized && !mConfiguration.hasConfigurationChanged()){
            displayAuthorized()
        }
        else {
            displayNotAuthorized()
        }

        // Set the maximize button according to the user theme
        val nightMod =
            this.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (nightMod == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            maximizeButton.setBackgroundResource(R.drawable.ic_baseline_open_in_full_24_light)
        } else {
            maximizeButton.setBackgroundResource(R.drawable.ic_baseline_open_in_full_24_dark)
        }

        // Preliminary view setup
        loadingContainer.visibility = View.GONE
        rawDataCheckBox.isEnabled = false
        filteredDataCheckBox.isEnabled = false
        rawDataCheckBox.setOnClickListener {
            makeDataVisible(rawDataCheckBox)
        }
        filteredDataCheckBox.setOnClickListener {
            makeDataVisible(filteredDataCheckBox)
        }

        optimizeButton.setOnClickListener {
            loadingContainer.visibility = View.VISIBLE
            mVizChart.setNoDataText("") // Empty string
            vizViewModel.runWorkflow()  //
        }

        maximizeButton.setOnClickListener {
            startActivity(
                Intent(
                    requireContext(), VisualizationActivity::class.java
                )//.putExtra("dd", vizViewModel.filterData)
            )
//            val intent = Intent(this, VisualizationActivity::class.java).apply {
//                putExtra(EXTRA_MESSAGE, response)
//            }
//            startActivity(intent)
        }

        // Observe the data we need for mVizChart
        vizViewModel.filterData.observe(viewLifecycleOwner) {
            // Hide the loading container
            loadingContainer.visibility = View.GONE

            // Populate this.mChartDataSets with the LiveData value
            mChartDataSets = createChartDataset(it.t, it.y, it.yHat)

            // Enable the checkboxes and set them to whether their corresponding data is visible -
            // useful for retaining their state on fragment switches
            rawDataCheckBox.isEnabled = true
            filteredDataCheckBox.isEnabled = true
            rawDataCheckBox.isChecked = mChartDataSets[0].isVisible
            filteredDataCheckBox.isChecked = mChartDataSets[1].isVisible

            // Draw the chart
            mVizChart.data = LineData(mChartDataSets)
            mVizChart.invalidate()
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     *
     */
    private fun displayAuthorized() {
        binding.authorizedViz.visibility = View.VISIBLE
        binding.notAuthorizedViz.visibility = View.GONE
        createVisualizationChart()
    }

    /**
     *
     */
    private fun displayNotAuthorized() {
        binding.authorizedViz.visibility = View.GONE
        binding.notAuthorizedViz.visibility = View.VISIBLE
//        Toast.makeText(
//            requireContext().applicationContext,
//            "User is not authorized",
//            Toast.LENGTH_SHORT
//        ).show()
    }

    /**
     *
     */
    private fun createVisualizationChart(){
        mVizChart.description.isEnabled = false
        mVizChart.setDrawGridBackground(false)
        mVizChart.isDragEnabled = true
        mVizChart.setScaleEnabled(true)
        mVizChart.setPinchZoom(false)
        mVizChart.animateXY(100, 100)

        val tf = Typeface.SANS_SERIF
        val l = mVizChart.legend
        l.typeface = tf

        val leftAxis = mVizChart.axisLeft
        leftAxis.typeface = tf

        mVizChart.axisRight.isEnabled = false

        val xAxis = mVizChart.xAxis
        xAxis.isEnabled = true
        xAxis.typeface = tf

        val nightMod =
            requireActivity().resources.configuration.uiMode and resConfiguration.UI_MODE_NIGHT_MASK
        if (nightMod == resConfiguration.UI_MODE_NIGHT_YES) {
            mVizChart.setBackgroundColor(Color.BLACK)
            mVizChart.axisLeft.textColor = Color.WHITE
            mVizChart.xAxis.textColor = Color.WHITE
            mVizChart.legend.textColor = Color.WHITE
        } else {
            mVizChart.setBackgroundColor(Color.WHITE)
            mVizChart.axisLeft.textColor = Color.BLACK
            mVizChart.xAxis.textColor = Color.BLACK
            mVizChart.legend.textColor = Color.BLACK
        }
    }

    /**
     * Makes the data visible according to whether each checkbox is checked
     * params:
     * [v] - the checkbox that was clicked
     */
    private fun makeDataVisible(v:CheckBox){
        when (v.id){
            R.id.filteredDataCheckBox -> {
                mChartDataSets[1].isVisible = binding.filteredDataCheckBox.isChecked
            }
            R.id.rawDataCheckBox ->{
                mChartDataSets[0].isVisible = binding.rawDataCheckBox.isChecked
            }
        }
        mVizChart.invalidate()
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
            getString(R.string.y_label)
        )
        rawDataset.color = Color.MAGENTA
        rawDataset.setDrawCircles(false)

        val filterDataset = LineDataSet(
            filterDataEntries,
            getString(R.string.yHat_label)
        )
        filterDataset.color = Color.RED
        filterDataset.setDrawCircles(false)
//        filterDataset.setDrawFilled(true)

        val dataSets: ArrayList<ILineDataSet> = ArrayList()
        dataSets.add(0, rawDataset)
        dataSets.add(1, filterDataset)

        return dataSets
    }

}