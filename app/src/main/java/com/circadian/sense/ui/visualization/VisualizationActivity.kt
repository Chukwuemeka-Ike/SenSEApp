package com.circadian.sense.ui.visualization

import android.graphics.Color
import android.graphics.Typeface
import android.content.res.Configuration as resConfig
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.circadian.sense.R
import com.circadian.sense.databinding.ActivityVisualizationBinding
import com.circadian.sense.utilities.*
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import net.openid.appauth.AuthorizationService
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class VisualizationActivity : AppCompatActivity() {
    private val TAG = "VisualizationActivity"

//    private val vizViewModel: VisualizationViewModel
    private lateinit var binding: ActivityVisualizationBinding

    private lateinit var mVizChart: LineChart
    private lateinit var mChartDataSets: ArrayList<ILineDataSet>
//    private lateinit var mAuthStateManager: AuthStateManager
//    private lateinit var mConfiguration: Configuration
//    private lateinit var mAuthService: AuthorizationService
//    private lateinit var mOBF: ObserverBasedFilter
//    private lateinit var mDataManager: DataManager
//    private lateinit var mOrchestrator: Orchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate layout
        binding = ActivityVisualizationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val minimizeButton = binding.minimizeButton

        // Checkboxes that allow user choose which data is visible on the graph
        val rawDataCheckBox = binding.rawDataCheckBox
        val filteredDataCheckBox = binding.filteredDataCheckBox
        mVizChart = binding.visualizationChart           // The chart for data visualization
        createVisualizationChart()

        // Set the minimize button according to the user theme
        val nightMod =
            this.resources.configuration.uiMode and resConfig.UI_MODE_NIGHT_MASK
        if (nightMod == resConfig.UI_MODE_NIGHT_YES) {
            minimizeButton.setBackgroundResource(R.drawable.ic_baseline_close_fullscreen_24_light)
        } else {
            minimizeButton.setBackgroundResource(R.drawable.ic_baseline_close_fullscreen_24_dark)
        }

        minimizeButton.setOnClickListener {
            finish()
        }
        rawDataCheckBox.setOnClickListener {
            makeDataVisible(rawDataCheckBox)
        }
        filteredDataCheckBox.setOnClickListener {
            makeDataVisible(filteredDataCheckBox)
        }

        val vizViewModel: VisualizationViewModel by viewModels()

//        mAuthStateManager = AuthStateManager.getInstance(application.applicationContext)
//        mConfiguration = Configuration.getInstance(application.applicationContext)
//        mAuthService = AuthorizationService(application.applicationContext)
//        mOBF = ObserverBasedFilter()
//        mDataManager = DataManager(application.applicationContext)
//        mOrchestrator = Orchestrator(
//            mAuthStateManager,
//            mConfiguration,
//            mAuthService,
//            mDataManager,
//            mOBF
//        )
        vizViewModel.runWorkflow()

        // Observe the data we need for mVizChart
        vizViewModel.chartData.observe(this) {
            // Enable the checkboxes and set them to whether their corresponding data is visible
            // Useful for retaining their state on fragment switches
            rawDataCheckBox.isEnabled = true
            filteredDataCheckBox.isEnabled = true
            rawDataCheckBox.isChecked = it[0].isVisible
            filteredDataCheckBox.isChecked = it[1].isVisible

            // Populate this.mChartDataSets with the liveDataset and draw mVizChart
            mChartDataSets = it
            mVizChart.data = LineData(mChartDataSets)
            mVizChart.invalidate()
        }

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
        mVizChart.animateXY(200, 200)
        mVizChart.axisRight.isEnabled = false
        mVizChart.setDrawMarkers(false)

        val tf = Typeface.SANS_SERIF
        mVizChart.legend.typeface = tf

        val leftAxis = mVizChart.axisLeft
        leftAxis.typeface = tf
        leftAxis.setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
        leftAxis.spaceTop = 15f

        val xAxis = mVizChart.xAxis
        xAxis.isEnabled = true
        xAxis.typeface = tf
        xAxis.setLabelCount(5, false)
        xAxis.granularity = 1440/12f
        xAxis.setCenterAxisLabels(false)

        xAxis.position = XAxis.XAxisPosition.TOP_INSIDE
        xAxis.valueFormatter = object : ValueFormatter() {
            private val mFormat = SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH)
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val millis: Long = TimeUnit.MINUTES.toMillis(value.toLong())
                return mFormat.format(Date(millis))
            }
        }


        val nightMod =
            this.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (nightMod == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
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
     * params:
     * [v] - the checkbox that was clicked
     */
    private fun makeDataVisible(v: CheckBox){
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
}