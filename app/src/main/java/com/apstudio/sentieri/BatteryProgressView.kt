package com.apstudio.sentieri

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat

class BatteryProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // 1. Configura la ProgressBar
    private val progressBar: ProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        max = 100
        progressDrawable = ContextCompat.getDrawable(context, R.drawable.battery_progress_drawable)
    }
    private val textView: TextView

    init {

        // 2. Configura la TextView
        textView = TextView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 22f // Ingrandisce il font (valore in SP)
        }

        // 3. Aggiungi i componenti al FrameLayout
        addView(progressBar)
        addView(textView)
    }

    /**
     * Aggiorna lo stato della batteria.
     * @param percentage Valore da 0.0f a 1.0f
     * @param batteryColor ColorInt opzionale per personalizzare il colore di avanzamento
     */
    fun setBatteryState(percentage: Float, batteryColor: Int? = null) {
        val clampedPercentage = percentage.coerceIn(0.0f, 1.0f)
        val soc = (clampedPercentage * 100).toInt()

        // Imposta il progresso e il testo
        progressBar.progress = soc
        textView.text = "$soc%"

        // Determina il colore in base alla carica se non fornito esternamente
        val color = batteryColor ?: when {
            soc >= 50 -> Color.GREEN
            soc >= 20 -> Color.YELLOW
            else -> Color.RED
        }
        
        progressBar.progressTintList = ColorStateList.valueOf(color)

        // Cambia il colore del testo (> 50% = Nero, altrimenti colorOnSurface del tema)
        if (clampedPercentage > 0.5f) {
            textView.setTextColor(Color.BLACK)
        } else {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnSurface,
                typedValue,
                true
            )
            textView.setTextColor(typedValue.data)
        }
    }
}

/*
Come usarlo nei layout XML
Ora puoi inserire direttamente il componente nei tuoi layout XML con le dimensioni e i margini desiderati:
<com.apstudio.sentieri.BatteryProgressView
    android:id="@+id/batteryIndicator"
    android:layout_width="90dp"
    android:layout_height="35dp" />
	
	Come aggiornarlo da codice (Activity o Fragment)

Nel tuo codice basterà richiamare la funzione setBatteryState:
Kotlin

val batteryIndicator = findViewById<BatteryProgressView>(R.id.batteryIndicator)

// Imposta la percentuale (es. 75%) e opzionalmente un colore custom
batteryIndicator.setBatteryState(
    percentage = 0.75f,
    batteryColor = Color.GREEN
)
	
	
*/