package com.apstudio.sentieri.Levo

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.levo_sdk.domain.model.BtMessage

@Composable
fun BikeScreen(
    ebikeMessage: BtMessage,
    isConnected: Boolean,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF09155A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "STATO E-BIKE",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (!isConnected) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Dispositivo non connesso", color = Color.Gray)
                }
            } else {
                // Sezione Batteria
                BatterySection(ebikeMessage)

                Spacer(Modifier.height(8.dp))

                // Velocità (Grande)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = ebikeMessage.speed,
                        fontSize = 60.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                    Text(text = "km/h", fontSize = 18.sp, color = Color.Gray)
                }

                Spacer(Modifier.height(8.dp))

                // Griglia Metriche
                MetricsGrid(ebikeMessage)

                Spacer(Modifier.height(12.dp))

                // Bottone per tornare indietro
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Red, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Torna alla mappa",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BatterySection(values: BtMessage) {
    val soc = values.soc.toIntOrNull() ?: 0
    val batteryPercentage = soc.toFloat() / 100f
    val socRE = values.socRE.toIntOrNull() ?: 0
    val rePercentage = socRE.toFloat() / 100f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BatteryBar(batteryPercentage, "BATTERIA PRINCIPALE")

        if (socRE > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            BatteryBar(rePercentage, "RANGE EXTENDER")
        }
    }
}

@Composable
fun BatteryBar(percentage: Float, label: String) {
    val soc = (percentage * 100).toInt()
    val batteryColor = when {
        percentage >= 0.5f -> Color.Green
        percentage > 0.2f -> Color.Yellow
        else -> Color.Red
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.LightGray,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(32.dp)) {
            LinearProgressIndicator(
                progress = { percentage },
                color = batteryColor,
                trackColor = Color.White.copy(alpha = 0.1f),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
            )
            Text(
                text = "$soc%",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (percentage > 0.5f) Color.Black else Color.White
            )
        }
    }
}

@Composable
fun MetricsGrid(values: BtMessage) {
    val hasRE = (values.socRE.toIntOrNull() ?: 0) > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Riga Assist Level (Grande e Unica)
        Text(
            text = values.assistLevel.uppercase(),
            fontSize = 44.sp,
            fontWeight = FontWeight.ExtraBold,
            color = getAssistColor(values.assistLevel),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Coppie di valori
        MetricRow("Rider", "${values.riderPower} W", "Motore", "${values.motorPower} W")
        MetricRow("Cadenza", "${values.cadence} rpm", "Temp Mot", "${values.motorTemp} °C")
        
        // Batterie e RE
        MetricRow(
            "Temp Batt", "${values.batteryTemp} °C",
            if (hasRE) "Temp RE" else "", if (hasRE) "${values.tempRE} °C" else ""
        )
        MetricRow(
            "Cicli", values.cycles,
            if (hasRE) "Cicli RE" else "", if (hasRE) values.cyclesRE else ""
        )
        
        MetricRow("Tensione", "${values.voltage} V", "Corrente", "${values.amperes} A")
        MetricRow("Odometro", "${values.total} km", "", "")
    }
}

@Composable
fun MetricRow(label1: String, value1: String, label2: String, value2: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(Modifier.weight(1f)) {
            if (label1.isNotEmpty()) {
                MetricText(label1, value1)
            }
        }
        Box(Modifier.weight(1f)) {
            if (label2.isNotEmpty()) {
                MetricText(label2, value2)
            }
        }
    }
}

@Composable
fun MetricText(label: String, value: String, valueColor: Color = Color.White) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = label, fontSize = 17.sp, color = Color.Gray)
        Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Preview(name = "Light Mode")
@Composable
fun PreviewBikeScreen() {
    BikeScreen(
        ebikeMessage = BtMessage(
            voltage = "36.5",
            amperes = "2.5",
            speed = "25.0",
            soc = "75",
            socRE = "45",
            tempRE = "28",
            cyclesRE = "15",
            assistLevel = "TRAIL",
            riderPower = "150",
            motorPower = "250",
            cadence = "85",
            cycles = "110",
            batteryTemp = "25",
            motorTemp = "45",
            total = "1234.5"
        ),
        isConnected = true,
        onBack = {}
    )
}

fun getAssistColor(level: String): Color = when (level.uppercase()) {
    "TURBO" -> Color.Red
    "TRAIL", "SPORT" -> Color.Green
    "ECO" -> Color.Cyan
    else -> Color.White
}
