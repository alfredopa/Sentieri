package com.apstudio.sentieri.layer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.databinding.FragmentToponimiBinding
import com.apstudio.sentieri.layer.placeholder.PlaceholderContent
import com.apstudio.sentieri.layer.placeholder.PlaceholderContent.PlaceholderItem

class ToponimiRecyclerViewAdapter(
    private val listener: OnItemClickListener,
    initialValues: MutableList<PlaceholderContent.PlaceholderItem> = mutableListOf()
) : RecyclerView.Adapter<ToponimiRecyclerViewAdapter.ViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    private val items: MutableList<PlaceholderContent.PlaceholderItem> = initialValues.toMutableList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            FragmentToponimiBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        //holder.idView.text = item.id
        holder.contentView.text = item.content
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<PlaceholderContent.PlaceholderItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    // Aggiungi questo metodo se vuoi accedere agli items dal fragment per il click
    fun getItem(position: Int): PlaceholderContent.PlaceholderItem? {
        return if (position >= 0 && position < items.size) {
            items[position]
        } else {
            null
        }
    }

    inner class ViewHolder(binding: FragmentToponimiBinding) :
        RecyclerView.ViewHolder(binding.root), View.OnClickListener {
        //val idView: TextView = binding.itemNumber
        val contentView: TextView = binding.content

        init {
            // Imposta il listener di click sull'intera vista dell'elemento
            itemView.setOnClickListener(this)
        }

        override fun toString(): String {
            return super.toString() + " '" + contentView.text + "'"
        }

        override fun onClick(v: View?) {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                // Notifica il listener (il Fragment) che un elemento è stato cliccato
                listener.onItemClick(position)
            }
        }
    }
}