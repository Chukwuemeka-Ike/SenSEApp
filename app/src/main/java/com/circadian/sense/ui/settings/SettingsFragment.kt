package com.circadian.sense.ui.settings

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsIntent.COLOR_SCHEME_LIGHT
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.circadian.sense.*
import com.circadian.sense.R
import com.circadian.sense.utilities.AuthStateManager
import com.circadian.sense.utilities.Configuration
import com.circadian.sense.utilities.DailyOptimizationWorker
import com.circadian.sense.utilities.DataManager
import net.openid.appauth.*
import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class SettingsFragment : PreferenceFragmentCompat() {
    private val TAG = "SettingsFragment"

    private lateinit var mAuthStateManager: AuthStateManager
    private lateinit var mAuthService: AuthorizationService
    private lateinit var mConfiguration: Configuration
    private lateinit var mExecutor: ExecutorService

    private val mClientId = AtomicReference<String>()
    private val mAuthRequest = AtomicReference<AuthorizationRequest>()
    private val mAuthIntent = AtomicReference<CustomTabsIntent>()
    private var mAuthIntentLatch = CountDownLatch(1)

    private var loginPreference: Preference? = null
    private var logoutPreference: Preference? = null

    /**
     *   Registers for an activity result when we launch the CustomTabsIntent
     *   for user to sign in, then continues the authorization based on result
     */
    private val startCustomTabsForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->

        // If successful, continue authorization with the returned data
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val authorizationExchangeResponse = result.data
            val mainIntent = Intent(
                requireContext().applicationContext, MainActivity::class.java
            )
                .putExtras(authorizationExchangeResponse!!)
            continueAuth(mainIntent)
            loginPreference?.isEnabled = false
            logoutPreference?.isEnabled = true

            Log.i(TAG, "Received auth code from server. Continuing authentication.")
        } else {
            Toast.makeText(
                requireContext(),
                "Authorization failed. Please try again",
                Toast.LENGTH_SHORT
            )
            Log.i(TAG, "Authorization failed. Please retry")
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // AuthStateManager, Executor, Configuration, AuthService
        mAuthStateManager = AuthStateManager.getInstance(requireContext().applicationContext)
        mExecutor = Executors.newSingleThreadExecutor()
        mConfiguration = Configuration.getInstance(requireContext().applicationContext)
        mAuthService = AuthorizationService(
            requireContext().applicationContext,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(mConfiguration.connectionBuilder)
                .build()
        )

        // Login and Logout preferences
        loginPreference = findPreference(getString(R.string.login_pref_tag))
        logoutPreference = findPreference(getString(R.string.logout_pref_tag))

        // If we're authorized, disable loginPreference and enable logoutPreference - and vice versa
        with(mAuthStateManager.current.isAuthorized) {
            loginPreference?.isEnabled = !this
            logoutPreference?.isEnabled = this
        }

        // Starts authorization workflow when login is clicked
        loginPreference?.setOnPreferenceClickListener {
            startAuth()
            true
        }

        // Logs the user out when clicked
        logoutPreference?.setOnPreferenceClickListener {
            createLogOutDialog()
            true
        }

        val feedBack: Preference? = findPreference(getString(R.string.feedback_pref_tag))
        feedBack?.isEnabled = false

        // Start warming up the browser and auth process if not authorized
        if (!mAuthStateManager.current.isAuthorized) {
            mExecutor.submit(Runnable { this.initializeAppAuth() })
        }

        // If invalid configuration, scream
        if (!mConfiguration.isValid) {
            displayError(mConfiguration.getConfigurationError()!!)
        }

        // Discard any existing authorization state due to the change of configuration
        // TODO: SECURITY CHECK. Someone shouldn't be able to just
        //  add something in and we accept it
        if (mConfiguration.hasConfigurationChanged()) {
            mAuthStateManager.replace(AuthState())
            mConfiguration.acceptConfiguration()
            Log.i(TAG, getString(R.string.configuration_has_changed))
            displayNotAuthorized(getString(R.string.configuration_has_changed))
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mExecutor.shutdownNow()
        mAuthService.dispose()
    }

    /**
     * Displays a loading container then starts auth process on a separate thread
     */
    @MainThread
    fun startAuth() {
        displayLoading(getString(R.string.start_auth))
        mExecutor.submit { doAuth() }
    }

    /**
     * Performs the authorization request using the default browser
     */
    @WorkerThread
    private fun doAuth() {
        try {
            mAuthIntentLatch.await()
        } catch (ex: InterruptedException) {
            Log.w(TAG, "Interrupted while waiting for auth intent")
        }

        // Start the CustomTabs and register for a result
        Log.i(TAG, "Launching CustomTabs")
        val intent = mAuthService.getAuthorizationRequestIntent(
            mAuthRequest.get(),
            mAuthIntent.get()
        )
        startCustomTabsForResult.launch(intent)
    }

    /**
     * Initializes the authorization service configuration if necessary, from the local
     * static values. We don't use OpenID as of now
     */
    @WorkerThread
    private fun initializeAppAuth() {
        Log.i(TAG, "Initializing AppAuth")
        recreateAuthorizationService()

        if (mAuthStateManager.current.authorizationServiceConfiguration != null) {
            // configuration is already created, skip to client initialization
            Log.i(TAG, "auth config already established")
            initializeClient()
            return
        }

        // Build the authorization service configuration directly
        // from the static configuration values.
        if (mConfiguration.getDiscoveryUri() == null) {
            Log.i(TAG, "Creating auth config from res/raw/auth_config.json")
            val config = AuthorizationServiceConfiguration(
                mConfiguration.getAuthEndpointUri()!!,
                mConfiguration.getTokenEndpointUri()!!,
                mConfiguration.getRegistrationEndpointUri(),
                mConfiguration.getEndSessionEndpoint()
            )
            mAuthStateManager.replace(AuthState(config))
            initializeClient()
            return
        }
    }

    /**
     * Sets the static client ID to
     * configuration.
     */
    @WorkerThread
    private fun initializeClient() {
        if (mConfiguration.getClientId() != null) {
            Log.i(TAG, "Using static client ID: " + mConfiguration.getClientId())
            // use a statically configured client ID
            mClientId.set(mConfiguration.getClientId())
            requireActivity().runOnUiThread { this.initializeAuthRequest() }
            return
        }
    }

    private fun recreateAuthorizationService() {
        if (mAuthService != null) {
            Log.i(TAG, "Discarding existing AuthService instance")
            mAuthService.dispose()
        }
        mAuthService = createAuthorizationService()
        mAuthRequest.set(null)
        mAuthIntent.set(null)
    }

    private fun createAuthorizationService(): AuthorizationService {
        Log.i(TAG, "Creating authorization service")
        val builder = AppAuthConfiguration.Builder()
        builder.setConnectionBuilder(mConfiguration.connectionBuilder)
        return AuthorizationService(requireContext().applicationContext, builder.build())
    }

    @MainThread
    private fun initializeAuthRequest() {
        createAuthRequest()
        warmUpBrowser()
    }

    private fun createAuthRequest() {
        val authRequestBuilder = AuthorizationRequest.Builder(
            mAuthStateManager.current.authorizationServiceConfiguration!!,
            mClientId.get(),
            ResponseTypeValues.CODE,
            mConfiguration.getRedirectUri()
        ).setScope(mConfiguration.getScope())
        mAuthRequest.set(authRequestBuilder.build())
    }

    private fun warmUpBrowser() {
        mAuthIntentLatch = CountDownLatch(1)
        mExecutor.execute {
            Log.i(TAG, "Warming up browser instance for auth request")
            val intentBuilder =
                mAuthService.createCustomTabsIntentBuilder(mAuthRequest.get().toUri())
            intentBuilder.setColorScheme(COLOR_SCHEME_LIGHT)
            intentBuilder.setShowTitle(true)
            mAuthIntent.set(intentBuilder.build())
            mAuthIntentLatch.countDown()
        }
    }

    /**
     * Continues the authorization workflow with the intent received from
     * user login
     */
    private fun continueAuth(intent: Intent) {
        // Get the response and exception from the intent
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        // Update the authorization state with the login result
        if (response != null || ex != null) {
            mAuthStateManager.updateAfterAuthorization(response, ex)
        }

        // Exchange authorization code for tokens if it's available
        if (response?.authorizationCode != null) {
//            mAuthStateManager.updateAfterAuthorization(response, ex)
            exchangeAuthorizationCode(response)
        } else if (ex != null) {
            displayNotAuthorized("Authorization flow failed: " + ex.message)
        } else {
            displayNotAuthorized(getString(R.string.no_auth_state_retained_exception))
        }
    }

    /**
     *
     */
    @MainThread
    private fun refreshAccessToken() {
        displayLoading("Refreshing access token")
        performTokenRequest(
            mAuthStateManager.current.createTokenRefreshRequest()
        ) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
            handleAccessTokenResponse(
                tokenResponse,
                authException
            )
        }
    }

    /**
     *
     */
    @MainThread
    private fun exchangeAuthorizationCode(authorizationResponse: AuthorizationResponse) {
        displayLoading("Exchanging authorization code")
        performTokenRequest(
            authorizationResponse.createTokenExchangeRequest()
        ) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
            handleCodeExchangeResponse(
                tokenResponse,
                authException
            )
        }
    }

    /**
     *
     */
    @MainThread
    private fun performTokenRequest(
        request: TokenRequest,
        callback: AuthorizationService.TokenResponseCallback
    ) {
        val clientAuthentication: ClientAuthentication
        clientAuthentication = try {
            mAuthStateManager.current.clientAuthentication
        } catch (ex: ClientAuthentication.UnsupportedAuthenticationMethod) {
            Log.d(
                TAG,
                "Token request cannot be made, client authentication for the token "
                        + "endpoint could not be constructed (%s)",
                ex
            )
            displayNotAuthorized("Client authentication method is unsupported")
            return
        }
        mAuthService.performTokenRequest(
            request,
            clientAuthentication,
            callback
        )
    }

    /**
     *
     */
    @WorkerThread
    private fun handleAccessTokenResponse(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) {
        mAuthStateManager.updateAfterTokenResponse(tokenResponse, authException)
        requireActivity().runOnUiThread { displayAuthorized() }
    }

    /**
     *
     */
    @WorkerThread
    private fun handleCodeExchangeResponse(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) {
        mAuthStateManager.updateAfterTokenResponse(tokenResponse, authException)
        if (!mAuthStateManager.current.isAuthorized) {
            val message = ("Authorization Code exchange failed"
                    + if (authException != null) authException.error else "")

            // WrongThread inference is incorrect for lambdas
            requireActivity().runOnUiThread { displayNotAuthorized(message) }
        } else {
            requireActivity().runOnUiThread { displayAuthorized() }

            // Once we're authorized, run the first optimization
            initiatePeriodicOptimizations()
        }
    }


    /**
     * Creates the first WorkManager Optimization WorkRequest to optimize the
     * filter on the user's data.
     */
    private fun initiatePeriodicOptimizations() {
        val dailyOptimizationWorkRequest =
            OneTimeWorkRequestBuilder<DailyOptimizationWorker>()
                .setConstraints(WORK_MANAGER_CONSTRAINTS)
                .addTag(DAILY_OPTIMIZATION_WORKER_TAG)
                .build()
        WorkManager.getInstance(requireContext().applicationContext)
            .enqueueUniqueWork(
                DAILY_OPTIMIZATION_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                dailyOptimizationWorkRequest
            )
    }


    /**
     * Creates the logout dialog and logs user out if they choose yes
     */
    @MainThread
    private fun createLogOutDialog() {

        // Instantiate the logout dialog
        val logoutDialog: AlertDialog = requireActivity().let {
            // Create the builder, then set its properties
            val builder = AlertDialog.Builder(it)
                .setTitle(getString(R.string.logout_button_label))
                .setMessage(getString(R.string.logout_dialog_message))
                .setPositiveButton( // User clicked yes button
                    getString(R.string.logout_dialog_positive),
                    DialogInterface.OnClickListener { _, _ ->
                        logOut()
                        Log.i(TAG, getString(R.string.initiated_logout_dialog))
                    })
                .setNegativeButton( // User cancelled the dialog
                    getString(R.string.logout_dialog_negative),
                    DialogInterface.OnClickListener { _, _ ->
                        Log.i(TAG, getString(R.string.cancelled_logout_dialog))
                    })

            // Create the AlertDialog
            builder.create()
        }

        // Show the dialog
        logoutDialog.show()
    }

    /**
     * Logs out the user by revoking app access to their data and
     */
    @MainThread
    private fun logOut() {
        // revoke the access token with Fitbit first before clearing authState locally
        // Request to revoke
        //        POST https://api.fitbit.com/oauth2/revoke
        //        Authorization: Bearer <access_token>
        //        Content-Type: application/x-www-form-urlencoded
        //        token=<access_token or refresh_token to be revoked>

        // Offload all this to a separate thread
        mExecutor.submit {
            val revokeTokenEndpoint = Uri.parse(
                mConfiguration.getRevokeTokenEndpointUri().toString()
            )

            // Refresh access token first to ensure no issues with revoking
            // TODO: Make this synchronous
            if (mAuthStateManager.current.needsTokenRefresh) {
                refreshAccessToken()
            }

            // Clear user data, remove any existing WorkRequests, and clear MainActivity's ViewModelStore
            DataManager(requireContext().applicationContext).clearData()
            WorkManager.getInstance(requireContext().applicationContext).cancelAllWork()
            requireActivity().runOnUiThread { requireActivity().viewModelStore.clear() }

            // Send revoke request to Fitbit servers
            try {
                val conn: HttpURLConnection =
                    mConfiguration.connectionBuilder.openConnection(revokeTokenEndpoint)
                conn.requestMethod = "POST"
                conn.setRequestProperty(
                    "Authorization",
                    "Bearer ${mAuthStateManager.current.accessToken}"
                )
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.setRequestProperty("Accept", "application/json")
                conn.instanceFollowRedirects = false
                conn.doOutput = true

                val outputString = "token=${mAuthStateManager.current.accessToken}"
                conn.outputStream.write(outputString.toByteArray())
                conn.outputStream.close()

                val responseCode = conn.responseCode
                Log.i(TAG, "Response code: ${responseCode}")
                Log.i(TAG, "Response message: ${conn.responseMessage}")

                // If we get a successful or unauthorized response, clear the auth state
                // We're either no longer authorized, or something is wrong
                if (responseCode == HttpURLConnection.HTTP_OK ||
                    responseCode == HttpURLConnection.HTTP_UNAUTHORIZED
                ) {
                    clearAuthState()

                    this.initializeAppAuth()

                    // Set the login, logout buttons appropriately
                    requireActivity().runOnUiThread {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.logout_successful),
                            Toast.LENGTH_SHORT
                        ).show()

                        loginPreference?.isEnabled = true
                        logoutPreference?.isEnabled = false
                    }
                } else {
                    Log.w(TAG, "Problem with revoke attempt: ${responseCode}")
                }
                conn.disconnect()

            } catch (ioEx: IOException) {
                Log.e(TAG, getString(R.string.access_revoke_io_error), ioEx)
                return@submit
            } catch (jsonEx: JSONException) {
                Log.e(TAG, getString(R.string.access_revoke_json_error), jsonEx)
                return@submit
            }
        }
    }

    /**
     * Discard the authorization state, but retain the configuration
     * to save from retrieving it again
     */
    private fun clearAuthState() {
        val currentState: AuthState = mAuthStateManager.current
        val clearedState = AuthState(currentState.authorizationServiceConfiguration!!)
        if (currentState.lastRegistrationResponse != null) {
            clearedState.update(currentState.lastRegistrationResponse)
        }
        mAuthStateManager.replace(clearedState)
    }


    /**
     * Display functions
     */
    @MainThread
    private fun displayError(error: String) {
        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
    }

    @MainThread
    private fun displayLoading(loadingMessage: String) {
        Log.i(TAG, "Loading: $loadingMessage")
    }

    @MainThread
    private fun displayNotAuthorized(explanation: String) {
        // TODO: Implement something useful
        Toast.makeText(requireContext(), explanation, Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Not authorized: ${explanation}")
    }

    @MainThread
    private fun displayAuthorized() {
        // TODO: Implement something useful
        Toast.makeText(requireContext(), "Authorization successful", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Authorization successful")
    }


}