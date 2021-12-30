package com.circadian.sense.ui.settings

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.circadian.sense.R

class SettingsViewModel : ViewModel() {
    private val _text = MutableLiveData<Int>().apply {
        value = R.string.title_settings
    }
    val text: LiveData<Int> = _text
}