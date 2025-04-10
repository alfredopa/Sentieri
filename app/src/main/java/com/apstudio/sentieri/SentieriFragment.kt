package com.apstudio.sentieri

// con widget da androidx restituisce errore in passaggio a scheda dettaglio
// cambiare anche in xml
//import androidx.appcompat.widget.SearchView
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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.apstudio.sentieri.SentieriFragmentDirections.Companion.actionSentieriFragmentToSchedaFragment
import com.apstudio.sentieri.databinding.FragmentSentieriBinding
import com.apstudio.sentieri.db.Sentieri
import com.apstudio.sentieri.db.SentieriRepo

class SentieriFragment : Fragment() {
    private val viewModel: SentieriViewModel by activityViewModels {
        SentieriFactory(
            SentieriRepo(
                requireActivity()
            )
        )
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
                searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        //if (query != null) {
                        viewModel.ricerca = query!!
                        cercaNome(query)
                        //}
                        return true
                    }

                    override fun onQueryTextChange(query: String?): Boolean {
                        //if (query != null) {
                        viewModel.ricerca = query!!
                        cercaNome(query)
                        //}
                        return true
                    }
                })

                searchView.setOnCloseListener {
                    viewModel.ricerca = ""
                    cercaNome(viewModel.ricerca)
                    true
                }

            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
                return when (menuItem.itemId) {
                    R.id.menu_search -> {
                        // clearCompletedTasks()
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
        binding.sentieriRecyclerView.addItemDecoration(
            DividerItemDecoration(
                activity,
                LinearLayoutManager.HORIZONTAL
            )
        )
        if (viewModel.ricerca.isNotEmpty()) {
            (activity as AppCompatActivity).supportActionBar?.title = "filtro: " + viewModel.ricerca
            cercaNome(viewModel.ricerca)
            // val actionRestart = menu?.findItem(R.id.menu_search)
            // onOptionsItemSelected(actionRestart)
        } //else {
          //  displaySentieriList()
        //}
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



