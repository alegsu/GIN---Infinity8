package it.shinyup.meteoradar.ui.radar

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import it.shinyup.meteoradar.R

data class HourForecastItem(
    val time: String,
    val description: String,
    val precipitation: Double,
    val precipitationProbability: Int,
    val cape: Double,
    val severityLabel: String,
    val severityColor: Int
)

class ForecastHourAdapter : RecyclerView.Adapter<ForecastHourAdapter.ViewHolder>() {

    private var items: List<HourForecastItem> = emptyList()

    fun submitList(list: List<HourForecastItem>) {
        items = list
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val strip: View = view.findViewById(R.id.severityStrip)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val tvSeverity: TextView = view.findViewById(R.id.tvSeverity)
        val tvMetrics: TextView = view.findViewById(R.id.tvMetrics)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_forecast_hour, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.strip.setBackgroundColor(item.severityColor)
        holder.tvTime.text = item.time
        holder.tvDescription.text = item.description
        holder.tvSeverity.text = item.severityLabel
        holder.tvSeverity.background = GradientDrawable().apply {
            setColor(item.severityColor)
            cornerRadius = 12f
        }
        val metrics = buildList {
            if (item.precipitation > 0.0) add("${String.format("%.1f", item.precipitation)}mm")
            if (item.precipitationProbability > 0) add("${item.precipitationProbability}%")
            if (item.cape > 100.0) add("CAPE ${item.cape.toInt()} J/kg")
        }
        holder.tvMetrics.text = metrics.joinToString(" · ")
        holder.tvMetrics.visibility = if (metrics.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun getItemCount() = items.size
}
