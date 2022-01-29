package com.circadian.sense

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.circadian.sense.databinding.ActivityMainBinding
import com.circadian.sense.ui.settings.LoginActivity
import com.circadian.sense.utilities.AuthStateManager
import com.circadian.sense.utilities.Configuration
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import net.openid.appauth.*
import net.openid.appauth.AuthorizationService.TokenResponseCallback
import net.openid.appauth.ClientAuthentication.UnsupportedAuthenticationMethod
import okio.buffer
import okio.source
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.charset.Charset
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private val KEY_USER_INFO = "userInfo"

    private lateinit var binding: ActivityMainBinding

    private lateinit var mAuthService: AuthorizationService
    private lateinit var mAuthStateManager: AuthStateManager
    private lateinit var mConfiguration: Configuration
    private lateinit var mExecutor: ExecutorService
    private val mUserInfoJson = AtomicReference<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mAuthStateManager = AuthStateManager.getInstance(this)
        mExecutor = Executors.newSingleThreadExecutor()
        mConfiguration = Configuration.getInstance(this)

        mAuthService = AuthorizationService(
            this,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(mConfiguration.connectionBuilder)
                .build()
        )

//        if (!mConfiguration!!.isValid){
//            displayError(mConfiguration!!.getConfigurationError()!!)
//            return
//        }
//
//        // TODO: Security check. Someone shouldn't be able to just
//        //  add something in and we accept it
//        if (mConfiguration.hasConfigurationChanged()) {
//            // discard any existing authorization state due to the change of configuration
//            Log.i(TAG, getString(R.string.configuration_has_changed))
//            mAuthStateManager.replace(AuthState())
//            mConfiguration.acceptConfiguration()
//
//            Toast.makeText(
//                this,
//                "Configuration change detected",
//                Toast.LENGTH_SHORT
//            ).show()
////            signOut()
////            displayNotAuthorized("Configuration")
////            return
//        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Create bottom navigation bar
        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        navView.setupWithNavController(navController)
//        navController.navigate(R.id.settingsFragment)

        Log.i(TAG,"${mAuthStateManager.current.lastRegistrationResponse?.jsonSerializeString()}")
        Log.i(TAG,"${mAuthStateManager.current.lastAuthorizationResponse?.jsonSerializeString()}")
        Log.i(TAG,"${mAuthStateManager.current.lastTokenResponse?.jsonSerializeString()}")

//        // Start Python if not started
//        if (! Python.isStarted()) {
//            Log.i(TAG, "Starting Python")
//            Python.start(AndroidPlatform(this))
//        }
//
//
//        if (!mAuthStateManager.current.isAuthorized) {
//            Log.i(TAG, "User not authorized")
//            mExecutor!!.submit(Runnable { initializeAppAuth() })
//        }
    }

    override fun onResume() {
        super.onResume()

//        if (mExecutor.isShutdown) {
//            mExecutor = Executors.newSingleThreadExecutor()
//        }
//        if (mAuthStateManager.current.isAuthorized) {
//            displayAuthorized()
//            return
//        }
//
//        Log.i(TAG, "onStart")
//        Log.i(TAG, "Intent: ${intent}")
//
//        // the stored AuthState is incomplete, so check if we are currently receiving the result of
//        // the authorization flow from the browser.
//        val response = AuthorizationResponse.fromIntent(intent)
//        val ex = AuthorizationException.fromIntent(intent)
//
//        // TODO: Check if this is redundant
//        if (response != null || ex != null) {
//            mAuthStateManager.updateAfterAuthorization(response, ex)
//        }
//
//        Log.i(TAG, "${response}, ${response?.authorizationCode}, $ex")
//        if (response != null && response.authorizationCode != null) {
//            // authorization code exchange is required
//            mAuthStateManager.updateAfterAuthorization(response, ex)
//            exchangeAuthorizationCode(response)
//        } else if (ex != null) {
//            displayNotAuthorized("Authorization flow failed: " + ex.message)
//        } else {
//            displayNotAuthorized("No authorization state retained - reauthorization required")
//        }
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
//        // user info is retained to survive activity restarts, such as when rotating the
//        // device or switching apps. This isn't essential, but it helps provide a less
//        // jarring UX when these events occur - data does not just disappear from the view.
//        if (mUserInfoJson.get() != null) {
//            state.putString(
//                KEY_USER_INFO,
//                mUserInfoJson.toString()
//            )
//        }
    }

    override fun onStop() {
        super.onStop()
        mExecutor.shutdownNow()
    }

    override fun onDestroy() {
        super.onDestroy()
        mAuthService.dispose()
        mExecutor.shutdownNow()
    }

