package com.apstudio.sentieri

import android.content.Context
import android.content.res.ColorStateList
import android.media.MediaPlayer
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.databinding.FragmentPoiDettaglioBinding
import com.apstudio.sentieri.db.OnItemClickListener
import net.federicomatera.agpxp.models.WayPoint
import java.io.IOException

class PoiAdapter(private val poiList: List<WayPoint>) : RecyclerView.Adapter<PoiAdapter.PoiViewHolder>() {

    private var onItemClickListener: OnItemClickListener? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPath: String? = null

    fun setOnItemClickListener(listener: OnItemClickListener) {
        onItemClickListener = listener
    }

    private fun playAudio(filePath: String, context: Context) {
        if (currentlyPlayingPath == filePath && mediaPlayer?.isPlaying == true) {
            // Se lo stesso file è già in riproduzione, fermalo (o mettilo in pausa, a tua scelta)
            stopCurrentPlayback()
            return
        }
        stopCurrentPlayback() // Ferma qualsiasi riproduzione precedente
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(filePath)
                prepareAsync() // Prepara in modo asincrono per non bloccare UI thread
                setOnPreparedListener {
                    start()
                    currentlyPlayingPath = filePath
                    // Qui potresti voler aggiornare l'UI del pulsante per indicare la riproduzione
                }
                setOnCompletionListener {
                    stopCurrentPlayback()
                    // Qui potresti voler aggiornare l'UI del pulsante allo stato normale
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e("PoiAdapter", "MediaPlayer Error: what: $what, extra: $extra for path $filePath")
                    stopCurrentPlayback()
                    // Gestisci l'errore, ad esempio mostrando un Toast
                    true // Indica che l'errore è stato gestito
                }
            } catch (e: IOException) {
                Log.e("PoiAdapter", "MediaPlayer IOException: ${e.message} for path $filePath")
                stopCurrentPlayback()
                // Gestisci l'eccezione, ad esempio mostrando un Toast
            } catch (e: IllegalStateException) {
                Log.e("PoiAdapter", "MediaPlayer IllegalStateException: ${e.message} for path $filePath")
                stopCurrentPlayback()
            }
        }
    }

    fun stopCurrentPlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.reset() // Resetta per riutilizzare o rilasciare
            it.release()
        }
        mediaPlayer = null
        currentlyPlayingPath = null
        // Qui potresti voler aggiornare l'UI di tutti i pulsanti allo stato normale se necessario
    }

    fun releasePlayer() {
        stopCurrentPlayback()
    }

    class PoiViewHolder(
        private val binding: FragmentPoiDettaglioBinding,
        private val onPlayAudioClicked: (filePath: String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentOnItemClickListener: OnItemClickListener? = null

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    currentOnItemClickListener?.onItemClick(position)
                }
            }
        }

        fun bind(poi: WayPoint) {
            binding.nomeText.text = poi.name ?: ""

            val descriptionText = if (poi.description?.isNotEmpty() == true) {
                poi.description
            } else {
                ""
            }
            binding.tvDescriz.text = descriptionText

            if (poi.comment?.isNotEmpty() == true) {
                val currentTextInTextView = binding.tvDescriz.text?.toString() ?: ("" + poi.comment)
                binding.tvDescriz.text = currentTextInTextView
            }

            binding.tvAlti.text = poi.elevation?.toInt()?.toString() ?: "N/A"
            // Assumendo che latitude e longitude non siano null in WayPoint come da libreria AGPXP
            binding.tvLat.text = String.format("%.6f", poi.latitude)
            binding.tvLon.text = String.format("%.6f", poi.longitude)

            val context = binding.root.context

            if (poi.src?.isNotEmpty() == true) {
                binding.btnVoice.isVisible = true
                binding.btnVoice.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.blurred_white ))
                binding.btnVoice.isEnabled = true
                binding.btnVoice.isClickable = true
                binding.btnVoice.setOnClickListener {
                    // È sicuro usare poi.src!! qui perché abbiamo già controllato isNotEmpty()
                    onPlayAudioClicked(poi.src)
                }
            } else {
                binding.btnVoice.isVisible = false
                binding.btnVoice.isEnabled = false
                binding.btnVoice.setOnClickListener(null) // Rimuovi il listener se non c'è audio
            }
        }

        fun setOnItemClickListener(listener: OnItemClickListener?) {
            this.currentOnItemClickListener = listener
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PoiViewHolder {
        val binding = FragmentPoiDettaglioBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PoiViewHolder(binding) { filePath ->
            playAudio(filePath, parent.context)
        }
    }

    override fun getItemCount(): Int {
        return poiList.size
    }

    override fun onBindViewHolder(holder: PoiViewHolder, position: Int) {
        val poi = poiList[position]
        holder.bind(poi)
        // Passa il listener generico dell'adapter per il click sull'intera riga
        holder.setOnItemClickListener(onItemClickListener)
    }
}