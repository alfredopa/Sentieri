package com.apstudio.sentieri.Levo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.apstudio.sentieri.SentieriViewModel
import com.example.levo_sdk.domain.model.BtMessage

class BikeFragment : Fragment() {

    private val viewModel: SentieriViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val ebikeMsg by viewModel.ebikeMessage.observeAsState(BtMessage())
                val isConnected by viewModel.isConnected.observeAsState(false)
                
                BikeScreen(
                    ebikeMessage = ebikeMsg,
                    isConnected = isConnected,
                    onBack = { findNavController().popBackStack() }
                )
            }
        }
    }
}
