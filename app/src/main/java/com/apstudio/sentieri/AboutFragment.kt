// in ui/AboutFragment.kt
package com.apstudio.sentieri // Assicurati che il package sia corretto

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.apstudio.sentieri.databinding.FragmentAboutBinding

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
        val appVersionName = BuildConfig.VERSION_NAME
        binding.tvAppNameVersion.text = "Sentieri v$appVersionName"

        // Qui potresti impostare altro testo dinamicamente se necessario,
        // ad esempio caricandolo da una risorsa stringa.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Pulisci il riferimento al binding per evitare memory leak
        _binding = null
    }
}
