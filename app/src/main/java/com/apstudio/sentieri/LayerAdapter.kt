package com.apstudio.sentieri

import android.view.LayoutInflater
import android.view.ViewGroup
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
        // Disabilita temporaneamente i listener per evitare trigger ciclici durante il bind
        holder.setOnItemClickListener(null)
        
        holder.binding.txTraccia.text = item.nome
        holder.binding.swcVisibile.isChecked = item.abilitato
        holder.binding.swcDirezione.isChecked = item.direzione
        holder.binding.swcQuota.isChecked = item.mostraPendenza
        holder.binding.btnSegui.isChecked = item.segui
        
        // Riattiva il listener
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
                    onItemClickListener?.onswcVisibileClick(position, isChecked)
                }
            }

            binding.swcQuota.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClickListener?.onswcQuotaPendenzaClick(position, isChecked)
                }
            }

            binding.swcDirezione.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClickListener?.onswcDirezioneClick(position, isChecked)
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

