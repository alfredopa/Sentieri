package com.apstudio.sentieri

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
            if (isNetworkAvailable()) {
                viewModel.syncWithNas()
            } else {
                Toast.makeText(requireContext(), "Connessione internet non disponibile", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.syncStatus.observe(viewLifecycleOwner) { status ->
            binding.tvStatus.text = status
        }

        viewModel.syncDetails.observe(viewLifecycleOwner) { details ->
            binding.tvSyncDetails.text = details
        }

        viewModel.isSyncing.observe(viewLifecycleOwner) { isSyncing ->
            binding.btnSync.isEnabled = !isSyncing
            binding.progressSync.visibility = if (isSyncing) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
}
