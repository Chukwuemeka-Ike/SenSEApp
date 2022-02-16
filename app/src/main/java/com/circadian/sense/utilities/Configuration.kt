package com.circadian.sense.utilities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.net.Uri
import android.text.TextUtils
import com.circadian.sense.R
import net.openid.appauth.connectivity.ConnectionBuilder
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import okio.BufferedSource
import okio.buffer
import okio.source
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.charset.Charset

/**
 * Reads and validates the app configuration from `res/raw/auth_config.json`. Configuration
 * changes are detected by comparing the hash of the last known configuration to the read
 * configuration. When a configuration change is detected, the app state is reset.
 */
class Configuration(private val mContext: Context) {

    private val mPrefs: SharedPreferences
    private val mResources: Resources

    /**
     * Returns a description of the configuration error, if the configuration is invalid.
     */
    private var mConfigJson: JSONObject? = null
    private var mConfigHash: String? = null
    private var mConfigError: String? = null

    private var mClientId: String? = null
    private var mScope: String? = null
    private var mRedirectUri: Uri? = null
    private var mEndSessionRedirectUri: Uri? = null
    private var mDiscoveryUri: Uri? = null
    private var mAuthEndpointUri: Uri? = null
    private var mTokenEndpointUri: Uri? = null
    private var mEndSessionEndpoint: Uri? = null
    private var mRegistrationEndpointUri: Uri? = null
    private var mUserInfoEndpointUri: Uri? = null
    private var mRevokeTokenEndpointUri: Uri? = null
    private var mHttpsRequired = true

    /**
     * Indicates whether the configuration has changed from the last known valid state.
     */
    fun hasConfigurationChanged(): Boolean {
        val lastHash = lastKnownConfigHash
        return mConfigHash != lastHash
    }

    /**
     * Indicates whether the current configuration is valid.
     */
    val isValid: Boolean
        get() = mConfigError == null

    /**
     * Returns a description of the configuration error, if the configuration is invalid.
     */
    fun getConfigurationError(): String? {
        return mConfigError
    }

    /**
     * Indicates that the current configuration should be accepted as the "last known valid"
     * configuration.
     */
    fun acceptConfiguration() {
        mPrefs.edit().putString(KEY_LAST_HASH, mConfigHash).apply()
    }

    // TODO: Change all of these to backing properties
    fun getClientId(): String? { return mClientId }

    fun getScope(): String { return mScope!! }

    fun getRedirectUri(): Uri { return mRedirectUri!! }

    fun getDiscoveryUri(): Uri? { return mDiscoveryUri }

    fun getEndSessionRedirectUri(): Uri? { return mEndSessionRedirectUri }

    fun getAuthEndpointUri(): Uri? { return mAuthEndpointUri }

    fun getTokenEndpointUri(): Uri? { return mTokenEndpointUri }

    fun getEndSessionEndpoint(): Uri? { return mEndSessionEndpoint }

    fun getRegistrationEndpointUri(): Uri? { return mRegistrationEndpointUri }

    fun getUserInfoEndpointUri(): Uri? { return mUserInfoEndpointUri }

    fun getRevokeTokenEndpointUri(): Uri? { return mRevokeTokenEndpointUri }

    fun isHttpsRequired(): Boolean { return mHttpsRequired }

    val connectionBuilder: ConnectionBuilder
        get() {
//            return if (mHttpsRequired) {
//                DefaultConnectionBuilder.INSTANCE
//            } else ConnectionBuilderForTesting.INSTANCE
            return DefaultConnectionBuilder.INSTANCE
        }
    private val lastKnownConfigHash: String?
        get() = mPrefs.getString(KEY_LAST_HASH, null)

