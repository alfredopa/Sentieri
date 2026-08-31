package com.example.levo_sdk.data.protocol

import com.example.levo_sdk.domain.model.BtMessage
import java.util.Locale

object LevoProtocol {

    fun decode(data: ByteArray, current: BtMessage): BtMessage {
        if (data.size < 3) return current

        val sender = data[0].toInt() and 0xFF
        val channel = data[1].toInt() and 0xFF

        val rawData = data.sliceArray(2 until data.size)

        return when (sender) {
            0x00 -> handleBattery(channel, rawData, current, isMain = true)
            0x04 -> handleBattery(channel, rawData, current, isMain = false)
            0x01 -> handleMotor(channel, rawData, current)
            else -> current
        }
    }

    private fun handleBattery(channel: Int, data: ByteArray, current: BtMessage, isMain: Boolean): BtMessage {
        return when (channel) {
            0x03 -> {
                val temp = "${data[0].toInt() and 0xFF}"
                if (isMain) current.copy(batteryTemp = temp) else current.copy(tempRE = temp)
            }
            0x05 -> {
                if (!isMain) return current
                val raw = data[0].toInt() and 0xFF
                val v = raw.toFloat() / 5f + 20f
                current.copy(voltage = String.format(Locale.US, "%.1f", v))
            }
            0x06 -> {
                if (!isMain) return current
                val raw = data[0].toInt() and 0xFF
                val a = raw.toFloat() / 5f
                current.copy(amperes = String.format(Locale.US, "%.1f", a))
            }
            0x0C -> {
                val soc = "${data[0].toInt() and 0xFF}"
                if (isMain) current.copy(soc = soc) else current.copy(socRE = soc)
            }
            0x0D -> {
                val cycles = "${readUint16(data)}"
                if (isMain) current.copy(cycles = cycles) else current.copy(cyclesRE = cycles)
            }
            else -> current
        }
    }

    private fun handleMotor(channel: Int, data: ByteArray, current: BtMessage): BtMessage {
        return when (channel) {
            0x00 -> current.copy(riderPower = "${readUint16(data)}")
            0x01 -> current.copy(cadence = "${readUint16(data) / 10}")
            0x02 -> {
                val raw = readUint16(data)
                val s = raw.toFloat() / 10f
                current.copy(speed = String.format(Locale.US, "%.1f", s))
            }
            0x04 -> {
                val raw = readUint32(data)
                val km = raw.toFloat() / 1000f
                current.copy(total = String.format(Locale.US, "%.1f", km))
            }
            0x05 -> {
                val raw = readUint16(data)
                val level = when (raw) {
                    0 -> "OFF"
                    1 -> "ECO"
                    2 -> "TRAIL"
                    3 -> "TURBO"
                    else -> "???"
                }
                current.copy(assistLevel = level)
            }
            0x07 -> current.copy(motorTemp = "${data[0].toInt() and 0xFF}")
            0x0C -> current.copy(motorPower = "${readUint16(data)}")
            else -> current
        }
    }

    private fun readUint16(data: ByteArray): Int {
        if (data.size < 2) return (data.getOrNull(0)?.toInt() ?: 0) and 0xFF
        return (data[1].toInt() and 0xFF shl 8) or (data[0].toInt() and 0xFF)
    }

    private fun readUint32(data: ByteArray): Long {
        if (data.size < 4) return 0L
        var res = 0L
        for (i in 0..3) {
            res = res or ((data[i].toLong() and 0xFF) shl (i * 8))
        }
        return res
    }
}

fun ByteArray.toHexString(): String = joinToString(separator = " ") { String.format("%02X", it) }
