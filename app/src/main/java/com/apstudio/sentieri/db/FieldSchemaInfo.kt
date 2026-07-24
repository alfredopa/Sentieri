package com.apstudio.sentieri.db

import androidx.annotation.Keep

@Keep
data class FieldSchemaInfo(
    val name: String,
    val description: String,
    val isVisible: Boolean
)