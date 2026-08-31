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
    val cadence: String = "0",
    val batteryTemp: String = "0",
    val motorTemp: String = "0",
    val motorPower: String = "0",
    val socRE: String = "0",
    val tempRE: String = "0",
    val cycles: String = "0",
    val cyclesRE: String = "0"
)