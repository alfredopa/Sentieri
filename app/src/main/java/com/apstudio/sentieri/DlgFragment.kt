package com.apstudio.sentieri

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController

// Chide se calibrare il barometro oppure annullare l'uso
class DlgFragment : DialogFragment() {
    lateinit var onclickCallback : () -> Unit

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_dlg, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val okBtn = view.findViewById<Button>(R.id.dlgBtnOk)
        okBtn.setOnClickListener{
            val directions =  MappaFragmentDirections.actionMappaFragmentToBarometro()
            this@DlgFragment.findNavController().navigate(directions)
            dismiss()
        }
        val annullaBtn = view.findViewById<Button>(R.id.dlgBtnAnnulla)
        annullaBtn.setOnClickListener{
            onclickCallback()
            dismiss()
        }

    }

    @JvmName("setOnclickCallback1")
    fun setOnclickCallback(onclickCallback: () -> Unit) {
        this.onclickCallback = onclickCallback
    }

}