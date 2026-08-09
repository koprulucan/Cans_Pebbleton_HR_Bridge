package com.example.canspebbletonhrbridge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object HeartRateState {

    private val _heartRate = MutableStateFlow<Int?>(null)

    val heartRate = _heartRate.asStateFlow()

    fun update(bpm: Int) {
        _heartRate.value = bpm
    }

    fun clear() {
        _heartRate.value = null
    }
}