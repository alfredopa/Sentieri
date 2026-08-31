package com.example.levo_sdk.domain

import com.example.levo_sdk.domain.model.BtMessage

sealed interface ConnectionResult {
    data object ConnectionEstablished: ConnectionResult
    data object Reconnecting: ConnectionResult
    data class TransferSucceeded(val message: BtMessage, val log: String): ConnectionResult
    data class Error(val message: String): ConnectionResult
}
