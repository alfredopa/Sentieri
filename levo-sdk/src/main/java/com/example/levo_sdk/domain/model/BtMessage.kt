package com.example.levo_sdk.domain.model

data class BtMessage(
    val voltage: String = "0.0",
    val amperes: String = "0.0",
    val speed: String = "0.0",
    val trip: String = "0.0",
    val total: String = "0.0",
    val soc: String = "0",
    val assistLevel: String = "OFF",
    val riderPower: String = "0",
    val batteryTemp: String = "0",
    val motorTemp: String = "0"
)
