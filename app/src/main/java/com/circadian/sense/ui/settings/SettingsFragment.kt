package com.circadian.sense.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.circadian.sense.R
import com.circadian.sense.utilities.AuthStateManager
import com.circadian.sense.utilities.Configuration
import net.openid.appauth.AuthState
import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SettingsFragment : PreferenceFragmentCompat() {
    private val TAG = "SettingsFragment"

    private lateinit var mAuthStateManager: AuthStateManager
    private lateinit var mConfiguration: Configuration
    private lateinit var mExecutor: ExecutorService

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Login and Logout preferences
        val loginPreference: Preference? = findPreference(getString(R.string.login_pref_tag))
        val logoutPreference: Preference? = findPreference(getString(R.string.logout_pref_tag))

        // If authorized, disable loginPreference and enable logoutPreference - and vice versa
        mAuthStateManager = AuthStateManager.getInstance(requireContext().applicationContext)
        mExecutor = Executors.newSingleThreadExecutor()
        mConfiguration = Configuration.getInstance(requireContext().applicationContext)

        with(mAuthStateManager.current.isAuthorized){
            loginPreference?.isEnabled = !this
            logoutPreference?.isEnabled = this
        }

        // Clicking loginPreference directs to LoginActivity to handle authorization workflow
        loginPreference?.setOnPreferenceClickListener{
            val intent = Intent(context, LoginActivity::class.java)
            startActivity(intent)
            true
        }

        // Logs the user out when clicked
        logoutPreference?.setOnPreferenceClickListener{
            signOut()
            true
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    @MainThread
    private fun signOut() {
        // revoke the refresh token with Fitbit first before clearing authState locally
        // Request to revoke
        //        POST https://api.fitbit.com/oauth2/revoke
        //        Authorization: Bearer Y2xpZW50X2lkOmNsaWVudCBzZWNyZXQ=
        //        Content-Type: application/x-www-form-urlencoded
        //        token=<access_token or refresh_token to be revoked>

        val RevokeTokenEndpoint = Uri.parse(mConfiguration.getRevokeTokenEndpointUri().toString())

        mExecutor.submit {
            try {
                val conn: HttpURLConnection =
                    mConfiguration.connectionBuilder.openConnection( RevokeTokenEndpoint )
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer ${mAuthStateManager.current.accessToken}")
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.setRequestProperty("Accept", "application/json")
                conn.instanceFollowRedirects = false
                conn.doOutput = true

                val outputString = "token=${mAuthStateManager.current.accessToken}"
                conn.outputStream.write(outputString.toByteArray())

                Log.i(TAG, "Response code: ${conn.responseCode}")
                Log.i(TAG, "Response message: ${conn.responseMessage}")

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    clearAuthState()
                }

            } catch (ioEx: IOException) {
                Log.e(TAG, getString(R.string.access_revoke_io_error), ioEx)
                return@submit
            } catch (jsonEx: JSONException) {
                Log.e(TAG, getString(R.string.access_revoke_json_error), jsonEx)
                return@submit
            }

        }
        findPreference<Preference?>(getString(R.string.login_pref_tag))?.isEnabled = true
        findPreference<Preference?>(getString(R.string.logout_pref_tag))?.isEnabled = false
    }

    private fun clearAuthState() {
        // discard the authorization and token state, but retain the configuration
        // to save from retrieving them again.
        val currentState: AuthState = mAuthStateManager.current
        val clearedState = AuthState(currentState.authorizationServiceConfiguration!!)
        if (currentState.lastRegistrationResponse != null) {
            clearedState.update(currentState.lastRegistrationResponse)
        }
        mAuthStateManager.replace(clearedState)
    }

}
