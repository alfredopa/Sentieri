package com.apstudio.sentieri.layer

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.R
import com.apstudio.sentieri.db.FieldSchemaInfo

/**
 * Adapter per visualizzare una lista di feature (come Map<String, Any>)
 * in un RecyclerView, mostrando dinamicamente i campi basati sulla configurazione.
 *
 * @param features Lista di feature da visualizzare. Ogni feature è una mappa
 *                 dove la chiave è il nome del campo e il valore è il valore del campo.
 * @param fieldConfiguration Lista di coppie (NomeCampo, Visibile) che definisce
 *                           quali campi mostrare e il loro ordine.
 */
class DynamicFeatureAdapter(
    private val features: List<Map<String, Any?>>,
    private val fieldConfiguration: List<FieldSchemaInfo>,
    private val showDetailsButton: Boolean
) : RecyclerView.Adapter<DynamicFeatureAdapter.FeatureViewHolder>() {

    // 1. Interfaccia per il Click Listener
    interface OnItemClickListener {
        fun onItemClicked(latitude: Double, longitude: Double, elevation: Double?)
        fun onDetailsButtonClicked(itemData: Map<String, Any?>)
    }

    // 2. Campo per il listener
    private var itemClickListener: OnItemClickListener? = null

    // Metodo per impostare il listener dall'esterno (es. da FeatureList)
    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeatureViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.dynamic_feature_item, parent, false) // Usa il tuo layout con CardView
        return FeatureViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: FeatureViewHolder, position: Int) {
        val currentFeature = features[position]
        holder.bind(currentFeature, fieldConfiguration)
        // AGGIUNGI questa logica per la visibilità del bottone
        if (showDetailsButton) {
            holder.detailsButton.visibility = View.VISIBLE
        } else {
            holder.detailsButton.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = features.size

    // 3. Rendi FeatureViewHolder una inner class per accedere a 'features' e 'itemClickListener'
    inner class FeatureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fieldsContainer: LinearLayout = itemView.findViewById(R.id.feature_fields_container)
        val detailsButton: Button = itemView.findViewById(R.id.details_button)
        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION && itemClickListener != null) {
                    val clickedFeature = features[position] // Accedi alla feature cliccata

                    // Estrai le coordinate pre-processate (assumendo che FeatureList le aggiunga)
                    val latitude = clickedFeature["__INTERNAL_LATITUDE__"] as? Double
                    val longitude = clickedFeature["__INTERNAL_LONGITUDE__"] as? Double
                    val elevation = clickedFeature["__INTERNAL_ELEVATION__"] as? Double // Può essere null

                    if (latitude != null && longitude != null) {
                        itemClickListener?.onItemClicked(latitude, longitude, elevation)
                    } else {
                        Log.e("DynamicFeatureAdapter", "Coordinate interne non trovate o non valide nella mappa della feature.")
                        // Potresti voler notificare l'utente o gestire l'errore diversamente
                    }
                }
            }
            // AGGIUNGI il listener per il click sul bottone
            detailsButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    itemClickListener?.onDetailsButtonClicked(features[position])
                }
            }
        }

        fun bind(featureData: Map<String, Any?>, config: List<FieldSchemaInfo>) {
            fieldsContainer.removeAllViews()
            val visibleFields = config.filter { it.isVisible }
            visibleFields.forEachIndexed { index, (campo: String, descrizione: String) ->
                // Non mostrare i campi interni delle coordinate se non vuoi
                if (campo == "__INTERNAL_LATITUDE__" || campo == "__INTERNAL_LONGITUDE__" || campo == "__INTERNAL_ELEVATION__") {
                    // Salta questi campi se non devono essere visualizzati direttamente
                    // return@forEachIndexed // 'return' qui non è corretto per forEachIndexed
                } else {
                    val fieldValue = featureData[campo]?.toString() ?: "N/D"
                    val textView = TextView(fieldsContainer.context).apply {
                        text = "$descrizione: $fieldValue"
                        val padding = (8 * resources.displayMetrics.density).toInt()
                        setPadding(0, padding / 4, 0, padding / 4)
                        textSize = 14f
                    }
                    fieldsContainer.addView(textView)

                    // Aggiungi un separatore se vuoi (Opzione 3 della risposta precedente)
                    if (index < visibleFields.filterNot { it.name.startsWith("__INTERNAL_") }.size - 1) { // Evita il separatore dopo l'ultimo campo visibile *reale*
                        val separator = View(fieldsContainer.context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                (1 * resources.displayMetrics.density).toInt()
                            )
                            setBackgroundColor(android.graphics.Color.LTGRAY)
                            val verticalMargin = (4 * resources.displayMetrics.density).toInt()
                            (layoutParams as LinearLayout.LayoutParams).setMargins(0, verticalMargin, 0, verticalMargin)
                        }
                        fieldsContainer.addView(separator)
                    }
                }
            }
        }
    }

    // Chiavi costanti per le coordinate interne (da usare anche in FeatureList)
    companion object {
        const val KEY_INTERNAL_LATITUDE = "__INTERNAL_LATITUDE__"
        const val KEY_INTERNAL_LONGITUDE = "__INTERNAL_LONGITUDE__"
        const val KEY_INTERNAL_ELEVATION = "__INTERNAL_ELEVATION__"
    }
}
