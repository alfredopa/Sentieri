package com.apstudio.sentieri.layer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.R

class StradarioRecyclerViewAdapter(
    private val listener: OnItemClickListener,
    initialValues: List<StradaData> = emptyList()
) : RecyclerView.Adapter<StradarioRecyclerViewAdapter.ViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(strada: StradaData)
    }

    private val items: MutableList<StradaData> = initialValues.toMutableList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_strada, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvNomeVia.text = if (item.nomeVia.isNotBlank()) item.nomeVia else "Strada senza nome"
        val comuneText = if (item.nomeComune.isNotBlank()) item.nomeComune else "Comune N/D"
        val segmentCountInfo = if (item.segments.size > 1) " (${item.segments.size} segmenti)" else ""
        holder.tvComune.text = "$comuneText$segmentCountInfo"
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<StradaData>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getItem(position: Int): StradaData? {
        return if (position in items.indices) items[position] else null
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        val tvNomeVia: TextView = itemView.findViewById(R.id.tvNomeVia)
        val tvComune: TextView = itemView.findViewById(R.id.tvComune)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onItemClick(items[position])
            }
        }
    }
}
