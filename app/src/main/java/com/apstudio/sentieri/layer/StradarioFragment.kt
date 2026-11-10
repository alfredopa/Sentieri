package com.apstudio.sentieri.layer

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.apstudio.sentieri.GeoPackageUtils
import com.apstudio.sentieri.R
import com.apstudio.sentieri.SentieriFactory
import com.apstudio.sentieri.SentieriViewModel
import com.apstudio.sentieri.databinding.FragmentStradarioBinding
import com.apstudio.sentieri.db.LayerItem
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import mil.nga.geopackage.GeoPackage
import mil.nga.geopackage.features.user.FeatureDao
import mil.nga.sf.Geometry
import mil.nga.sf.GeometryCollection
import mil.nga.sf.LineString
import mil.nga.sf.MultiLineString
import mil.nga.sf.Point
import org.osmdroid.util.GeoPoint
import java.io.File

class StradarioFragment : Fragment(), StradarioRecyclerViewAdapter.OnItemClickListener {
    private var _binding: FragmentStradarioBinding? = null
    private val binding get() = _binding!!

    private var openedGeoPackage: GeoPackage? = null
    private var featureDao: FeatureDao? = null
    private lateinit var adapter: StradarioRecyclerViewAdapter

    private val viewModel: SentieriViewModel by activityViewModels {
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStradarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = StradarioRecyclerViewAdapter(this, emptyList())
        binding.rvStradeList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@StradarioFragment.adapter
        }

        apreStradarioGeoPackage()

        binding.btnCercaStrade.setOnClickListener {
            eseguiRicerca()
        }