//    @MainThread
//    private fun displayNotAuthorized(explanation: String) {
//        // TODO: Implement something useful
//        Toast.makeText(this, "Not authorized", Toast.LENGTH_SHORT).show()
////        findViewById<View>(R.id.not_authorized).visibility = View.VISIBLE
////        findViewById<View>(R.id.authorized).visibility = View.GONE
////        findViewById<View>(R.id.loading_container).visibility = View.GONE
////        (findViewById<View>(R.id.explanation) as TextView).text = explanation
////        findViewById<View>(R.id.reauth).setOnClickListener { view: View? -> signOut() }
//
//        Log.i(TAG, "Not authorized: ${explanation}")
//    }
//
//    @MainThread
//    private fun displayLoading(message: String) {
////        findViewById<View>(R.id.loading_container).visibility = View.VISIBLE
////        findViewById<View>(R.id.authorized).visibility = View.GONE
////        findViewById<View>(R.id.not_authorized).visibility = View.GONE
////        (findViewById<View>(R.id.loading_description) as TextView).text = message
//    }
//
//    @MainThread
//    private fun displayAuthorized() {
//        // TODO: Implement something useful
////        findViewById<View>(R.id.authorized).visibility = View.VISIBLE
////        findViewById<View>(R.id.not_authorized).visibility = View.GONE
////        findViewById<View>(R.id.loading_container).visibility = View.GONE
//        val state: AuthState = mAuthStateManager.current
//
//        val userInfo = mUserInfoJson.get()
//
//        Log.i(TAG, "Authorization successful")
//
//    }
//
//    @MainThread
//    private fun refreshAccessToken() {
//        displayLoading("Refreshing access token")
//        performTokenRequest(
//            mAuthStateManager.current.createTokenRefreshRequest()
//        ) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
//            handleAccessTokenResponse(
//                tokenResponse,
//                authException
//            )
//        }
//    }
//
//    @MainThread
//    private fun exchangeAuthorizationCode(authorizationResponse: AuthorizationResponse) {
//        displayLoading("Exchanging authorization code")
//        performTokenRequest(
//            authorizationResponse.createTokenExchangeRequest()
//        ) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
//            handleCodeExchangeResponse(
//                tokenResponse,
//                authException
//            )
//        }
//    }
//
//    @MainThread
//    private fun performTokenRequest(
//        request: TokenRequest,
//        callback: TokenResponseCallback
//    ) {
//        val clientAuthentication: ClientAuthentication
//        clientAuthentication = try {
//            mAuthStateManager.current.clientAuthentication
//        } catch (ex: UnsupportedAuthenticationMethod) {
//            Log.d(
//                TAG,
//                "Token request cannot be made, client authentication for the token "
//                        + "endpoint could not be constructed (%s)",
//                ex
//            )
//            displayNotAuthorized("Client authentication method is unsupported")
//            return
//        }
//        mAuthService.performTokenRequest(
//            request,
//            clientAuthentication,
//            callback
//        )
//    }
//
//    @WorkerThread
//    private fun handleAccessTokenResponse(
//        tokenResponse: TokenResponse?,
//        authException: AuthorizationException?
//    ) {
//        mAuthStateManager.updateAfterTokenResponse(tokenResponse, authException)
//        runOnUiThread { displayAuthorized() }
//    }
//
//    @WorkerThread
//    private fun handleCodeExchangeResponse(
//        tokenResponse: TokenResponse?,
//        authException: AuthorizationException?
//    ) {
//        mAuthStateManager.updateAfterTokenResponse(tokenResponse, authException)
//        if (!mAuthStateManager.current.isAuthorized) {
//            val message = ("Authorization Code exchange failed"
//                    + if (authException != null) authException.error else "")
//
//            // WrongThread inference is incorrect for lambdas
//            runOnUiThread { displayNotAuthorized(message) }
//        } else {
//            runOnUiThread { displayAuthorized() }
//        }
//    }
//
//    @MainThread
//    private fun fetchUserInfo(accessToken: String, idToken: String, ex: AuthorizationException?) {
//        if (ex != null) {
//            Log.e(TAG, getString(R.string.token_refresh_failed))
//            mUserInfoJson.set(null)
//            runOnUiThread { displayAuthorized() }
//            return
//        }
//        val discovery: AuthorizationServiceDiscovery =
//            mAuthStateManager.current.authorizationServiceConfiguration!!.discoveryDoc!!
//        val userInfoEndpoint = if (mConfiguration.getUserInfoEndpointUri() != null) Uri.parse(
//            mConfiguration.getUserInfoEndpointUri().toString()
//        ) else Uri.parse(discovery.userinfoEndpoint.toString())
//        mExecutor.submit {
//            try {
//                val conn: HttpURLConnection =
//                    mConfiguration.connectionBuilder.openConnection(
//                        userInfoEndpoint
//                    )
//                conn.setRequestProperty("Authorization", "Bearer $accessToken")
//                conn.instanceFollowRedirects = false
//                val response = conn.inputStream.source().buffer()
//                    .readString(Charset.forName("UTF-8"))
//                mUserInfoJson.set(JSONObject(response))
//            } catch (ioEx: IOException) {
//                Log.e(TAG, getString(R.string.request_user_info_io_exception), ioEx)
//                showSnackbar(getString(R.string.request_user_info_io_exception))
//            } catch (jsonEx: JSONException) {
//                Log.e(TAG, getString(R.string.request_user_info_json_exception), jsonEx)
//                showSnackbar(getString(R.string.request_user_info_json_exception))
//            }
//            runOnUiThread { displayAuthorized() }
//        }
//    }
//
//    @MainThread
//    private fun showSnackbar(message: String) {
//        Snackbar.make(
//            findViewById(R.id.nav_view),
//            message,
//            Snackbar.LENGTH_SHORT
//        ).show()
//    }


}