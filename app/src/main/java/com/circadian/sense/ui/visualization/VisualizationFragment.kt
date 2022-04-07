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
import com.circadian.sense.DAILY_OPTIMIZATION_WORKER_TAG
import com.circadian.sense.NUM_DATA_POINTS_PER_DAY
import com.circadian.sense.NUM_DAYS
import com.circadian.sense.R
import com.circadian.sense.databinding.FragmentVisualizationBinding
import com.circadian.sense.utilities.AuthStateManager
import com.circadian.sense.utilities.Configuration
import com.github.mikephil.charting.charts.BubbleChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
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

    private lateinit var mDailyDataChart: LineChart
    private lateinit var mAveragePhaseChart: BubbleChart
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
//            vizViewModel.runWorkflow()  //
//        }

        val loadingContainer = binding.loadingContainer     // Loading container with progress bar
//        loadingContainer.visibility = View.VISIBLE

        mDailyDataChart = binding.dailyDataChart           // The chart for data visualization
        mAveragePhaseChart = binding.averagePhaseChart

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

//        binding.vizExpositionButton.setOnClickListener { showPopupWindow(it, container) }

        // Listen for WorkManager. If it's succeeded or enqueued and mVizChart hasn't been populated, createChartDataset
        WorkManager.getInstance(requireContext())
            .getWorkInfosByTagLiveData(DAILY_OPTIMIZATION_WORKER_TAG)
            .observe(viewLifecycleOwner) { workInfos ->
                if (workInfos.isNotEmpty() && mDailyDataChart.data == null && workInfos[0] != null) {
                    Log.i(TAG, "WEJAILEJF")
                    Log.i(TAG, "Work info: ${workInfos[0].state}")
                    if ((workInfos[0].state == WorkInfo.State.ENQUEUED) || (workInfos[0].state == WorkInfo.State.SUCCEEDED)) {
                        Log.i(
                            TAG,
                            "Work in successful or enqueued state, so creating chart dataset"
                        )
                        vizViewModel.createChartDataset()
                        loadingContainer.visibility = View.GONE
                    } else if (workInfos[0].state == WorkInfo.State.RUNNING) {
                        Log.i(TAG, "Work currently running")
                        loadingContainer.visibility = View.VISIBLE
                    }
                }
            }

        // Observe the LiveData we need for mVizChart
        vizViewModel.dailyDataChartDataset.observe(viewLifecycleOwner) {
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
            mDailyDataChart.data = LineData(mChartDataSets)
            mDailyDataChart.invalidate()

        }

        vizViewModel.averagePhaseDataset.observe(viewLifecycleOwner) {
            loadingContainer.visibility = View.GONE
            mAveragePhaseChart.data = it
            mAveragePhaseChart.invalidate()
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
        // DailyDataChart setup
        mDailyDataChart.description.isEnabled = false
        mDailyDataChart.setDrawGridBackground(false)
        mDailyDataChart.isDragEnabled = true
        mDailyDataChart.setScaleEnabled(true)
        mDailyDataChart.setPinchZoom(false)
        mDailyDataChart.animateXY(200, 200)
        mDailyDataChart.axisRight.isEnabled = false
        mDailyDataChart.setDrawMarkers(false)

        val tf = Typeface.SANS_SERIF
        mDailyDataChart.legend.typeface = tf

        mDailyDataChart.axisLeft.typeface = tf
        mDailyDataChart.axisLeft.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
        mDailyDataChart.axisLeft.spaceTop = 20f

        mDailyDataChart.xAxis.isEnabled = true
        mDailyDataChart.xAxis.typeface = tf
        mDailyDataChart.xAxis.setLabelCount(3, true)
        mDailyDataChart.xAxis.granularity = NUM_DATA_POINTS_PER_DAY / 24f
        mDailyDataChart.xAxis.setCenterAxisLabels(false)
        mDailyDataChart.xAxis.setDrawLimitLinesBehindData(true)

        // Convert the x-axis millis values to string timestamps
        mDailyDataChart.xAxis.position = XAxis.XAxisPosition.TOP_INSIDE
        mDailyDataChart.xAxis.valueFormatter = object : ValueFormatter() {
            private val mFormat = SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH)
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val millis: Long = TimeUnit.MINUTES.toMillis(value.toLong())
                return mFormat.format(Date(millis))
            }
        }

        // AveragePhaseChart setup
        mAveragePhaseChart.description.isEnabled = false
        mAveragePhaseChart.setDrawGridBackground(false)
        mAveragePhaseChart.isDragEnabled = true
        mAveragePhaseChart.setScaleEnabled(false)
        mAveragePhaseChart.setPinchZoom(false)
        mAveragePhaseChart.axisRight.isEnabled = false

        mAveragePhaseChart.legend.typeface = tf
        mAveragePhaseChart.axisLeft.typeface = tf
        mAveragePhaseChart.axisLeft.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
        mAveragePhaseChart.axisLeft.spaceTop = 20f
        mAveragePhaseChart.axisLeft.axisMinimum = 0f
        mAveragePhaseChart.axisLeft.axisMaximum = NUM_DAYS.toFloat() + 1
        mAveragePhaseChart.axisLeft.isEnabled = true
        mAveragePhaseChart.axisLeft.isInverted = true
        mAveragePhaseChart.axisLeft.setDrawLimitLinesBehindData(true)
        mAveragePhaseChart.axisLeft.setLabelCount(0, true)

        mAveragePhaseChart.xAxis.axisMinimum = -12f
        mAveragePhaseChart.xAxis.axisMaximum = 12f


        // Color the chart according to user theme
        val nightMod =
            requireActivity().resources.configuration.uiMode and resConfiguration.UI_MODE_NIGHT_MASK
        if (nightMod == resConfiguration.UI_MODE_NIGHT_YES) {
            mDailyDataChart.setBackgroundColor(Color.BLACK)
            mDailyDataChart.axisLeft.textColor = Color.WHITE
            mDailyDataChart.xAxis.textColor = Color.WHITE
            mDailyDataChart.legend.textColor = Color.WHITE
            mAveragePhaseChart.setBackgroundColor(Color.BLACK)
            mAveragePhaseChart.axisLeft.textColor = Color.WHITE
            mAveragePhaseChart.xAxis.textColor = Color.WHITE
            mAveragePhaseChart.legend.textColor = Color.WHITE
        } else {
            mDailyDataChart.setBackgroundColor(Color.WHITE)
            mDailyDataChart.axisLeft.textColor = Color.BLACK
            mDailyDataChart.xAxis.textColor = Color.BLACK
            mDailyDataChart.legend.textColor = Color.BLACK
            mAveragePhaseChart.setBackgroundColor(Color.WHITE)
            mAveragePhaseChart.axisLeft.textColor = Color.BLACK
            mAveragePhaseChart.xAxis.textColor = Color.BLACK
            mAveragePhaseChart.legend.textColor = Color.BLACK
        }

        // Get NUM_DAYS ago in Epoch Minutes
        val day1InMinutes = TimeUnit.SECONDS.toMinutes(
            LocalDate.now()
                .minusDays(NUM_DAYS.toLong())
                .atStartOfDay(ZoneId.systemDefault())
                .toEpochSecond()
        )

        for (i in 0 until NUM_DAYS) {
            val llXAxis =
                LimitLine((day1InMinutes + (NUM_DATA_POINTS_PER_DAY * i.toFloat())), "Day ${i + 1}")
            llXAxis.lineWidth = 1f
            llXAxis.lineColor = mDailyDataChart.xAxis.textColor
            llXAxis.textColor = mDailyDataChart.xAxis.textColor
            llXAxis.enableDashedLine(10f, 10f, 0f)
            llXAxis.labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM
            mDailyDataChart.xAxis.addLimitLine(llXAxis)
        }

        for (i in 1 until NUM_DAYS + 1) {
            val llXAxis = LimitLine(i.toFloat(), "Day ${i}")
            llXAxis.lineWidth = 1f
            llXAxis.lineColor = mAveragePhaseChart.xAxis.textColor
            llXAxis.textColor = mAveragePhaseChart.xAxis.textColor
            llXAxis.enableDashedLine(10f, 10f, 0f)
            llXAxis.labelPosition = LimitLine.LimitLabelPosition.LEFT_TOP
            mAveragePhaseChart.axisLeft.addLimitLine(llXAxis)
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
        mDailyDataChart.invalidate()
    }

}