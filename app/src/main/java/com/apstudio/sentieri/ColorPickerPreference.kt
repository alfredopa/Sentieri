package com.apstudio.sentieri

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.core.graphics.drawable.toDrawable
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder

// Classe per la preferenza personalizzata del selettore colori
class ColorPickerPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    private var selectedColor: Int = Color.RED // Colore di default

    // Questo viene chiamato quando la preferenza viene creata
    init {
        // Imposta un layout per mostrare un'anteprima del colore
        widgetLayoutResource = R.layout.preference_color_preview
    }

    // Questo viene chiamato quando la preferenza viene associata alla sua vista
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val colorPreview = holder.findViewById(R.id.color_preview)
        colorPreview.background = selectedColor.toDrawable()
    }

    // Quando l'utente clicca sulla preferenza
    override fun onClick() {
        super.onClick()

        ColorPickerDialogBuilder
            .with(context)
            .setTitle("Scegli il colore della traccia")
            .initialColor(selectedColor) // Colore iniziale mostrato nel picker
            .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
            .density(12)
            .setPositiveButton("OK") { _, color, _ ->
                // Salva il colore selezionato nelle SharedPreferences
                persistInt(color)
                // Aggiorna la nostra variabile interna e la UI
                selectedColor = color
                notifyChanged() // Forza la preferenza a ridisegnarsi per mostrare il nuovo colore
            }
            .setNegativeButton("Annulla") { _, _ -> }
            .build()
            .show()
    }

    // Recupera il valore salvato quando la preferenza viene inizializzata
    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(defaultValue)
        // Recupera il colore salvato, o usa il colore di default se non c'è nulla
        selectedColor = getPersistedInt(defaultValue as? Int ?: Color.RED)
    }
}
