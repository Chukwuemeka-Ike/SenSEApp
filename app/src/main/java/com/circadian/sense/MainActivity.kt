package com.circadian.sense

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.circadian.sense.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var authManager: AuthManager
    val tag = "SenSE Debug"

    override fun onCreate(savedInstanceState: Bundle?) {
        // Inflate activity layout
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Create bottom navigation bar
        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        navView.setupWithNavController(navController)

//        // Create an authManager object to handle auth workflow
//        authManager = AuthManager(this)
////        Log.i(tag, "Auth State: ${authManager.authState.jsonSerializeString()}")
////        Log.i(tag, "Refresh token: ${authManager.authState.refreshToken}")
//        authManager.requestUserData()

        // Testing out OBF
        val OBF = ObserverBasedFilter(this)
//        OBF.optimizeFilter(dataRequestResponse)
        val L = floatArrayOf(0.0001242618548789415f, 0.0019148682768328732f, 0.09530636024613613f)
        OBF.simulateDynamics(L)

    }

}