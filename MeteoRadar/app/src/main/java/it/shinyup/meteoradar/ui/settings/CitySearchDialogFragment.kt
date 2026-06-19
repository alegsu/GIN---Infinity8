package it.shinyup.meteoradar.ui.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.shinyup.meteoradar.R
import it.shinyup.meteoradar.data.WeatherRepository
import it.shinyup.meteoradar.data.models.GeocodingResult
import it.shinyup.meteoradar.utils.Prefs
import kotlinx.coroutines.*

class CitySearchDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_SLOT_KEY = "slot_key"

        fun newInstance(slotKey: String): CitySearchDialogFragment {
            return CitySearchDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SLOT_KEY, slotKey)
                }
            }
        }
    }

    private val repository = WeatherRepository()
    private var searchJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var etSearch: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var rvResults: RecyclerView
    private lateinit var tvEmpty: TextView

    private val results = mutableListOf<GeocodingResult>()
    private lateinit var adapter: CityResultAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_city_search, container, false)

        etSearch = view.findViewById(R.id.etCitySearch)
        progressBar = view.findViewById(R.id.progressBar)
        rvResults = view.findViewById(R.id.rvResults)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        adapter = CityResultAdapter(results) { result ->
            saveCity(result)
        }
        rvResults.layoutManager = LinearLayoutManager(requireContext())
        rvResults.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 2) {
                    searchCities(query)
                } else {
                    results.clear()
                    adapter.notifyDataSetChanged()
                    tvEmpty.visibility = View.GONE
                }
            }
        })

        dialog?.setTitle(getString(R.string.city_search_title))

        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun searchCities(query: String) {
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(300) // debounce
            progressBar.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE

            try {
                val response = withContext(Dispatchers.IO) {
                    repository.searchCities(query)
                }
                results.clear()
                if (response != null) {
                    results.addAll(response)
                }
                adapter.notifyDataSetChanged()
                tvEmpty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    results.clear()
                    adapter.notifyDataSetChanged()
                    tvEmpty.visibility = View.VISIBLE
                }
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun saveCity(result: GeocodingResult) {
        val slotKey = arguments?.getString(ARG_SLOT_KEY) ?: "favorite_1"
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val nameKey: String
        val latKey: String
        val lonKey: String

        if (slotKey == "favorite_2") {
            nameKey = Prefs.FAVORITE_2_NAME
            latKey = Prefs.FAVORITE_2_LAT
            lonKey = Prefs.FAVORITE_2_LON
        } else {
            nameKey = Prefs.FAVORITE_1_NAME
            latKey = Prefs.FAVORITE_1_LAT
            lonKey = Prefs.FAVORITE_1_LON
        }

        prefs.edit()
            .putString(nameKey, result.name)
            .putString(latKey, result.latitude.toString())
            .putString(lonKey, result.longitude.toString())
            .apply()

        Toast.makeText(requireContext(), getString(R.string.city_saved, result.displayName()), Toast.LENGTH_SHORT).show()
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
    }

    private class CityResultAdapter(
        private val items: List<GeocodingResult>,
        private val onClick: (GeocodingResult) -> Unit
    ) : RecyclerView.Adapter<CityResultAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvCityName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_city_result, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvName.text = item.displayName()
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
