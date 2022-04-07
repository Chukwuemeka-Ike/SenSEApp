package com.circadian.sense.ui.visualization

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.CheckBox
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.circadian.sense.NUM_DATA_POINTS_PER_DAY
import com.circadian.sense.NUM_DAYS
import com.circadian.sense.R
import com.circadian.sense.databinding.ActivityVisualizationBinding
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
import android.content.res.Configuration as resConfig

class VisualizationActivity : AppCompatActivity() {
    private val TAG = "VisualizationActivity"

    private lateinit var binding: ActivityVisualizationBinding

    private lateinit var mDailyDataChart: LineChart
    private lateinit var mChartDataSets: ArrayList<ILineDataSet>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create the same VizViewModel that VisualizationFragment uses
        val vizViewModel: VisualizationViewModel by viewModels()
//        vizViewModel.createChartDataset()

        // Inflate layout
        binding = ActivityVisualizationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val minimizeButton = binding.minimizeButton

        // Checkboxes that allow user choose which data is visible on the graph
        val rawDataCheckBox = binding.rawDataCheckBox
        val filteredDataCheckBox = binding.filteredDataCheckBox

        // The chart for data visualization
        mDailyDataChart = binding.dailyDataChart
        createVisualizationChart()

        // Set the minimize button according to the user theme
        val nightMod =
            this.resources.configuration.uiMode and resConfig.UI_MODE_NIGHT_MASK
        if (nightMod == resConfig.UI_MODE_NIGHT_YES) {
            minimizeButton.setBackgroundResource(R.drawable.ic_baseline_close_fullscreen_24_light)
        } else {
            minimizeButton.setBackgroundResource(R.drawable.ic_baseline_close_fullscreen_24_dark)
        }

        // If minimize is clicked, close the activity
        minimizeButton.setOnClickListener {
            finish()
        }
        rawDataCheckBox.setOnClickListener {
            makeDataVisible(rawDataCheckBox)
        }
        filteredDataCheckBox.setOnClickListener {
            makeDataVisible(filteredDataCheckBox)
        }

        // Observe the data we need for mVizChart
        vizViewModel.dailyDataChartDataset.observe(this) {
            // Enable the checkboxes and set them to whether their corresponding data is visible
            // Useful for retaining their state on screen switches
            rawDataCheckBox.isEnabled = true
            filteredDataCheckBox.isEnabled = true
            rawDataCheckBox.isChecked = it[0].isVisible
            filteredDataCheckBox.isChecked = it[1].isVisible

            // Populate this.mChartDataSets with the liveDataset and draw mVizChart
            mChartDataSets = it
            mDailyDataChart.data = LineData(mChartDataSets)
            mDailyDataChart.invalidate()
        }

    }

    /**
     * Creates the visualization chart and sets all its default values before the plots are made
     */
    private fun createVisualizationChart() {
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

        val leftAxis = mDailyDataChart.axisLeft
        leftAxis.typeface = tf
        leftAxis.setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
        leftAxis.spaceTop = 20f

        val xAxis = mDailyDataChart.xAxis
        xAxis.isEnabled = true
        xAxis.typeface = tf
        xAxis.setLabelCount(5, true)
        xAxis.granularity = NUM_DATA_POINTS_PER_DAY / 48f
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
            this.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (nightMod == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            mDailyDataChart.setBackgroundColor(Color.BLACK)
            leftAxis.textColor = Color.WHITE
            xAxis.textColor = Color.WHITE
            mDailyDataChart.legend.textColor = Color.WHITE
        } else {
            mDailyDataChart.setBackgroundColor(Color.WHITE)
            leftAxis.textColor = Color.BLACK
            xAxis.textColor = Color.BLACK
            mDailyDataChart.legend.textColor = Color.BLACK
        }

        // Get NUM_DAYS ago in Epoch Minutes
        val day1InMinutes = TimeUnit.SECONDS.toMinutes(
            LocalDate.now()
                .minusDays(NUM_DAYS.toLong())
                .atStartOfDay(ZoneId.systemDefault())
                .toEpochSecond()
        )
        for (i in 0 until NUM_DAYS){
            val llXAxis = LimitLine( (day1InMinutes+(NUM_DATA_POINTS_PER_DAY*i.toFloat())), "Day ${i + 1}")
            llXAxis.lineWidth = 1f
            llXAxis.lineColor = xAxis.textColor
            llXAxis.textColor = xAxis.textColor
            llXAxis.enableDashedLine(10f, 10f, 0f)
            llXAxis.labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM
            xAxis.addLimitLine(llXAxis)
//            Log.i(TAG, "Added limit lines $i")
        }
    }

    /**
     * Makes the data visible according to whether each checkbox is checked
     * params:
     * [v] - the checkbox that was clicked
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