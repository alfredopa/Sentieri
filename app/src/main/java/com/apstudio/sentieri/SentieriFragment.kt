package com.apstudio.sentieri

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.apstudio.sentieri.SentieriFragmentDirections.Companion.actionSentieriFragmentToSchedaFragment
import com.apstudio.sentieri.databinding.FragmentSentieriBinding
import com.apstudio.sentieri.db.Sentieri
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import com.applandeo.materialcalendarview.CalendarDay
import com.applandeo.materialcalendarview.EventDay
import com.applandeo.materialcalendarview.listeners.OnDayClickListener
import java.util.Calendar
import java.util.Locale

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
                searchView.setIconifiedByDefault(false)
                searchView.queryHint = "Ricerca sentiero"

                // Nascondi il pulsante di chiusura (X) per forzare l'uso del tasto indietro
                val closeButtonId = searchView.context.resources.getIdentifier("android:id/search_close_btn", null, null)
                val closeButton = searchView.findViewById<ImageView>(closeButtonId)
                closeButton?.visibility = View.GONE
                closeButton?.setImageDrawable(null)

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
                        
                        // Se la query è vuota, mostriamo comunque tutti i sentieri
                        if (query.isEmpty()) {
                            if (viewModel.isCalendarMode.value != true) {
                                displaySentieriList()
                            }
                            (activity as? AppCompatActivity)?.supportActionBar?.title = "Elenco sentieri"
                        } else {
                            (activity as? AppCompatActivity)?.supportActionBar?.title = "Filtro: $query"
                        }

                        // Forza la scomparsa del pulsante X che SearchView tenta di mostrare
                        closeButton?.visibility = View.GONE
                        
                        return true
                    }
                })

                search.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                    override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                        // Se attiviamo la ricerca, chiudiamo il calendario
                        if (viewModel.isCalendarMode.value == true) {
                            viewModel.setCalendarMode(false)
                        }
                        return true
                    }

                    override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                        // Reset totale quando si chiude la barra di ricerca
                        viewModel.ricerca = ""
                        if (viewModel.isCalendarMode.value != true) {
                            displaySentieriList()
                        } else {
                            viewModel.selectedDate?.let { cercaPerData(it) }
                        }
                        (activity as? AppCompatActivity)?.supportActionBar?.title = "Elenco sentieri"
                        return true
                    }
                })

                searchView.setOnCloseListener {
                    // Ritorna true per impedire alla SearchView di chiudersi internamente (iconify)
                    // La chiusura è delegata al tasto indietro della toolbar (CollapseActionView)
                    true 
                }

            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
                return when (menuItem.itemId) {
                    R.id.menu_search -> {
                        true
                    }
                    R.id.menu_calendar -> {
                        val current = viewModel.isCalendarMode.value ?: false
                        viewModel.setCalendarMode(!current)
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

        binding.calendarViewSentieri.setOnDayClickListener(object : OnDayClickListener {
            override fun onDayClick(eventDay: EventDay) {
                val calendar = eventDay.calendar
                val date = formatDate(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                )
                viewModel.selectedDate = date
                cercaPerData(date)
            }
        })

        // Osserva le date con registrazioni per evidenziarle
        viewModel.getGiorniConRegistrazioni().observe(viewLifecycleOwner) { dateList ->
            val calendarDays = mutableListOf<CalendarDay>()
            dateList.forEach { dateStr ->
                try {
                    val parts = dateStr.split("-")
                    val cal = Calendar.getInstance()
                    cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                    
                    val calendarDay = CalendarDay(cal).apply {
                        backgroundResource = R.drawable.ic_day_highlight
                        labelColor = android.R.color.white
                        // Mantiene il cerchio rosso anche quando il giorno è selezionato
                        selectedBackgroundResource = R.drawable.ic_day_highlight
                        selectedLabelColor = android.R.color.white
                    }
                    calendarDays.add(calendarDay)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            binding.calendarViewSentieri.setCalendarDays(calendarDays)
        }

        // Osserva lo stato del calendario per gestire la visibilità
        viewModel.isCalendarMode.observe(viewLifecycleOwner) { isVisible ->
            toggleCalendarVisibility(isVisible)
        }

        // Ripristina la data graficamente se siamo in modalità calendario
        viewModel.selectedDate?.let { dateStr ->
            try {
                val parts = dateStr.split("-")
                val cal = Calendar.getInstance()
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                binding.calendarViewSentieri.setDate(cal)
                binding.calendarViewSentieri.selectedDates = listOf(cal)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun toggleCalendarVisibility(visible: Boolean) {
        if (visible) {
            binding.containerCalendar.visibility = View.VISIBLE
            // Se il calendario è visibile e abbiamo una data salvata, cerchiamo per quella
            val date = viewModel.selectedDate ?: run {
                val calendar = Calendar.getInstance()
                val today = formatDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
                viewModel.selectedDate = today
                today
            }
            
            // Sincronizza anche la selezione grafica nel calendario
            try {
                val parts = date.split("-")
                val cal = Calendar.getInstance()
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                binding.calendarViewSentieri.setDate(cal)
                binding.calendarViewSentieri.selectedDates = listOf(cal)
            } catch (_: Exception) {}

            cercaPerData(date)
        } else {
            binding.containerCalendar.visibility = View.GONE
            binding.tvNoDataSentieri.visibility = View.GONE
            if (viewModel.ricerca.isEmpty()) {
                displaySentieriList()
            } else {
                cercaNome(viewModel.ricerca)
            }
        }
    }

    private fun cercaPerData(date: String) {
        val dateQuery = "$date%"
        viewModel.getSentieriPerData(dateQuery).observe(viewLifecycleOwner) { list ->
            adapter.setData(list)
            if (list.isEmpty()) {
                binding.tvNoDataSentieri.visibility = View.VISIBLE
            } else {
                binding.tvNoDataSentieri.visibility = View.GONE
            }
        }
    }

    private fun formatDate(year: Int, month: Int, day: Int): String {
        return String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day)
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.ricerca.isNotEmpty()) {
            cercaNome(viewModel.ricerca)
        } else if (viewModel.isCalendarMode.value == true && viewModel.selectedDate != null) {
            cercaPerData(viewModel.selectedDate!!)
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



