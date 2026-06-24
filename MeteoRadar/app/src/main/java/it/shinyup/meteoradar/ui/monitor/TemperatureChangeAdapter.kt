package it.shinyup.meteoradar.ui.monitor

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import it.shinyup.meteoradar.R
import kotlin.math.abs
import kotlin.math.roundToInt

data class TemperatureChangeItem(
    val city: String,
    val date: String,
    val dateLabel: String,
    val oldMax: Double,
    val newMax: Double,
    val oldMin: Double,
    val newMin: Double,
    val maxDelta: Double,
    val minDelta: Double,
    val detectedAt: Long,
    val ageLabel: String
)

class TemperatureChangeAdapter : RecyclerView.Adapter<TemperatureChangeAdapter.ViewHolder>() {

    private var items: List<TemperatureChangeItem> = emptyList()

    fun submitList(list: List<TemperatureChangeItem>) {
        items = list
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCity: TextView = view.findViewById(R.id.tvChangeCity)
        val tvDate: TextView = view.findViewById(R.id.tvChangeDate)
        val tvMax: TextView = view.findViewById(R.id.tvMaxChange)
        val tvMin: TextView = view.findViewById(R.id.tvMinChange)
        val tvAge: TextView = view.findViewById(R.id.tvChangeAge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_temp_change, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.tvCity.text = "📍 ${item.city}"
        holder.tvDate.text = item.dateLabel

        holder.tvMax.text = formatDelta("Max", item.oldMax, item.newMax, item.maxDelta)
        holder.tvMax.setTextColor(deltaColor(item.maxDelta))

        holder.tvMin.text = formatDelta("Min", item.oldMin, item.newMin, item.minDelta)
        holder.tvMin.setTextColor(deltaColor(item.minDelta))

        holder.tvAge.text = "Rilevato: ${item.ageLabel}"
    }

    override fun getItemCount() = items.size

    private fun formatDelta(label: String, old: Double, new: Double, delta: Double): String {
        val arrow = if (delta > 0) "↑" else "↓"
        return "$label: ${old.roundToInt()}° → ${new.roundToInt()}° $arrow${"%+.1f".format(delta)}°"
    }

    private fun deltaColor(delta: Double): Int = when {
        abs(delta) >= 3.0 -> Color.parseColor("#F44336")
        abs(delta) >= 2.0 -> Color.parseColor("#FF9800")
        abs(delta) >= 1.0 -> Color.parseColor("#FFC107")
        else              -> Color.parseColor("#4CAF50")
    }
}
