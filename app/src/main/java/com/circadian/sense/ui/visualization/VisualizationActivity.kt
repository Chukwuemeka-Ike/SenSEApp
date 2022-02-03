package com.circadian.sense.ui.visualization

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.circadian.sense.R
import com.circadian.sense.databinding.ActivityVisualizationBinding

class VisualizationActivity : AppCompatActivity() {
    private val TAG = "VisualizationActivity"

//    private val vizViewModel: VisualizationViewModel
    private lateinit var binding: ActivityVisualizationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate layout
        binding = ActivityVisualizationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val minimizeButton = binding.minimizeButton

        // Set the minimize button according to the user theme
        val nightMod =
            this.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (nightMod == Configuration.UI_MODE_NIGHT_YES) {
            minimizeButton.setBackgroundResource(R.drawable.ic_baseline_close_fullscreen_24_light)
        } else {
            minimizeButton.setBackgroundResource(R.drawable.ic_baseline_close_fullscreen_24_dark)
        }

        minimizeButton.setOnClickListener {
            finish()
        }
        val intentData = intent?.getStringExtra("dd")

//        // Observe the data we need for mVizChart
//        vizViewModel.filterData.observe(viewLifecycleOwner) {
//            // Hide the loading container
//            loadingContainer.visibility = View.GONE
//
//            // Populate this.mChartDataSets with the LiveData value
//            mChartDataSets = createChartDataset(it.t, it.y, it.yHat)
//
//            // Enable the checkboxes and set them to whether their corresponding data is visible -
//            // useful for retaining their state on fragment switches
//            rawDataCheckBox.isEnabled = true
//            filteredDataCheckBox.isEnabled = true
//            rawDataCheckBox.isChecked = mChartDataSets[0].isVisible
//            filteredDataCheckBox.isChecked = mChartDataSets[1].isVisible
//
//            // Draw the chart
//            mVizChart.data = LineData(mChartDataSets)
//            mVizChart.invalidate()
//        }
    }
}