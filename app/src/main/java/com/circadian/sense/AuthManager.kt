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
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import java.util.*

class AuthManager (private val context: Context) {

    private val defAuthState = null
    val tag = "SenSE Debug"
    private var _authState: AuthState
    val authState: AuthState get() = _authState
    private lateinit var _dataRequestResponse: String
    val dataRequestResponse: String get() = _dataRequestResponse
    private var serviceConfig: AuthorizationServiceConfiguration

    // TODO: Take auth service out of all the functions and make one

    init {
        _authState = readState(context)
        serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse(context.getString(R.string.authorization_endpoint)),  // authorization endpoint
            Uri.parse(context.getString(R.string.token_endpoint))           // token endpoint
        )
    }

    /**
     * Requests new user data
     * @return []
     */
    fun requestUserData(): String {
        // GET request, requestDataUrl, header["Authorization"] = "Bearer "+access_token

        // If not authorized, start auth workflow first
        var response = ""
        if(!_authState.isAuthorized){
            authorize()
        }

//        val user_id = "9FFF7Z"
        val user_id = parseUserID()
        val authService = AuthorizationService(context)
        val dataRequestUrl: String = "https://api.fitbit.com/1/user/${user_id}/activities/heart/date/2021-12-01/2021-12-01/1min/time/00:00/01:30.json"

        authService.createCustomTabsIntentBuilder()

        val mRequestQueue = Volley.newRequestQueue(context)

        _authState.performActionWithFreshTokens(
            authService,
            AuthState.AuthStateAction { accessToken, idToken, ex ->
                if (ex != null) {
                    // negotiation for fresh tokens failed, check ex for more details
                    Log.d(tag, "Exception: ${ex}")
                    return@AuthStateAction
                }
                Log.i(tag, "Made it into request with fresh tokens")
                val authorizationHeader = "Bearer ${accessToken}"
                val mStringRequest = buildDataRequest(dataRequestUrl, authorizationHeader)
                mRequestQueue.add(mStringRequest)
                Log.i(tag, "Sent the request successfully.")
                writeState(_authState)
            }
        )


        return response
    }

    // TODO: Streamline this and abstract what needs abstraction
    /**
     * Builds the data request
     * @param - [dataRequestUrl] to for requesting specific data
     * @param - [authorizationHeader] to include in request
     * @return - [mStringRequest] - built Volley StringRequest that can be added to a request queue
     */
    private fun buildDataRequest(dataRequestUrl: String, authorizationHeader: String) : StringRequest {
        // Initialize StringRequest
        val mStringRequest : StringRequest = object : StringRequest(
            Method.GET,
            dataRequestUrl,
            Response.Listener { response ->
                val strResp = response.toString()
                Toast.makeText(context.applicationContext, "Data Received", Toast.LENGTH_SHORT).show()
                Log.i("Data Request Response: ", strResp)
                saveDataRequestResponse(strResp)
//                try {
//                    val fileName = "/mnt/sdcard/Download/response.json"
//                    val myfile = File(fileName)
//                    myfile.writeText(strResp)
//                    Toast.makeText(context, "Response saved", Toast.LENGTH_LONG)
//                        .show()
//                    Log.i("Response ", "saved")
//                } catch (e: java.lang.Exception) {
//                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
//                    Log.i("Error", e.toString())
//                    e.printStackTrace()
//                }
            },
            Response.ErrorListener { error ->
                Log.i("This is the error", "Error :" + error.toString())
                Toast.makeText(
                    context,
                    "Error " + error.toString(),
                    Toast.LENGTH_SHORT
                ).show()
                fun onErrorResponse(error: VolleyError) {
                    val body: String
                    //get status code here
                    val statusCode = error.networkResponse.statusCode.toString()
                    //get response body and parse with appropriate encoding
                    if (error.networkResponse.data != null) {
                        try {
                            body = String(error.networkResponse.data, Charset.defaultCharset())
                            Log.i("Response body ", body)
                        } catch (e: UnsupportedEncodingException) {
                            e.printStackTrace()
                        }
                    }
                }
                onErrorResponse(error)
            }
        ) {
            @Throws(AuthFailureError::class)
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Authorization"] = authorizationHeader
                return headers
            }

        }

        return mStringRequest
    }

    private fun saveDataRequestResponse(strResp: String) {
        _dataRequestResponse = strResp
    }

    private fun authorize() {
        TODO("Not yet implemented")
        return
    }

    private fun isAuthorized(): Boolean {
        return _authState.isAuthorized
    }

    // TODO: Complete this, make better
    private fun parseUserID() : String {
        // Get user id from token response
        try {
            val tokenRequestResult = _authState.jsonSerialize().getJSONObject("mLastTokenResponse")
            val dataSet = tokenRequestResult.getJSONObject("additionalParameters")
            return dataSet.getString("user_id")
        }
        catch (e: Exception){
            Log.w(tag, "User ID unavailable: ${e}")
            return ""
        }
    }

    /**
     * Continues the authorization work flow once the login activity is brought back into focus
     * Searches for an authorization response or exception and reacts accordingly
     */
    fun continueAuthWorkflow(binding: ViewBinding, intent: Intent) {
        val resp = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        // Only run this block if one of the responses is non-null
        if (resp != null || ex != null) {
            if (resp != null) {
                Snackbar.make(
                    binding.root, context.getString(R.string.auth_success_snack),
                    BaseTransientBottomBar.LENGTH_SHORT
                ).show()
            } else {
                Snackbar.make(
                    binding.root, context.getString(R.string.auth_failed_snack),
                    BaseTransientBottomBar.LENGTH_SHORT
                ).show()
            }
            _authState.update(resp, ex)

            Log.i(tag, "Response: ${resp}\nException: ${ex}")
            Log.i(tag, "AuthState after Response: ${_authState}")
            if (resp != null) {
                Log.i(tag, "Exchanging code")
                exchangeAuthCodeForTokens(resp)
            } else {
                Log.i(tag, "Blimey")
            }
        }
    }

    /**
     * Request the authorization code using the appauth service and request builder
     */
    fun requestAuthorizationCode() {

        val authRequestBuilder = AuthorizationRequest.Builder(
            serviceConfig,                                      // the authorization service configuration
            context.getString(R.string.client_id),              // the client ID
            ResponseTypeValues.CODE,                            // the response_type value: we want a code
            context.getString(R.string.redirect_uri)
                .toUri()    // the redirect URI to which the auth response is sent
        )

        val authRequest = authRequestBuilder
            .setScope(context.getString(R.string.scope))
            .build()

        val authService = AuthorizationService(context)
        authService.performAuthorizationRequest(
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
     * Exchange the authorization code for tokens which we can use to access data
     * @param [resp] - authorization response received from authorization code request
     */
    private fun exchangeAuthCodeForTokens(resp: AuthorizationResponse) {
        val authService = AuthorizationService(context)

        // Request tokens
        authService.performTokenRequest(
            resp.createTokenExchangeRequest(),
            AuthorizationService.TokenResponseCallback { tokenResponse, tokenException ->
                // tokenResponse contains the server's JSON response with tokens if successful
                // Update authState
                _authState.update(tokenResponse, tokenException)
                writeState(_authState)

                // Need to get user id, refresh token and access token
                //
                _authState.performActionWithFreshTokens(
                    authService,
                    AuthState.AuthStateAction { accessToken, idToken, ex ->
                        if (ex != null) {
                            // negotiation for fresh tokens failed, check ex for more details
                            Log.d(tag, "Exception: ${ex}")
                            return@AuthStateAction
                        }
                    }
                )
            }
        )
    }

    /**
     * Saves the authentication state to auth state pref
     *  @param [context] - application context to use
     */
    private fun writeState(authState: AuthState) {
        try {
            val sharedPrefs: SharedPreferences = context.getSharedPreferences(
                context.getString(R.string.auth_state_pref_file), Context.MODE_PRIVATE
            )
            with(sharedPrefs.edit()) {
                putString(
                    context.getString(R.string.auth_state_pref_key),
                    _authState.jsonSerializeString()
                ).commit()
            }
            Log.i(tag, "Wrote shared prefs successfully")

        } catch (e: Exception) {
            throw IllegalStateException("Failed to write state to shared prefs")
        }
    }

    /**
     * Loads the auth state from shared preferences file if available
     * Otherwise, it creates a new AuthState
     *  @param [thisContext] - application context
     *  @return [state] - saved or new AuthState object
     */
    private fun readState(thisContext: Context): AuthState {
        Log.i(tag, "Reading auth state from preferences")

        // Get auth state from shared preferences if it exists, otherwise create a new one
        val sharedPrefs: SharedPreferences = thisContext.getSharedPreferences(
            thisContext.getString(R.string.auth_state_pref_file), Context.MODE_PRIVATE
        )
        val authStateString: String? = sharedPrefs.getString(
            thisContext.getString(R.string.auth_state_pref_key), defAuthState
        )

        val state: AuthState = if (authStateString != defAuthState) {
            AuthState.jsonDeserialize(authStateString)
        } else {
            AuthState(serviceConfig)
        }
        return state
    }
}