    @Throws(InvalidConfigurationException::class)
    private fun readConfiguration() {

        val configSource: BufferedSource =
            mResources.openRawResource(R.raw.auth_config).source().buffer()
        val configData = okio.Buffer()

        try {
            configSource.readAll(configData)
            mConfigJson = JSONObject(configData.readString(Charset.forName("UTF-8")))
        } catch (ex: IOException) {
            throw InvalidConfigurationException(
                "Failed to read configuration: " + ex.message
            )
        } catch (ex: JSONException) {
            throw InvalidConfigurationException(
                "Unable to parse configuration: " + ex.message
            )
        }
        mConfigHash = configData.sha256().base64()
        mClientId = getConfigString("client_id")
        mScope = getRequiredConfigString("authorization_scope")
        mRedirectUri = getRequiredConfigUri("redirect_uri")
        mEndSessionRedirectUri = getRequiredConfigUri("end_session_redirect_uri")
        mRevokeTokenEndpointUri = getRequiredConfigUri("revoke_token_endpoint_uri")

        if (!isRedirectUriRegistered) {
            throw InvalidConfigurationException(
                "redirect_uri is not handled by any activity in this app! "
                        + "Ensure that the appAuthRedirectScheme in your build.gradle file "
                        + "is correctly configured, or that an appropriate intent filter "
                        + "exists in your app manifest."
            )
        }
        if (getConfigString("discovery_uri") == null) {
            mAuthEndpointUri = getRequiredConfigWebUri("authorization_endpoint_uri")
            mTokenEndpointUri = getRequiredConfigWebUri("token_endpoint_uri")
            mUserInfoEndpointUri = getRequiredConfigWebUri("user_info_endpoint_uri")
            mEndSessionEndpoint = getRequiredConfigUri("end_session_endpoint")
            if (mClientId == null) {
                mRegistrationEndpointUri = getRequiredConfigWebUri("registration_endpoint_uri")
            }
        } else {
            mDiscoveryUri = getRequiredConfigWebUri("discovery_uri")
        }
        mHttpsRequired = mConfigJson!!.optBoolean("https_required", true)
    }

    fun getConfigString(propName: String?): String? {
        var value = mConfigJson!!.optString(propName)
//        if (value == null) {
//            return null
//        }
        value = value.trim { it <= ' ' }
        return if (TextUtils.isEmpty(value)) {
            null
        } else value
    }

    @Throws(InvalidConfigurationException::class)
    private fun getRequiredConfigString(propName: String): String {
        val value = getConfigString(propName)
            ?: throw InvalidConfigurationException(
                "$propName is required but not specified in the configuration"
            )
        return value
    }

    @Throws(InvalidConfigurationException::class)
    fun getRequiredConfigUri(propName: String): Uri {
        val uriStr = getRequiredConfigString(propName)
        val uri: Uri
        try {
            uri = Uri.parse(uriStr)
        } catch (ex: Throwable) {
            throw InvalidConfigurationException("$propName could not be parsed", ex)
        }
        if (!uri.isHierarchical || !uri.isAbsolute) {
            throw InvalidConfigurationException(
                "$propName must be hierarchical and absolute"
            )
        }
        if (!TextUtils.isEmpty(uri.encodedUserInfo)) {
            throw InvalidConfigurationException("$propName must not have user info")
        }
        if (!TextUtils.isEmpty(uri.encodedQuery)) {
            throw InvalidConfigurationException("$propName must not have query parameters")
        }
        if (!TextUtils.isEmpty(uri.encodedFragment)) {
            throw InvalidConfigurationException("$propName must not have a fragment")
        }
        return uri
    }

    @Throws(InvalidConfigurationException::class)
    fun getRequiredConfigWebUri(propName: String): Uri {
        val uri = getRequiredConfigUri(propName)
        val scheme = uri.scheme
        if (TextUtils.isEmpty(scheme) || !(("http" == scheme) || ("https" == scheme))) {
            throw InvalidConfigurationException(
                "$propName must have an http or https scheme"
            )
        }
        return uri
    }

    // ensure that the redirect URI declared in the configuration is handled by some activity
    // in the app, by querying the package manager speculatively
    private val isRedirectUriRegistered: Boolean
        get() {
            // ensure that the redirect URI declared in the configuration is handled by some activity
            // in the app, by querying the package manager speculatively
            val redirectIntent = Intent()
            redirectIntent.setPackage(mContext.packageName)
            redirectIntent.action = Intent.ACTION_VIEW
            redirectIntent.addCategory(Intent.CATEGORY_BROWSABLE)
            redirectIntent.data = mRedirectUri
            return !mContext.packageManager.queryIntentActivities(redirectIntent, 0).isEmpty()
        }

    class InvalidConfigurationException : Exception {
        internal constructor(reason: String?) : super(reason) {}
        internal constructor(reason: String?, cause: Throwable?) : super(reason, cause) {}
    }

    companion object {
        private val TAG = "Configuration"
        private val PREFS_NAME = "config"
        private val KEY_LAST_HASH = "lastHash"
        private var sInstance = WeakReference<Configuration?>(null)

        fun getInstance(context: Context): Configuration {
            var config = sInstance.get()
            if (config == null) {
                config = Configuration(context)
                sInstance = WeakReference(config)
            }
            return config
        }
    }

    init {
        mPrefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        mResources = mContext.resources
        try {
            readConfiguration()
        } catch (ex: InvalidConfigurationException) {
            mConfigError = ex.message
        }
    }
}