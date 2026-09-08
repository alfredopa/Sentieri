package com.apstudio.sentieri

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
            viewModel.syncWithNas()
        }

        viewModel.syncStatus.observe(viewLifecycleOwner) { status ->
            binding.tvStatus.text = status
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
}
