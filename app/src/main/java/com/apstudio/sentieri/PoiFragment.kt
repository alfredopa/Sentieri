package com.apstudio.sentieri

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat.getColor
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.db.OnItemClickListener
import com.apstudio.sentieri.db.SentieriRepo
import org.osmdroid.util.GeoPoint


class PoiFragment : Fragment() {
    private lateinit var viewModel: SentieriViewModel
    private lateinit var recyclerPoi: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity().applicationContext as AppSentieri).get(SentieriViewModel::class.java)
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
        if (viewModel.fotoList.size > 0) {
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
        if (viewModel.wayPoint.size == 0) {
            // nessun waypoint
            val toast = Toast.makeText(requireActivity(), "Nessun waypoint presente!", Toast.LENGTH_LONG)
            toast.show()
            toast.view?.setBackgroundColor(getColor(requireActivity(), R.color.purple_500))
        }
        else
            displayPoiList()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun displayPoiList() {
        // Versione DB
        /*poiDao.livePoiDB().observe(viewLifecycleOwner, Observer { poiDao ->
            val adapter = PoiAdapter(poiDao)
            recyclerPoi.adapter = adapter
        })*/
        val wayPoints = viewModel.wayPoint
        val adapter = PoiAdapter(wayPoints)
        recyclerPoi.adapter = adapter
        adapter.setOnItemClickListener(object : OnItemClickListener {
            override fun onItemClick(position: Int) {
                val destPoi : GeoPoint
                destPoi = if (wayPoints[position].elevation == null)
                    GeoPoint(wayPoints[position].latitude, wayPoints[position].longitude)
                else
                    GeoPoint(wayPoints[position].latitude, wayPoints[position].longitude, wayPoints[position].elevation!!)

                viewModel.poi = destPoi
                val directions = PoiFragmentDirections.actionPoiFragmentToMappaFragment()
                findNavController().navigate(directions)
                //Log.d("LayerDialog", "clicked position: $position")
            }
        })
    }
}