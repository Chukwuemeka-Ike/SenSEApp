package com.circadian.sense.ui.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.circadian.sense.AuthManager
import com.circadian.sense.MainActivity
import com.circadian.sense.R
import com.circadian.sense.databinding.FragmentSettingsBinding
import com.circadian.sense.ui.home.HomeViewModel
import net.openid.appauth.AuthState

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

        // Clicking loginPreference directs to LoginActivity to handle authorization workflow
        val loginPreference: Preference? = findPreference(getString(R.string.login_pref_tag))
        loginPreference?.setOnPreferenceClickListener{
            val intent = Intent(context, LoginActivity::class.java)
            startActivity(intent)
            true
        }

        // TODO: Implement logout functionality to clear user data and revoke access
        val logoutPreference: Preference? = findPreference(getString(R.string.logout_pref_tag))
        logoutPreference?.setOnPreferenceClickListener{
            true
        }


        // If authorized, disable loginPreference and enable logoutPreference - and vice versa
        authManager = context?.let { AuthManager(it) }
        with(authManager?.authState?.isAuthorized){
            loginPreference?.isEnabled = !this!!
            logoutPreference?.isEnabled = this!!
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    // TODO: Get rid of this?
    override fun onResume() {
        super.onResume()

        with(authManager?.authState?.isAuthorized){
            findPreference<Preference?>(getString(R.string.login_pref_tag))!!.isEnabled = !this!!
            findPreference<Preference?>(getString(R.string.logout_pref_tag))!!.isEnabled = this!!
        }
    }

    fun setAuthManager(authManager: AuthManager){
        this.authManager = authManager
    }
}
