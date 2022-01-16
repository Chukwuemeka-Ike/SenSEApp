package com.circadian.sense.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.circadian.sense.utilities.AuthManager
import com.circadian.sense.databinding.ActivityLoginBinding
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_SHORT
import com.google.android.material.snackbar.Snackbar

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {

        // Initial activity creation and layout inflation
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Create an authManager object to handle auth workflow
        authManager = AuthManager(this)

        // If authorized, close LoginActivity
        if (authManager.authState.isAuthorized) {
            Toast.makeText(this, "Already logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Request auth code on login click - we only get here if not already authorized
        binding.loginButton.setOnClickListener {
            authManager.requestAuthorizationCode()
        }
        // TODO: CHECK IF THIS CAN BE PUT IN ONSTART()
//        authManager.continueAuthWorkflow(binding, intent)

    }

    override fun onStart() {
        super.onStart()

        // If authorized, close LoginActivity
        if (authManager.authState.isAuthorized) {
            Toast.makeText(this, "Already logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        authManager.continueAuthWorkflow(binding, intent)
    }

    // TODO: Get rid of this?
    override fun onResume() {
        super.onResume()

        // If authorized, close LoginActivity
        if (authManager.authState.isAuthorized) {
            Snackbar.make(binding.root, "Already logged in", LENGTH_SHORT).show()
            finish()
            return
        }
    }

}