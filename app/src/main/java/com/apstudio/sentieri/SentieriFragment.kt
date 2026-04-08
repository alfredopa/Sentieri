package com.apstudio.sentieri

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.apstudio.sentieri.SentieriFragmentDirections.Companion.actionSentieriFragmentToSchedaFragment
import com.apstudio.sentieri.databinding.FragmentSentieriBinding
import com.apstudio.sentieri.db.Sentieri
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import kotlin.getValue

class SentieriFragment : Fragment() {
    private val viewModel: SentieriViewModel by activityViewModels {
        val application = requireActivity().application
        // 1. Ottieni una singola istanza del database
        val database = SentieriDB.getInstance(application)
        // 2. Crea il repository passando TUTTI i DAO richiesti
        val repository = SentieriRepo(
            sentieriDao = database.sentieriDao(),
            trackDao = database.trackDao(),
            poiDao = database.poiDao(),
            fotoPoiDao = database.fotoPoiDao()
        )
        // 3. Crea la factory con il repository e l'applicazione
        SentieriFactory(repository, application)
    }
    private lateinit var binding: FragmentSentieriBinding
    private lateinit var adapter: MyRecyclerViewAdapter
    //private lateinit var searchView: SearchView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSentieriBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.myViewModel = viewModel

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items
                menuInflater.inflate(R.menu.cerca_sentiero, menu)

                val search = menu.findItem(R.id.menu_search)
                val searchView = search.actionView as SearchView
                searchView.isSubmitButtonEnabled = true
                // Imposta la query iniziale se è già presente nel ViewModel
                if (viewModel.ricerca.isNotEmpty()) {
                    search.expandActionView() // Espandi la search view
                    searchView.setQuery(viewModel.ricerca, false) // Imposta il testo senza eseguire la ricerca
                    searchView.clearFocus() // Togli il focus per non far apparire la tastiera
                }

                searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        if (query != null) {
                            viewModel.ricerca = query
                            cercaNome(query)
                        }
                        searchView.clearFocus() // Nascondi la tastiera dopo l'invio
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        val query = newText.orEmpty()
                        viewModel.ricerca = query
                        cercaNome(query)

                        // --- LOGICA CHIAVE PER L'ANNULLAMENTO ---
                        // Se la query diventa vuota (perché l'utente ha premuto 'X' o cancellato tutto)
                        if (query.isEmpty()) {
                            // Forziamo la chiusura della SearchView e ripristiniamo la lista completa
                            searchView.isIconified = true // Collassa la SearchView (la "iconifica")
                            search.collapseActionView()   // Assicura che l'action view si chiuda
                            displaySentieriList()         // Ricarica la lista originale
                            (activity as? AppCompatActivity)?.supportActionBar?.title = "Elenco sentieri"
                        }
                        return true
                    }
                })

                searchView.setOnCloseListener {
                    // 1. Pulisci la variabile di stato della ricerca
                    viewModel.ricerca = ""
                    // 2. Richiama la funzione che carica la lista completa e ordinata
                    displaySentieriList()
                    // 3. Resetta il titolo dell'ActionBar (opzionale ma consigliato)
                    (activity as? AppCompatActivity)?.supportActionBar?.title = "Elenco sentieri"
                    true // Indica che hai gestito l'evento
                }

            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
                return when (menuItem.itemId) {
                    R.id.menu_search -> {
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        initRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.ricerca.isNotEmpty()) {
            cercaNome(viewModel.ricerca)
        } else {
            displaySentieriList()
        }
    }

    private fun initRecyclerView() {
        binding.sentieriRecyclerView.layoutManager = LinearLayoutManager(context)
        adapter = MyRecyclerViewAdapter { selectedItem: Sentieri -> listItemClicked(selectedItem) }
        binding.sentieriRecyclerView.adapter = adapter
        binding.sentieriRecyclerView.addItemDecoration(
            DividerItemDecoration(
                activity,
                LinearLayoutManager.VERTICAL
            )
        )
        if (viewModel.ricerca.isNotEmpty()) {
            (activity as AppCompatActivity).supportActionBar?.title = "filtro: " + viewModel.ricerca
            cercaNome(viewModel.ricerca)
        }
    }

    private fun displaySentieriList() {
        viewModel.getSavedSentieri().observe(viewLifecycleOwner) {
            adapter.setData(it)
            adapter.notifyItemRangeChanged(0, it.size)
        }
    }

    private fun listItemClicked(sentieri: Sentieri) {
        // implementa click passa il valore del TrackId del record cliccato
        val direction = actionSentieriFragmentToSchedaFragment(sentieri.id)
        findNavController().navigate(direction)
    }

    private fun cercaNome(query: String) {
        val searchQuery = "%$query%"
        viewModel.cercaNome(searchQuery).observe(viewLifecycleOwner) { list ->
            adapter.setData(list)
            adapter.notifyItemRangeChanged(0, list.size)
        }
    }
}



