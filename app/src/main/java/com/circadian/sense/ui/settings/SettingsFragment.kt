package com.circadian.sense.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.circadian.sense.AuthManager
import com.circadian.sense.R

class SettingsFragment : PreferenceFragmentCompat() {

    private var authManager: AuthManager? = null
    val TAG = "SenSE Debug"

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
        authManager = context?.let { AuthManager(it) }
        with(authManager?.authState?.isAuthorized){
            loginPreference?.isEnabled = !this!!
            logoutPreference?.isEnabled = this!!
        }

        // Clicking loginPreference directs to LoginActivity to handle authorization workflow
        loginPreference?.setOnPreferenceClickListener{
            val intent = Intent(context, LoginActivity::class.java)
            startActivity(intent)
            true
        }

        // Logs the user out when clicked
        logoutPreference?.setOnPreferenceClickListener{
            authManager?.logoutUser()
            true
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

//    // TODO: Get rid of this?
//    override fun onResume() {
//        super.onResume()
//
//        with(authManager?.authState?.isAuthorized){
//            findPreference<Preference?>(getString(R.string.login_pref_tag))!!.isEnabled = !this!!
//            findPreference<Preference?>(getString(R.string.logout_pref_tag))!!.isEnabled = this!!
//        }
//    }

    fun setAuthManager(authManager: AuthManager){
        this.authManager = authManager
    }
}
