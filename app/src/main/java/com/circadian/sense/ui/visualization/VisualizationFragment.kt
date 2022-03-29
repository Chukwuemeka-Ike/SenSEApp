package com.circadian.sense.ui.visualization

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.circadian.sense.R
import com.circadian.sense.DAILY_OPTIMIZATION_WORKER_TAG
import com.circadian.sense.databinding.FragmentVisualizationBinding
import com.circadian.sense.utilities.AuthStateManager
import com.circadian.sense.utilities.Configuration
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
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
    ): View {

        // Set the ViewBinding
        _binding = FragmentVisualizationBinding.inflate(inflater, container, false)
        val root: View = binding.root

        mAuthStateManager = AuthStateManager.getInstance(requireContext().applicationContext)
        mConfiguration = Configuration.getInstance(requireContext().applicationContext)

        // Checkboxes that allow user choose which data is visible on the graph
        val rawDataCheckBox = binding.rawDataCheckBox
        val filteredDataCheckBox = binding.filteredDataCheckBox
        rawDataCheckBox.isEnabled = false
        filteredDataCheckBox.isEnabled = false

        rawDataCheckBox.setOnClickListener {
            makeDataVisible(rawDataCheckBox)
        }
        filteredDataCheckBox.setOnClickListener {
            makeDataVisible(filteredDataCheckBox)
        }

//        val optimizeButton = binding.optimizeButton     // Optimize button
//        optimizeButton.setOnClickListener {
//            mVizChart.setNoDataText("") // Empty string
//            loadingContainer.visibility = View.VISIBLE
//            vizViewModel.runWorkflow()  //
//        }

        val loadingContainer = binding.loadingContainer     // Loading container with progress bar
        loadingContainer.visibility = View.GONE

        mVizChart = binding.visualizationChart           // The chart for data visualization

        if (mAuthStateManager.current.isAuthorized && !mConfiguration.hasConfigurationChanged()) {
            displayAuthorized()
        } else {
            displayNotAuthorized()
        }

        // Set the maximize button according to the user theme
        val maximizeButton = binding.maximizeButton
        maximizeButton.isEnabled = false

        val nightMod =
            this.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (nightMod == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            maximizeButton.setBackgroundResource(R.drawable.ic_baseline_open_in_full_24_light)
        } else {
            maximizeButton.setBackgroundResource(R.drawable.ic_baseline_open_in_full_24_dark)
        }

        maximizeButton.setOnClickListener {
            startActivity(
                Intent(
                    requireContext(), VisualizationActivity::class.java
                )
            )
        }


        // Listen for WorkManager. If it's succeeded and mVizChart hasn't been populated, createChartDataset
        WorkManager.getInstance(requireContext()).getWorkInfosByTagLiveData(DAILY_OPTIMIZATION_WORKER_TAG)
            .observe(viewLifecycleOwner) { workInfos ->
                if (workInfos.isNotEmpty() && mVizChart.data == null && workInfos[0] != null){
                    if (workInfos[0].state == WorkInfo.State.SUCCEEDED) {
                        Log.i(TAG,"Work Infos not empty, and successful work state, so creating chart dataset")
                        vizViewModel.createChartDataset()
                        loadingContainer.visibility = View.GONE
                    } else {
                        loadingContainer.visibility = View.VISIBLE
                    }
                }
            }

        // Observe the LiveData we need for mVizChart
        vizViewModel.chartData.observe(viewLifecycleOwner) {
            // Hide the loading container
            loadingContainer.visibility = View.GONE

            // Enable the checkboxes and set them to whether their corresponding data is visible
            // Useful for retaining their state on fragment switches
            rawDataCheckBox.isEnabled = true
            filteredDataCheckBox.isEnabled = true
            rawDataCheckBox.isChecked = it[0].isVisible
            filteredDataCheckBox.isChecked = it[1].isVisible

            maximizeButton.isEnabled = true

            // Populate mChartDataSets with the liveDataset and draw mVizChart
            mChartDataSets = it
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
     * Shows the Authorized container
     */
    private fun displayAuthorized() {
        binding.authorizedViz.visibility = View.VISIBLE
        binding.notAuthorizedViz.visibility = View.GONE
        createVisualizationChart()
    }

    /**
     * Shows the Not Authorized container
     */
    private fun displayNotAuthorized() {
        binding.authorizedViz.visibility = View.GONE
        binding.notAuthorizedViz.visibility = View.VISIBLE
    }

    /**
     * Creates the visualization chart and sets all its default values before the plots are made
     */
    private fun createVisualizationChart() {
        mVizChart.description.isEnabled = false
        mVizChart.setDrawGridBackground(false)
        mVizChart.isDragEnabled = true
        mVizChart.setScaleEnabled(true)
        mVizChart.setPinchZoom(false)
        mVizChart.animateXY(200, 200)
        mVizChart.axisRight.isEnabled = false
        mVizChart.setDrawMarkers(false)

        val tf = Typeface.SANS_SERIF
        mVizChart.legend.typeface = tf

        val leftAxis = mVizChart.axisLeft
        leftAxis.typeface = tf
        leftAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
        leftAxis.spaceTop = 20f

        val xAxis = mVizChart.xAxis
        xAxis.isEnabled = true
        xAxis.typeface = tf
        xAxis.setLabelCount(3, false)
        xAxis.granularity = 1440 / 24f
        xAxis.setCenterAxisLabels(false)

        // Convert the x-axis millis values to string timestamps
        xAxis.position = XAxis.XAxisPosition.TOP_INSIDE
        xAxis.valueFormatter = object : ValueFormatter() {
            private val mFormat = SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH)
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val millis: Long = TimeUnit.MINUTES.toMillis(value.toLong())
                return mFormat.format(Date(millis))
            }
        }

        // Color the chart according to user theme
        val nightMod =
            requireActivity().resources.configuration.uiMode and resConfiguration.UI_MODE_NIGHT_MASK
        if (nightMod == resConfiguration.UI_MODE_NIGHT_YES) {
            mVizChart.setBackgroundColor(Color.BLACK)
            leftAxis.textColor = Color.WHITE
            xAxis.textColor = Color.WHITE
            mVizChart.legend.textColor = Color.WHITE
        } else {
            mVizChart.setBackgroundColor(Color.WHITE)
            leftAxis.textColor = Color.BLACK
            xAxis.textColor = Color.BLACK
            mVizChart.legend.textColor = Color.BLACK
        }
    }

    /**
     * Makes the data visible according to whether each checkbox is checked
     * @param [v] - the checkbox that was clicked
     */
    private fun makeDataVisible(v: CheckBox) {
        when (v.id) {
            R.id.filteredDataCheckBox -> {
                mChartDataSets[1].isVisible = binding.filteredDataCheckBox.isChecked
            }
            R.id.rawDataCheckBox -> {
                mChartDataSets[0].isVisible = binding.rawDataCheckBox.isChecked
            }
        }
        mVizChart.invalidate()
    }

}