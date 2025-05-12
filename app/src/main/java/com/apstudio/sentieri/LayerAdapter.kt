package com.apstudio.sentieri

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.databinding.ItemLayerBinding
import com.apstudio.sentieri.db.LayerItem
import com.apstudio.sentieri.db.OnLayerClickListener

// Adapter per la RecyclerView delle tracce setta visibile o meno
class LayerAdapter(private val layerItems: MutableList<LayerItem>) :
    RecyclerView.Adapter<LayerAdapter.LayerViewHolder>() {

    private var onItemClickListener: OnLayerClickListener? = null

    fun setOnItemClickListener(listener: OnLayerClickListener) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LayerViewHolder {
        val binding = ItemLayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LayerViewHolder(binding)
    }


    override fun getItemCount(): Int {
        return layerItems.size
    }

    override fun onBindViewHolder(holder: LayerViewHolder, position: Int) {
        val item = layerItems[position]
        holder.binding.txTraccia.text = item.nome
        holder.binding.swcVisibile.isChecked = item.abilitato
        holder.binding.swcDirezione.isChecked = item.direzione
        holder.binding.btnSegui.isChecked = item.segui
        holder.setOnItemClickListener(onItemClickListener)
    }

    class LayerViewHolder(val binding: ItemLayerBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Imposta il listener di click sul root layout della view (il CardView)
            binding.root.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClickListener?.onItemLongClick(position)
                }
                //Log.d("LayerAdapter", "Long click at position $position")
                true
            }
            // Imposta il listener di click sullo switch
            binding.swcVisibile.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClickListener?.onswcVisibileClick(position)
                    // Gestisci il cambio di stato dello switch alla posizione 'position'
                    // Ad esempio, puoi aggiornare lo stato del layer corrispondente
                    //Log.d(
                    //    "LayerAdapter",
                    //    "Switch at position $position is now ${if (isChecked) "ON" else "OFF"}"
                    //)
                }
            }

            binding.swcDirezione.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClickListener?.onswcDirezioneClick(position) // Nuovo listener
                    //Log.d(
                    //    "LayerAdapter",
                    //    "Blocca switch at position $position is now ${if (isChecked) "ON" else "OFF"}"
                    //)
                }
            }

            binding.btnSegui.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClickListener?.onbtnSeguiClick(position) // Nuovo listener
                }
            }
        }

        private var onItemClickListener: OnLayerClickListener? = null

        fun setOnItemClickListener(listener: OnLayerClickListener?) {
            this.onItemClickListener = listener
        }
    }


}

