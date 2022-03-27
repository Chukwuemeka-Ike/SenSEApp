package com.circadian.sense.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.circadian.sense.R

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<Int>().apply {
        value = R.string.home_greeting
    }
    val text: LiveData<Int> = _text
}