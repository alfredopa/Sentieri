package com.apstudio.sentieri

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.databinding.ItemSearchResultBinding
import com.apstudio.sentieri.db.Sentieri

class SearchAdapter(
    private val onItemSelected: (Sentieri, Boolean) -> Unit
) : RecyclerView.Adapter<SearchAdapter.ViewHolder>() {

    private var items: List<Sentieri> = emptyList()
    private val selectedIds = mutableSetOf<Int>()

    fun submitList(newList: List<Sentieri>) {
        items = newList.sortedByDescending { it.DataOra }
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<Sentieri> {
        return items.filter { selectedIds.contains(it.id) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Sentieri) {
            binding.tvNome.text = item.nome
            binding.tvDettagli.text = "Data: ${MapUtils.prnDataFromUtc(item.DataOra)} - Lunghezza: ${String.format("%.2f", item.lunghezza/1000)} km"
            
            binding.checkBox.setOnCheckedChangeListener(null)
            binding.checkBox.isChecked = selectedIds.contains(item.id)
            
            binding.checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedIds.add(item.id) else selectedIds.remove(item.id)
                onItemSelected(item, isChecked)
            }
            
            binding.root.setOnClickListener {
                binding.checkBox.isChecked = !binding.checkBox.isChecked
            }
        }
    }
}
