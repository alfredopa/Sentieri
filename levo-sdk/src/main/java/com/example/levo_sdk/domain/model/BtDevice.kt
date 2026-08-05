package com.example.levo_sdk.domain.model

import android.bluetooth.BluetoothClass

data class BtDevice(
    val name: String?,
    val address: String,
    val btType: Int = 0,
    val btClass: BluetoothClass? = null,
    val isPaired: Boolean = false
)
