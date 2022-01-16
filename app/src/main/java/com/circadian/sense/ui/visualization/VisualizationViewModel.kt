package com.circadian.sense.ui.visualization

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class VisualizationViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Data Visualization"
    }
    val text: LiveData<String> = _text


}