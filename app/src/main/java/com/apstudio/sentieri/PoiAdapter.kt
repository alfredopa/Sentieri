package com.apstudio.sentieri

import android.content.Context
import android.content.res.ColorStateList
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.databinding.FragmentPoiDettaglioBinding
import com.apstudio.sentieri.db.OnItemClickListener
import net.federicomatera.agpxp.models.WayPoint
import java.io.File
import java.io.FileInputStream
import java.util.Locale

class PoiAdapter(private val poiList: List<WayPoint>) : RecyclerView.Adapter<PoiAdapter.PoiViewHolder>() {

    private var onItemClickListener: OnItemClickListener? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPath: String? = null

    fun setOnItemClickListener(listener: OnItemClickListener) {
        onItemClickListener = listener
    }

    private fun playAudio(filePath: String, context: Context) {
        if (currentlyPlayingPath == filePath && mediaPlayer?.isPlaying == true) {
            stopCurrentPlayback()
            return
        }
        stopCurrentPlayback()

        val audioFile = File(filePath)
        val isUri = filePath.startsWith("content://")

        if (!isUri && !audioFile.exists()) {
            Log.e("PoiAdapter", "File audio non trovato: $filePath")
            Toast.makeText(context, "File audio non trovato", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isUri) {
            Log.d("PoiAdapter", "File: ${audioFile.name}. Dimensione: ${audioFile.length()} bytes")
        }

        // Controllo e forzatura volume multimediale
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (currentVolume == 0) {
            Log.w("PoiAdapter", "Volume STREAM_MUSIC è a zero. Provo ad alzarlo.")
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVolume * 0.8).toInt(), AudioManager.FLAG_SHOW_UI)
        }

        mediaPlayer = MediaPlayer().apply {
            try {
                // Impostiamo STREAM_MUSIC (vecchio metodo ma spesso più affidabile per il routing immediato)
                @Suppress("DEPRECATION")
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                
                // Anche AudioAttributes per sicurezza sui dispositivi più nuovi
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )

                if (isUri) {
                    setDataSource(context, Uri.parse(filePath))
                } else {
                    FileInputStream(audioFile).use { fis ->
                        setDataSource(fis.fd)
                    }
                }

                setVolume(1.0f, 1.0f)
                
                prepareAsync()
                setOnPreparedListener { mp ->
                    Log.d("PoiAdapter", "Preparato correttamente. Durata rilevata: ${mp.duration} ms")
                    mp.start()
                    currentlyPlayingPath = filePath
                }
                setOnCompletionListener {
                    Log.d("PoiAdapter", "Riproduzione completata.")
                    stopCurrentPlayback()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("PoiAdapter", "MediaPlayer Error: what: $what, extra: $extra")
                    stopCurrentPlayback()
                    true
                }
            } catch (e: Exception) {
                Log.e("PoiAdapter", "Errore critico MediaPlayer: ${e.message}")
                Toast.makeText(context, "Errore durante l'avvio dell'audio", Toast.LENGTH_SHORT).show()
                stopCurrentPlayback()
            }
        }
    }

    fun stopCurrentPlayback() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
            } catch (_: Exception) {}
            it.reset()
            it.release()
        }
        mediaPlayer = null
        currentlyPlayingPath = null
    }

    class PoiViewHolder(
        private val binding: FragmentPoiDettaglioBinding,
        private val onPlayAudioClicked: (filePath: String, context: Context) -> Unit
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
                val currentTextInTextView = binding.tvDescriz.text?.toString() ?: ""
                binding.tvDescriz.text = if (currentTextInTextView.isEmpty()) poi.comment else "$currentTextInTextView\n${poi.comment}"
            }

            binding.tvAlti.text = poi.elevation?.toInt()?.toString() ?: "N/A"
            binding.tvLat.text = String.format(Locale.US, "%.6f", poi.latitude)
            binding.tvLon.text = String.format(Locale.US, "%.6f", poi.longitude)

            val context = binding.root.context

            if (poi.src?.isNotEmpty() == true) {
                binding.btnVoice.isVisible = true
                binding.btnVoice.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.blurred_white ))
                binding.btnVoice.isEnabled = true
                binding.btnVoice.setOnClickListener {
                    onPlayAudioClicked(poi.src!!, context)
                }
            } else {
                binding.btnVoice.isVisible = false
                binding.btnVoice.setOnClickListener(null)
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
        return PoiViewHolder(binding) { filePath, context ->
            playAudio(filePath, context)
        }
    }

    override fun getItemCount(): Int = poiList.size

    override fun onBindViewHolder(holder: PoiViewHolder, position: Int) {
        val poi = poiList[position]
        holder.bind(poi)
        holder.setOnItemClickListener(onItemClickListener)
    }
}