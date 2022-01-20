package com.circadian.sense.ui.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.circadian.sense.MainActivity
import com.circadian.sense.R
import com.circadian.sense.databinding.ActivityLoginBinding
import com.circadian.sense.utilities.AuthStateManager
import com.circadian.sense.utilities.Configuration
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_SHORT
import com.google.android.material.snackbar.Snackbar
import net.openid.appauth.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val TAG = "LoginActivity"
    private val EXTRA_FAILED = "failed"

    private var mAuthService: AuthorizationService? = null
    private var mAuthStateManager: AuthStateManager? = null
    private var mConfiguration: Configuration? = null

    private val mClientId = AtomicReference<String>()
    private val mAuthRequest = AtomicReference<AuthorizationRequest>()
    private val mAuthIntent = AtomicReference<CustomTabsIntent>()
    private var mAuthIntentLatch = CountDownLatch(1)
    private var mExecutor: ExecutorService? = null

    private val startForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            val intent = result.data

            // Pass the data to MainActivity
            val mainIntent = Intent(this, MainActivity::class.java)
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            mainIntent.putExtras(intent!!)
            startActivity(mainIntent)

            Log.i(TAG, "Received auth code")
        }
        else {
            Log.i(TAG, "Authorization failed")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mExecutor = Executors.newSingleThreadExecutor()
        mAuthStateManager = AuthStateManager.getInstance(this)
        mConfiguration = Configuration.getInstance(this)

        if (mAuthStateManager!!.current.isAuthorized
            && !mConfiguration!!.hasConfigurationChanged()) {
            Log.i(TAG, getString(R.string.user_already_authenticated))
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
//                    .putExtra("letter", true)
            )
            finish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request auth code on login click - we only get here if not already authorized
        binding.loginButton.setOnClickListener {
            startAuth()
            displayLoading("Initializing")
        }

        if (!mConfiguration!!.isValid){
            displayError(mConfiguration!!.getConfigurationError()!!)
            return
        }

        if (mConfiguration!!.hasConfigurationChanged()) {
            // discard any existing authorization state due to the change of configuration
            Log.i(TAG, getString(R.string.configuration_has_changed))
            mAuthStateManager!!.replace(AuthState())
            mConfiguration!!.acceptConfiguration()
        }

        if (intent.getBooleanExtra(EXTRA_FAILED, false)) {
            displayAuthCancelled()
        }

        displayLoading("Initializing")
        mExecutor!!.submit(Runnable { initializeAppAuth() })
    }

    override fun onStart() {
        super.onStart()
        if (mExecutor!!.isShutdown) {
            mExecutor = Executors.newSingleThreadExecutor()
        }
    }

    override fun onStop() {
        super.onStop()
        mExecutor!!.shutdownNow()
    }

    override fun onDestroy() {
        super.onDestroy()
        mAuthService?.dispose()
    }

    @MainThread
    fun startAuth() {
        displayLoading("Making authorization request")

        // WrongThread inference is incorrect for lambdas
        // noinspection WrongThread
        mExecutor!!.submit { doAuth() }
    }

    /**
     * Performs the authorization request, using the browser selected in the spinner,
     * and a user-provided `login_hint` if available.
     */
    @WorkerThread
    private fun doAuth() {
        try {
            mAuthIntentLatch.await()
        } catch (ex: InterruptedException) {
            Log.w(TAG, "Interrupted while waiting for auth intent")
        }

        val intent = mAuthService!!.getAuthorizationRequestIntent(
            mAuthRequest.get(),
            mAuthIntent.get()
        )
        startForResult.launch(intent)

    }

    /**
     * Initializes the authorization service configuration if necessary, either from the local
     * static values or by retrieving an OpenID discovery document.
     */
    @WorkerThread
    private fun initializeAppAuth() {
        Log.i(TAG, "Initializing AppAuth")
        recreateAuthorizationService()
        if (mAuthStateManager!!.current.authorizationServiceConfiguration != null) {
            // configuration is already created, skip to client initialization
            Log.i(TAG, "auth config already established")
            initializeClient()
            return
        }

        // if we are not using discovery, build the authorization service configuration directly
        // from the static configuration values.
        if (mConfiguration!!.getDiscoveryUri() == null) {
            Log.i(TAG, "Creating auth config from res/raw/auth_config.json")
            val config = AuthorizationServiceConfiguration(
                mConfiguration!!.getAuthEndpointUri()!!,
                mConfiguration!!.getTokenEndpointUri()!!,
                mConfiguration!!.getRegistrationEndpointUri(),
                mConfiguration!!.getEndSessionEndpoint()
            )
            mAuthStateManager!!.replace(AuthState(config))
            initializeClient()
            return
        }

//        // TODO: We don't use an OpenID discovery doc, so this might be throwaway
//        // WrongThread inference is incorrect for lambdas
//        // noinspection WrongThread
//        runOnUiThread { displayLoading("Retrieving discovery document") }
//        Log.i(TAG, "Retrieving OpenID discovery doc")
//        AuthorizationServiceConfiguration.fetchFromUrl(
//            mConfiguration!!.getDiscoveryUri()!!,
//            { config: AuthorizationServiceConfiguration?, ex: AuthorizationException? ->
//                this.handleConfigurationRetrievalResult(
//                    config,
//                    ex
//                )
//            },
//            mConfiguration!!.connectionBuilder
//        )
    }

    @MainThread
    private fun handleConfigurationRetrievalResult(
        config: AuthorizationServiceConfiguration?,
        ex: AuthorizationException
    ) {
        if (config == null) {
            Log.i(TAG, "Failed to retrieve discovery document", ex)
            displayError("Failed to retrieve discovery document: " + ex.message)
            return
        }
        Log.i(TAG, "Discovery document retrieved")
        mAuthStateManager!!.replace(AuthState(config))
        mExecutor!!.submit { initializeClient() }
    }

    /**
     * Initiates a dynamic registration request if a client ID is not provided by the static
     * configuration.
     */
    @WorkerThread
    private fun initializeClient() {
        if (mConfiguration!!.getClientId() != null) {
            Log.i(TAG, "Using static client ID: " + mConfiguration!!.getClientId())
            // use a statically configured client ID
            mClientId.set(mConfiguration!!.getClientId())
            runOnUiThread { this.initializeAuthRequest() }
            return
        }

        val lastResponse: RegistrationResponse? =
            mAuthStateManager?.current?.lastRegistrationResponse
        if (lastResponse != null) {
            Log.i(TAG, "Using dynamic client ID: " + lastResponse.clientId)
            // already dynamically registered a client ID
            mClientId.set(lastResponse.clientId)
            runOnUiThread { this.initializeAuthRequest() }
            return
        }

        // WrongThread inference is incorrect for lambdas
        // noinspection WrongThread
        runOnUiThread { displayLoading("Dynamically registering client") }
        Log.i(TAG, "Dynamically registering client")
        val registrationRequest = RegistrationRequest.Builder(
            mAuthStateManager!!.current.authorizationServiceConfiguration!!,
            listOf(mConfiguration!!.getRedirectUri())
        ).setTokenEndpointAuthenticationMethod(ClientSecretBasic.NAME)
            .build()
        mAuthService!!.performRegistrationRequest(
            registrationRequest
        ) { response: RegistrationResponse?, ex: AuthorizationException? ->
            this.handleRegistrationResponse(
                response,
                ex
            )
        }
    }

    @MainThread
    private fun handleRegistrationResponse(
        response: RegistrationResponse?,
        ex: AuthorizationException?
    ) {
        mAuthStateManager!!.updateAfterRegistration(response, ex)
        if (response == null) {
            Log.i(TAG, "Failed to dynamically register client", ex)
            displayError("Failed to register client: " + ex!!.message)
            return
        }
        Log.i(TAG, "Dynamically registered client: " + response.clientId)
        mClientId.set(response.clientId)
        initializeAuthRequest()
    }

    private fun recreateAuthorizationService() {
        if (mAuthService != null) {
            Log.i(TAG, "Discarding existing AuthService instance")
            mAuthService!!.dispose()
        }
        mAuthService = createAuthorizationService()
        mAuthRequest.set(null)
        mAuthIntent.set(null)
    }

    private fun createAuthorizationService(): AuthorizationService {
        Log.i(TAG, "Creating authorization service")
        val builder = AppAuthConfiguration.Builder()
        builder.setConnectionBuilder(mConfiguration!!.connectionBuilder)
        return AuthorizationService(this, builder.build())
    }

    @MainThread
    private fun initializeAuthRequest() {
        createAuthRequest()
        warmUpBrowser()
    }

    private fun warmUpBrowser() {
        mAuthIntentLatch = CountDownLatch(1)
        mExecutor!!.execute {
            Log.i(TAG, "Warming up browser instance for auth request")
            val intentBuilder =
                mAuthService!!.createCustomTabsIntentBuilder(mAuthRequest.get().toUri())
//            intentBuilder.setColorScheme(R.color.COLOR_SCHEME_LIGHT)
            mAuthIntent.set(intentBuilder.build())
            mAuthIntentLatch.countDown()
        }
    }

    private fun createAuthRequest() {
        val authRequestBuilder = AuthorizationRequest.Builder(
            mAuthStateManager!!.current.authorizationServiceConfiguration!!,
            mClientId.get(),
            ResponseTypeValues.CODE,
            mConfiguration!!.getRedirectUri()
        ).setScope(mConfiguration!!.getScope())
        mAuthRequest.set(authRequestBuilder.build())
    }


    @MainThread
    private fun displayError(error: String) {
        Snackbar.make(
            findViewById(R.id.login_button),
            error,
            LENGTH_SHORT)
        .show()
    }

    @MainThread
    private fun displayLoading(loadingMessage: String) {
//        findViewById<View>(R.id.loading_container).visibility = View.VISIBLE
////        findViewById<View>(R.id.auth_container).visibility = View.GONE
////        findViewById<View>(R.id.error_container).visibility = View.GONE
//        (findViewById<View>(R.id.loading_description) as TextView).text =
//            loadingMessage
    }

    private fun displayAuthCancelled() {
        Snackbar.make(
            findViewById(R.id.login_button),
            R.string.auth_failed_snack,
            LENGTH_SHORT)
        .show()
    }

}