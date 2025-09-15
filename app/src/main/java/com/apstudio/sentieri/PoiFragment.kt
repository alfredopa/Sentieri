package com.apstudio.sentieri

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getColor
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.db.OnItemClickListener
import com.apstudio.sentieri.db.PoiDB
import com.apstudio.sentieri.db.SentieriRepo
import net.federicomatera.agpxp.models.WayPoint
import org.osmdroid.util.GeoPoint
import java.util.Date
import kotlin.Double


class PoiFragment : Fragment() {
    private lateinit var viewModel: SentieriViewModel
    private lateinit var recyclerPoi: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity().applicationContext as AppSentieri)[SentieriViewModel::class.java]
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_poi, container, false)

        /*// Get the DCIM directory
        val dcimDirectory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        // Get the Sentieri directory
        val fotoDirectory = File(dcimDirectory, "Sentieri")
        if (viewModel.fotoList.size > 0) {
            viewModel.fotoList.forEach {
                val file = File(it)
                file.delete()
            }
        }*/
        if (viewModel.fotoList.isNotEmpty()) {
            // Create a recyclerPhoto  object and set the adapter
            val recyclerPhoto = view.findViewById<RecyclerView>(R.id.rv_photo)
            recyclerPhoto.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            val fotoAdapter = ImageAdapter(viewModel.fotoList)
            recyclerPhoto.adapter = fotoAdapter
            fotoAdapter.setOnItemClickListener(object : OnItemClickListener {
                    override fun onItemClick(position: Int) {
                        //Log.d("ImageAdapter", "clicked position: $position")
                        val uri = viewModel.fotoList[position]
                        val directions = PoiFragmentDirections.actionPoiFragmentToCameraFragment(uri.toString())
                        findNavController().navigate(directions)
                    }
            })
        }

//       imposta recyclerPoi per i Waypoint
        recyclerPoi = view.findViewById(R.id.rv_poi)
        recyclerPoi.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Chiama displayPoiList, che ora costruirà la lista combinata
        // e gestirà il caso in cui la lista combinata sia vuota.
        displayPoiList()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun displayPoiList() {
        val waypointsToShow = mutableListOf<net.federicomatera.agpxp.models.WayPoint>()

        // 1. Aggiungi i waypoint preesistenti da viewModel.wayPoint
        // Questi sono già nel formato corretto (net.federicomatera.agpxp.models.WayPoint)
        waypointsToShow.addAll(viewModel.wayPoint)

        // 2. Converti e aggiungi i nuovi waypoint da viewModel.poiDBList
        viewModel.poiDBList.forEach { poiFromDb ->
            // Converti l'oggetto PoiDB in un oggetto net.federicomatera.agpxp.models.WayPoint
            // Assicurati che il mapping dei campi sia corretto!
            val newWayPointEntry = net.federicomatera.agpxp.models.WayPoint(
                latitude = poiFromDb.Latit.toDouble(),
                longitude = poiFromDb.Longit.toDouble(),
                elevation = poiFromDb.Ele.toDouble(), // o poiFromDb.Ele?.toDouble() se Ele è nullable
                name = poiFromDb.NomePOI,
                description = poiFromDb.DescrPOI, // o comment = poiFromDb.DescrPOI
                src = poiFromDb.UriPath // Mappa UriPath al campo corretto in WayPoint (es. source)
                // time = ... // Converti poiFromDb.Time (String) in Date se necessario per il costruttore di WayPoint
            )
            waypointsToShow.add(newWayPointEntry)
        }

        if (waypointsToShow.isEmpty()) {
            val toast = Toast.makeText(requireActivity(), "Nessun waypoint da visualizzare!", Toast.LENGTH_LONG)
            toast.show()
            toast.view?.setBackgroundColor(ContextCompat.getColor(requireActivity(), R.color.purple_500))
            recyclerPoi.adapter = null // Pulisci l'adapter
            return
        }

        val adapter = PoiAdapter(waypointsToShow) // Usa la lista combinata
        recyclerPoi.adapter = adapter
        adapter.setOnItemClickListener(object : OnItemClickListener {
            override fun onItemClick(position: Int) {
                if (position < 0 || position >= waypointsToShow.size) return // Controllo di sicurezza
                val clickedWayPoint = waypointsToShow[position]
                val altitudine = clickedWayPoint.elevation?: 0.0 // Gestisci elevation nullabile
                val destPoi = GeoPoint(
                    clickedWayPoint.latitude,
                    clickedWayPoint.longitude,
                    altitudine
                )

                viewModel.poi = destPoi // viewModel.poi è un GeoPoint, quindi questo è corretto
                val directions = PoiFragmentDirections.actionPoiFragmentToMappaFragment()
                findNavController().navigate(directions)
            }
        })
        // Non è necessario adapter.notifyDataSetChanged() qui perché l'adapter viene sempre ricreato.
    }

}