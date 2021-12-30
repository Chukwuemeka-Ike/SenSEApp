package com.circadian.sense

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.JsonReader
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.circadian.sense.databinding.ActivityMainBinding
import com.circadian.sense.ui.visualization.VisualizationFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.json.JSONObject
import kotlin.concurrent.thread


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


        thread{
            // Start Python
            if (! Python.isStarted()) {
                Python.start(AndroidPlatform(this))
            }

            val py = Python.getInstance()
            val module = py.getModule("obf") // Python filename
            try {
                val bytes = module.callAttr("testFunc")//.toJava(FloatArray::class.java)
                Log.i(tag, "Output: ${bytes}")
            }
            catch(e: Exception){
                Log.i(tag, "Exception: ${e}")
            }
        }

//        try {
//            val bytes = module.callAttr("plot",
//                findViewById<EditText>(R.id.etX).text.toString(),
//                findViewById<EditText>(R.id.etY).text.toString())
//                .toJava(ByteArray::class.java)
//            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
//            findViewById<ImageView>(R.id.imageView).setImageBitmap(bitmap)
//
//            currentFocus?.let {
//                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
//                    .hideSoftInputFromWindow(it.windowToken, 0)
//            }
//        } catch (e: PyException) {
//            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
//        }
//        val OBF = ObserverBasedFilter()
//        Log.i(tag, "OBF: ${OBF.A}")
        // Create an authManager object to handle auth workflow
//        authManager = AuthManager(this)
//        Log.i(tag, "Auth State: ${authManager.authState.jsonSerializeString()}")
//        Log.i(tag, "Refresh token: ${authManager.authState.refreshToken}")
//        authManager.authState.createTokenRefreshRequest()




    }

    override fun onResume() {
        super.onResume()
//        authManager.requestData()
    }



}