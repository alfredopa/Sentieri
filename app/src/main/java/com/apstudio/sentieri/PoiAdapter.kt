package com.apstudio.sentieri

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.databinding.FragmentPoiDettaglioBinding
import com.apstudio.sentieri.db.OnItemClickListener
import net.federicomatera.agpxp.models.WayPoint

class PoiAdapter (private val poiList: List<WayPoint>) : RecyclerView.Adapter<PoiAdapter.PoiViewHolder>(){

    private var onItemClickListener: OnItemClickListener? = null

    fun setOnItemClickListener(listener: OnItemClickListener) {
        onItemClickListener = listener
    }

    class PoiViewHolder(private val binding: FragmentPoiDettaglioBinding) : RecyclerView.ViewHolder(binding.root)
    {
        init {
            // Imposta il listener di click sul root layout
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClickListener?.onItemClick(position)
                }
            }
        }
        fun bind(poi: WayPoint) {
            binding.nomeText.text = poi.name
            binding.tvDescriz.text  = poi.comment
            binding.tvAlti.text = poi.elevation?.toInt().toString()
            binding.tvLat.text = String.format("%.6f", poi.latitude)
            binding.tvLon.text = String.format("%.6f", poi.longitude)
        }

        private var onItemClickListener: OnItemClickListener? = null

        fun setOnItemClickListener(listener: OnItemClickListener?) {
            this.onItemClickListener = listener
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PoiViewHolder {
        //        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
        val binding = FragmentPoiDettaglioBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PoiViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return poiList.size
    }

    override fun onBindViewHolder(holder: PoiViewHolder, position: Int) {
        holder.bind(poiList[position])
        holder.setOnItemClickListener(onItemClickListener)
    }
}