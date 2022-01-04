package com.circadian.sense

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import androidx.viewbinding.ViewBinding
import com.android.volley.AuthFailureError
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.circadian.sense.ui.settings.LoginActivity
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import net.openid.appauth.*
import java.io.File
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import java.util.*

/**
 * AuthManager class
 * Makes it easy to persist the application's authorization state and
 * handles communication with Fitbit's server with a single object
 */
class AuthManager(private val context: Context) {

    // mAuthState and backing property for external access to the current state.
    // TODO: Do we need external access to the AuthState once everything is set up?
    private var mAuthState: AuthState
    val authState: AuthState get() = mAuthState

    // mLastDataRequestResponse and backing property
    // TODO: No need for external access once this class is fully implemented
    private lateinit var mLastDataRequestResponse: String
    val dataRequestResponse: String get() = mLastDataRequestResponse

    private var mAuthService: AuthorizationService
    private var mSharedPrefs: SharedPreferences
    private var serviceConfig: AuthorizationServiceConfiguration
    private var mUtils: Utils

    init {
        mSharedPrefs = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
        mAuthService = AuthorizationService(context)
        mAuthState = readState()
        mUtils = Utils(context)

        serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse(context.getString(R.string.authorization_endpoint)),  // authorization endpoint
            Uri.parse(context.getString(R.string.token_endpoint))                // token endpoint
        )
    }

    /**
     * I envision this as something that can be called once by clicking the Login
     * button in SettingsFragment, and it handles the entire workflow
     * TODO: Implement this function (probably using callbacks), then replace LoginActivity
     */
    private fun authorize(binding: ViewBinding, intent: Intent) {
        TODO("Not yet implemented")

//        mAuthService.createCustomTabsIntentBuilder()

//        requestAuthorizationCode()
//        continueAuthWorkflow(binding, intent)
        return
    }

    /**
     * Requests the authorization code using the AppAuth service and request builder
     * Launches a CustomTabs to allow the user login to their Fitbit account
     * and give us permission
     */
    fun requestAuthorizationCode() {
        // Set up the AuthRequest Builder with required parameters
        val authRequestBuilder = AuthorizationRequest.Builder(
            serviceConfig,                                      // the authorization service configuration
            context.getString(R.string.client_id),              // the client ID
            ResponseTypeValues.CODE,                            // the response_type value: we want a code
            context.getString(R.string.redirect_uri).toUri()    // the redirect URI to which the auth response is sent
        )

        // Add the scope, then build the request
        val authRequest = authRequestBuilder
            .setScope(context.getString(R.string.scope))
            .build()

        // Perform the initial authorization request
        mAuthService.performAuthorizationRequest(
            authRequest,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, LoginActivity::class.java),
                0
            ), // Auth completed
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, LoginActivity::class.java),
                0
            )  // Auth failed
        )
    }

    /**
     * Continues the authorization work flow once the application is brought back into focus
     * Searches for an authorization response or exception and reacts accordingly
     * @param [binding] - the activity's ViewBinding - TODO: See if we can eliminate this
     * @param [intent] - the intent that triggered the activity -
     *              Allows us check if we got an AuthResponse or AuthException. If not, we can't move forward in the workflow
     *
     */
    fun continueAuthWorkflow(binding: ViewBinding, intent: Intent) {
        val resp = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        // Only run this block if one of the responses is non-null
        if (resp != null || ex != null) {
            // Update mAuthState with the response and exception
            mAuthState.update(resp, ex)

            // Exchange the authorization code for tokens if auth was successful
            // TODO: Implement a way to allow the user retry authorization if there was an issue
            if (resp != null) {
                Snackbar.make(
                    binding.root, context.getString(R.string.auth_success_snack),
                    BaseTransientBottomBar.LENGTH_SHORT
                ).show()
                Log.i(tag, "Exchanging auth code for tokens")
                exchangeAuthCodeForTokens()
            } else {
                Snackbar.make(
                    binding.root, context.getString(R.string.auth_failed_snack),
                    BaseTransientBottomBar.LENGTH_SHORT
                ).show()
                Log.i(tag, "Exception: $ex")
            }
//            Log.i(tag, "Response: ${resp}\nException: ${ex}")
//            Log.i(tag, "AuthState after Response: ${mAuthState}")
        } else {
            Log.i(tag, "No response or exception received")
        }
    }

    /**
     * Exchanges the auth code for tokens which we can use to access user data
     * Uses mAuthState.lastAuthorizationResponse to create a token exchange request
     * If this function is called, there should be a valid response
     */
    private fun exchangeAuthCodeForTokens() {
        // Perform initial token request
        mAuthService.performTokenRequest(
            mAuthState.lastAuthorizationResponse!!.createTokenExchangeRequest(),
            AuthorizationService.TokenResponseCallback { tokenResponse, tokenException ->
                // tokenResponse contains the server's JSON response with tokens if successful
                mAuthState.update(tokenResponse, tokenException)
                writeState()
            }
        )
    }

    /**
     * Requests new user data and updates auth state as necessary
     * If new user data is acquired, the data is saved in a file that
     * can be accessed by the application
     */
    fun requestUserData() {
        // TODO: Implement logic to allow user login if not authorized or no user_id
        // Check if authorized before attempting anything
        if (!mAuthState.isAuthorized) {
            Log.w(tag, "Not authorized")
            return
        }

        // Get user_id from mLastTokenResponse. Exit function if there is none
        val user_id = getUserID()
        if (user_id == "") {
            return
        }
        val dataRequestUrl: String =
            "https://api.fitbit.com/1/user/${user_id}/activities/heart/date/2021-12-01/2021-12-01/1min/time/00:00/06:30.json"

        // Create a new request queue then attempt to send the data request with fresh tokens
        val mRequestQueue = Volley.newRequestQueue(context)

        mAuthState.performActionWithFreshTokens(
            mAuthService,
            AuthState.AuthStateAction { accessToken, idToken, ex ->
                if (ex != null) {
                    // negotiation for fresh tokens failed, check ex for more details
                    Log.d(tag, "Exception: ${ex}")
                    return@AuthStateAction
                }
                Log.i(tag, "Made it into request with fresh tokens")
                val authorizationHeader = "Bearer ${accessToken}"
                val mStringRequest = buildDataRequest(dataRequestUrl, authorizationHeader)

                // Add the string request to queue which fires it off
                mRequestQueue.add(mStringRequest)
                Log.i(tag, "Sent the request successfully.")

                // Write auth state to shared preferences file
                writeState()
            }
        )
    }

    // TODO: Streamline this and abstract what needs abstraction
    /**
     * Builds a HTTP GET request with target url and authorization header
     * @param - [dataRequestUrl] for requesting specific data from Fitbit
     * @param - [authorizationHeader] to include in GET request
     * @return - [mStringRequest] - the Volley StringRequest that can be added to a request queue
     */
    private fun buildDataRequest(
        dataRequestUrl: String,
        authorizationHeader: String
    ): StringRequest {

        val mStringRequest: StringRequest = object : StringRequest(
            Method.GET,
            dataRequestUrl,
            Response.Listener { response ->
                Log.i(tag, "Data Request Response: $response")
                Toast.makeText(context, "Received User Data", Toast.LENGTH_SHORT).show()

                // Save the response to JSON file
                mUtils.writeData(response.toString(), context.getString(R.string.data_request_response_file))
//                saveDataRequestResponse(response.toString())
            },
            Response.ErrorListener { error ->
                Log.i(tag, "Data Request Error: $error")
                Toast.makeText(context, "Error $error", Toast.LENGTH_SHORT).show()

                // Function to work on the error received. Currently only used to print to console
                fun onErrorResponse(error: VolleyError) {
                    val body: String

                    // Get status code
                    val statusCode = error.networkResponse.statusCode.toString()

                    // Get response body and parse with appropriate encoding
                    if (error.networkResponse.data != null) {
                        try {
                            body = String(error.networkResponse.data, Charset.defaultCharset())
                            Log.i(tag, "Response body $body")
                        } catch (e: UnsupportedEncodingException) {
                            e.printStackTrace()
                        }
                    }
                }
                onErrorResponse(error)
            }) {
            // Modify the authorization header to authorizationHeader
            @Throws(AuthFailureError::class)
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Authorization"] = authorizationHeader
                return headers
            }

        }

        return mStringRequest
    }

    /**
     * Saves the response to a JSON file
     * @param [response] - the response to save
     */
    private fun saveDataRequestResponse(response: String) {
        // Set the object's last response to the argument
        mLastDataRequestResponse = response

        // Save the response to file
        try {
            val fileName = context.getString(R.string.data_request_response_file)
            val myfile = File(fileName)
            myfile.writeText(response)

            Toast.makeText(context, "Response saved", Toast.LENGTH_LONG).show()
            Log.i(tag, "Successfully saved data response")
        } catch (e: java.lang.Exception) {
            Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            Log.i(tag, "Error saving data response: ${e}")
            e.printStackTrace()
        }
    }

    /**
     * Gets user id from mAuthState
     * @return [user_id] from mLastTokenResponse or empty string if there's none
     */
    // TODO: Complete this, make better
    private fun getUserID(): String {
        var user_id = ""

        // Get user id from mLastTokenResponse, warn if there's any error
        try {
            val tokenRequestResult = mAuthState.jsonSerialize().getJSONObject("mLastTokenResponse")
            val dataSet = tokenRequestResult.getJSONObject("additionalParameters")
            user_id = dataSet.getString("user_id")
        } catch (e: Exception) {
            Log.w(tag, "User ID unavailable: ${e}")
        }

        return user_id
    }

    /**
     * Empties out the shared preferences file and initializes a clean mAuthState
     */
    fun logoutUser(){
        // TODO: Ask them if they're sure first
        with(mSharedPrefs.edit()){
            clear().commit()
        }
        Log.i(tag, "Cleared out sharedPrefs")
        mAuthState = readState()
    }

    /**
     * Saves mAuthState to shared preferences file for easy persistence
     */
    private fun writeState() {
        try {
            with(mSharedPrefs.edit()) {
                putString(KEY_STATE, mAuthState.jsonSerializeString()).commit()
            }
            Log.i(tag, "Saved auth state successfully")
        } catch (e: Exception) {
            throw IllegalStateException("Failed to write auth state to shared prefs")
        }
    }

    /**
     * Loads the auth state from shared preferences file if available
     * Otherwise, it creates a new AuthState
     *  @return [state] - saved or new AuthState object
     */
    private fun readState(): AuthState {
        Log.i(tag, "Reading auth state from preferences")
        val authStateString: String? = mSharedPrefs.getString(KEY_STATE, defaultAuthState)

        val state: AuthState = if (authStateString != defaultAuthState) {
            AuthState.jsonDeserialize(authStateString)
        } else {
            AuthState(serviceConfig)
        }
        return state
    }

    // Companion object to hold these values for now
    companion object {
        private val defaultAuthState = null
        private const val tag = "SenSE Debug"
        private const val STORE_NAME = "com_circadian_sense_auth_state"
        private const val KEY_STATE = "authState"
    }
}