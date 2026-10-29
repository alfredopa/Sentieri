package com.apstudio.sentieri

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import com.apstudio.sentieri.databinding.FragmentSincronizzaBinding
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo

class SincronizzaFragment : Fragment() {

    private var _binding: FragmentSincronizzaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SentieriViewModel by activityViewModels(
        factoryProducer = {
            val application = requireActivity().application
            val database = SentieriDB.getInstance(application)
            val repository = SentieriRepo(
                sentieriDao = database.sentieriDao(),
                trackDao = database.trackDao(),
                poiDao = database.poiDao(),
                fotoPoiDao = database.fotoPoiDao()
            )
            SentieriFactory(repository, application)
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSincronizzaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSync.setOnClickListener {
            if (NetworkUtils.isNetworkAvailable(requireContext())) {
                viewModel.syncWithNas()
            } else {
                Toast.makeText(requireContext(), "Connessione internet non disponibile (nessuna rete dati o Wi-Fi)", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnDownloadDb.setOnClickListener {
            if (!NetworkUtils.isNetworkAvailable(requireContext())) {
                Toast.makeText(requireContext(), "Connessione internet non disponibile", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.scarica_db_nas)
                .setMessage(R.string.scarica_db_conferma)
                .setPositiveButton("Scarica") { _, _ ->
                    viewModel.scaricaDatabaseCompleto()
                }
                .setNegativeButton("Annulla", null)
                .show()
        }

        viewModel.syncStatus.observe(viewLifecycleOwner) { status ->
            binding.tvStatus.text = status
        }

        viewModel.syncDetails.observe(viewLifecycleOwner) { details ->
            binding.tvSyncDetails.text = details
            binding.scrollSyncDetails.post {
                binding.scrollSyncDetails.fullScroll(View.FOCUS_DOWN)
            }
        }

        viewModel.isSyncing.observe(viewLifecycleOwner) { isSyncing ->
            binding.btnSync.isEnabled = !isSyncing
            binding.btnDownloadDb.isEnabled = !isSyncing
            binding.progressSync.visibility = if (isSyncing) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
