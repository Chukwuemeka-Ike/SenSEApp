package com.circadian.sense

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.circadian.sense.databinding.ActivityMainBinding
import com.circadian.sense.utilities.DailyOptimizationWorker
import com.circadian.sense.utilities.Orchestrator
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_SenSE)

        // Inflate layout
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Create bottom navigation bar
        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        navView.setupWithNavController(navController)

//        // Used this to manually rerun the work after a failure preventing
//        // unique work from getting enqueued
//        val dailyOptimizationWorkRequest =
//            OneTimeWorkRequestBuilder<DailyOptimizationWorker>()
//                .setConstraints(WORK_MANAGER_CONSTRAINTS)
//                .addTag(DAILY_OPTIMIZATION_WORKER_TAG)
//                .build()
//        WorkManager.getInstance(this.applicationContext)
//            .enqueue(
//                dailyOptimizationWorkRequest
//            )
//        Log.i(TAG, "WorkManager work list: " +
//                "${WorkManager.getInstance(this.applicationContext).getWorkInfosByTag(
//                    DAILY_OPTIMIZATION_WORKER_TAG)}")

        // Start Python if not started
        if (!Python.isStarted()) {
            Log.i(TAG, "Starting Python")
            Python.start(AndroidPlatform(this))
        }

//        val mOrchestrator = Orchestrator(application.applicationContext)
//        val job = Job()
//        val uiScope = CoroutineScope(Dispatchers.Main + job)
//        uiScope.launch(Dispatchers.IO) {
//            val data = mOrchestrator.getFreshData()
//            Log.i(TAG, "Data: ${data?.dataTimestamp}")
//        }
    }


}