        binding.etVia.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                eseguiRicerca()
                true
            } else {
                false
            }
        }
    }

    private fun apreStradarioGeoPackage() {
        if (openedGeoPackage != null && featureDao != null) return

        val baseDir = requireContext().externalMediaDirs.firstOrNull()
            ?: requireContext().getExternalFilesDir(null)
            ?: requireContext().filesDir

        val mappeDir = File(baseDir, "Mappe")
        var stradarioFile = File(mappeDir, "Stradario.gpkg")
        if (!stradarioFile.exists()) {
            stradarioFile = File(baseDir, "Stradario.gpkg")
        }

        if (!stradarioFile.exists()) {
            Toast.makeText(
                requireContext(),
                "File Stradario.gpkg non trovato nella cartella Mappe.",
                Toast.LENGTH_LONG
            ).show()
            Log.e("StradarioFragment", "File non trovato: ${stradarioFile.absolutePath}")
            return
        }

        try {
            val manager = GeoPackageUtils.getManager(requireContext())
            openedGeoPackage = manager.openExternal(stradarioFile)
            if (openedGeoPackage != null) {
                featureDao = openedGeoPackage?.getFeatureDao("Strade")
            } else {
                Log.e("StradarioFragment", "Impossibile aprire il GeoPackage Stradario.")
            }
        } catch (e: Exception) {
            Log.e("StradarioFragment", "Errore durante l'apertura del file Stradario.gpkg", e)
        }
    }

    private fun eseguiRicerca() {
        // Nascondi tastiera
        val imm = requireContext().getSystemService<InputMethodManager>()
        imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)

        val comuneQuery = binding.etComune.text?.toString()?.trim() ?: ""
        val viaQuery = binding.etVia.text?.toString()?.trim() ?: ""

        if (comuneQuery.isEmpty() && viaQuery.isEmpty()) {
            Toast.makeText(requireContext(), "Inserisci un comune o un nome di via per cercare", Toast.LENGTH_SHORT).show()
            return
        }

        if (featureDao == null) {
            apreStradarioGeoPackage()
            if (featureDao == null) {
                Toast.makeText(requireContext(), "Database Stradario non disponibile.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val condizioneWhere: String
        val argomentiWhere: Array<String>

        when {
            comuneQuery.isNotEmpty() && viaQuery.isNotEmpty() -> {
                condizioneWhere = "nome LIKE ? AND name LIKE ?"
                argomentiWhere = arrayOf("%$comuneQuery%", "%$viaQuery%")
            }
            comuneQuery.isNotEmpty() -> {
                condizioneWhere = "nome LIKE ?"
                argomentiWhere = arrayOf("%$comuneQuery%")
            }
            else -> {
                condizioneWhere = "name LIKE ?"
                argomentiWhere = arrayOf("%$viaQuery%")
            }
        }

        val mapStrade = LinkedHashMap<String, MutableList<List<GeoPoint>>>()
        val mapInfo = HashMap<String, Pair<String, String>>()

        try {
            val cursor = featureDao?.query(condizioneWhere, argomentiWhere)
            cursor?.use { c ->
                var rowCount = 0
                if (c.moveToFirst()) {
                    do {
                        val row = c.row
                        val nomeComune = row.getValue("nome")?.toString()?.trim() ?: ""
                        val nomeVia = row.getValue("name")?.toString()?.trim() ?: ""

                        val points = mutableListOf<GeoPoint>()
                        val geomData = row.geometry
                        if (geomData != null && !geomData.isEmpty) {
                            extractPoints(geomData.geometry, points)
                        }

                        if (points.isNotEmpty()) {
                            val key = "${nomeComune.uppercase()}_${nomeVia.uppercase()}"
                            if (!mapStrade.containsKey(key)) {
                                mapStrade[key] = mutableListOf()
                                mapInfo[key] = Pair(nomeComune, nomeVia)
                            }
                            mapStrade[key]?.add(points)
                            rowCount++
                        }
                    } while (c.moveToNext() && rowCount < 1000)
                }
            }
        } catch (e: Exception) {
            Log.e("StradarioFragment", "Errore nell'esecuzione della query stradario", e)
            Toast.makeText(requireContext(), "Errore nella ricerca: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        val risultati = mutableListOf<StradaData>()
        for ((key, segmentList) in mapStrade) {
            val info = mapInfo[key] ?: Pair("", "")
            risultati.add(
                StradaData(
                    nomeComune = info.first,
                    nomeVia = info.second,
                    highway = null,
                    segments = segmentList
                )
            )
        }

        adapter.updateData(risultati)
        binding.tvRisultatiInfo.visibility = View.VISIBLE
        binding.tvRisultatiInfo.text = getString(R.string.strade_risultati_count, risultati.size)
    }

    private fun extractPoints(geometry: Geometry?, points: MutableList<GeoPoint>) {
        if (geometry == null) return
        when (geometry) {
            is Point -> {
                points.add(GeoPoint(geometry.y, geometry.x))
            }
            is LineString -> {
                geometry.points.forEach { pt ->
                    points.add(GeoPoint(pt.y, pt.x))
                }
            }
            is MultiLineString -> {
                geometry.lineStrings.forEach { ls ->
                    ls.points.forEach { pt ->
                        points.add(GeoPoint(pt.y, pt.x))
                    }
                }
            }
            is GeometryCollection<*> -> {
                geometry.geometries.forEach { childGeom ->
                    extractPoints(childGeom, points)
                }
            }
        }
    }

    override fun onItemClick(strada: StradaData) {
        if (strada.segments.isEmpty()) return

        val label = if (strada.nomeVia.isNotBlank()) "${strada.nomeVia} (${strada.nomeComune})" else strada.nomeComune

        // Rimuovi precedenti tracciati di strade caricate
        viewModel.layerItems.removeAll { it.isStreet || it.nome.startsWith("Strada:") }

        // Crea un UNICO LayerItem contenente tutti i segmenti della strada
        val stradaLayerItem = LayerItem(
            nome = "Strada: $label",
            abilitato = true,
            direzione = false,
            segui = false,
            distanza = 0f,
            ascesa = 0,
            discesa = 0,
            mostraPendenza = false,
            isStreet = true,
            punti = strada.segments.firstOrNull() ?: emptyList(),
            multiSegments = strada.segments
        )
        viewModel.layerItems.add(stradaLayerItem)

        val allPoints = strada.segments.flatten()
        if (allPoints.isNotEmpty()) {
            val targetPoint = allPoints[allPoints.size / 2]
            val bundle = Bundle().apply {
                putDouble("latitude", targetPoint.latitude)
                putDouble("longitude", targetPoint.longitude)
            }

            findNavController().navigate(R.id.action_stradarioFragment_to_mappaFragment, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        try {
            openedGeoPackage?.close()
        } catch (e: Exception) {
            Log.e("StradarioFragment", "Errore chiusura GeoPackage Stradario: ${e.message}", e)
        }
        openedGeoPackage = null
        featureDao = null
    }
}
