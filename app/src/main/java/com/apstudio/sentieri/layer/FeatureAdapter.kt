package com.apstudio.sentieri.layer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.graphics.toColorInt
import com.apstudio.sentieri.R


class FeatureAdapter (
    private val featureTableInfo: MutableList<FeatureTableInfo>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<FeatureAdapter.FeatureViewHolder>() {
    private val coloreDefault = "#0000FF"

    interface OnItemClickListener {
        // Questo metodo verrà chiamato quando un elemento viene cliccato
        // Prende come parametro l'oggetto dati associato all'elemento cliccato
        fun onItemClick(position: Int)
        fun onSwitchCheckedChanged(position: Int, isChecked: Boolean)
    }


    inner class FeatureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        val coloreTabella: TextView = itemView.findViewById(R.id.txColore)
        val nomeTabella: TextView = itemView.findViewById(R.id.txtabella)
        val switchView: Switch = itemView.findViewById(R.id.swcVisible)

        // Definisci il listener una sola volta
        private val checkedChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            val currentPosition = adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                // Notifica SOLO l'Activity/Fragment. Non modificare featureTableInfo qui.
                listener.onSwitchCheckedChanged(currentPosition, isChecked)
            }
        }

        init {
            itemView.setOnClickListener(this)
            // Imposta il listener definito sopra
            switchView.setOnCheckedChangeListener(checkedChangeListener)
        }

        fun bind(item: FeatureTableInfo) {

            if (item.colore == "RANDOM")
                coloreTabella.setBackgroundColor(coloreDefault.toColorInt())
            else
                coloreTabella.setBackgroundColor(item.colore.toColorInt())
            nomeTabella.text = item.descrTabella

            // 1. Rimuovi TEMPORANEAMENTE il listener per evitare che si attivi
            //    quando imposti programmaticamente lo stato dello switch.
            switchView.setOnCheckedChangeListener(null)

            // 2. Imposta lo stato dello switch dal modello dati
            switchView.isChecked = item.isVisible

            // 3. Riattacca il listener originale (quello definito nel ViewHolder)
            switchView.setOnCheckedChangeListener(checkedChangeListener)
        }

        override fun onClick(v: View?) {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onItemClick(position)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): FeatureViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.lista_feature, parent, false) // Layout con lo Switch
        return FeatureViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: FeatureViewHolder, position: Int) {
        val currentItem = featureTableInfo[position]
        holder.bind(currentItem) // Chiama un metodo di bind nel ViewHolder
    }

    override fun getItemCount(): Int {
        return featureTableInfo.size
    }


}