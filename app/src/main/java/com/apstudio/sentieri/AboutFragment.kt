// in ui/AboutFragment.kt
package com.apstudio.sentieri // Assicurati che il package sia corretto

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.apstudio.sentieri.BuildConfig.VERSION_NAME
import com.apstudio.sentieri.databinding.FragmentAboutBinding
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.io.path.copyTo
import kotlin.io.path.exists

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    // Questa proprietà è valida solo tra onCreateView e onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Infla il layout per questo fragment usando ViewBinding
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ottieni la versione dell'app dinamicamente dal file build.gradle
        binding.tvAppNameVersion.text = "Sentieri $VERSION_NAME"
        binding.btnManuale.setOnClickListener {
            apriGuidaPdf()
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Pulisci il riferimento al binding per evitare memory leak
        _binding = null
    }


    // Funzione principale per visualizzare il PDF
    private fun apriGuidaPdf() {
        val nomeFilePdf = "Manuale Sentieri.pdf" // Sostituisci con il nome esatto del tuo file

        // Copia il file dagli assets alla cache e ottieni il suo URI
        val pdfUri: Uri? = copiaAssetInCacheEPrendiUri(requireContext(), nomeFilePdf)

        if (pdfUri == null) {
            Toast.makeText(requireContext(), "Errore: impossibile aprire il file PDF.", Toast.LENGTH_LONG).show()
            return
        }

        // Crea l'intent per visualizzare il PDF
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(pdfUri, "application/pdf")
            // Aggiungi questo flag per dare il permesso temporaneo all'app che apre il PDF
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Questo blocco viene eseguito se nessuna app per PDF è installata
            Toast.makeText(requireContext(), "Nessuna applicazione trovata per aprire i file PDF.", Toast.LENGTH_LONG).show()
            Log.e("About", "Nessun gestore per l'intent ACTION_VIEW con tipo PDF", e)
        }
    }

    /**
     * Copia un file dalla cartella 'assets' in una sottocartella della cache dell'app
     * e restituisce un URI sicuro tramite FileProvider.
     *
     * @param context Il contesto dell'applicazione.
     * @param nomeFileAsset Il nome del file da copiare dalla cartella assets.
     * @return L'URI del file copiato, o null se si verifica un errore.
     */
    private fun copiaAssetInCacheEPrendiUri(context: Context, nomeFileAsset: String): Uri? {
        // Percorso della cartella di destinazione nella cache
        val cartellaCache = File(context.cacheDir, "shared_pdfs")
        if (!cartellaCache.exists()) {
            cartellaCache.mkdirs() // Crea la cartella se non esiste
        }

        val fileDestinazione = File(cartellaCache, nomeFileAsset)

        try {
            // Apri lo stream di input per il file negli assets
            context.assets.open(nomeFileAsset).use { inputStream ->
                // Apri lo stream di output per il file di destinazione
                FileOutputStream(fileDestinazione).use { outputStream ->
                    // Copia i dati
                    inputStream.copyTo(outputStream)
                }
            }

            // Una volta copiato il file, ottieni l'URI tramite il FileProvider
            val authority = "${context.packageName}.provider"
            return FileProvider.getUriForFile(context, authority, fileDestinazione)

        } catch (e: IOException) {
            Log.e("About", "Errore durante la copia del file dagli assets alla cache", e)
            return null // Restituisce null in caso di errore
        }
    }

}
