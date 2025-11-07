package com.apstudio.sentieri

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.db.OnItemClickListener
import com.bumptech.glide.Glide

class ImageAdapter(private val images: MutableList<Uri>) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {
    private var onItemClickListener: OnItemClickListener? = null
    fun setOnItemClickListener(listener: OnItemClickListener) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
        //Log.d("ImageAdapter", "onCreateViewHolder")
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(images[position].toString())
        holder.setOnItemClickListener(onItemClickListener)
    }

    override fun getItemCount(): Int = images.size

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.small_photo)
        init {
            // Imposta il listener di click sul root layout
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClickListener?.onItemClick(position)
                }
            }
        }
        fun bind(imageUrl: String) {
            // Load the image into the ImageView
            Glide.with(itemView.context)
                .load(imageUrl)
                .into(imageView)
        }
        private var onItemClickListener: OnItemClickListener? = null

        fun setOnItemClickListener(listener: OnItemClickListener?) {
            this.onItemClickListener = listener
        }
    }
}