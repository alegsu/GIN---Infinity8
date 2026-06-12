package it.shinyup.meteoradar.ui.radar

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import it.shinyup.meteoradar.R

data class HourForecastItem(
    val time: String,
    val emoji: String,
    val description: String,
    val precipitation: Double,
    val precipitationProbability: Int,
    val cape: Double,
    val liftedIndex: Double,
    val windGusts: Double,
    val freezingLevel: Double,
    val score: Int,
    val severityLabel: String,
    val severityColor: Int
)

class ForecastHourAdapter : RecyclerView.Adapter<ForecastHourAdapter.ViewHolder>() {

    private var items: List<HourForecastItem> = emptyList()
    var showTechDetails: Boolean = false

    fun submitList(list: List<HourForecastItem>) {
        items = list
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val strip: View       = view.findViewById(R.id.severityStrip)
        val tvEmoji: TextView = view.findViewById(R.id.tvEmoji)
        val tvTime: TextView  = view.findViewById(R.id.tvTime)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val tvSeverity: TextView    = view.findViewById(R.id.tvSeverity)
        val scoreBar: ProgressBar   = view.findViewById(R.id.scoreBar)
        val tvScore: TextView       = view.findViewById(R.id.tvScore)
        val tvTech: TextView        = view.findViewById(R.id.tvTech)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_forecast_hour, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.strip.setBackgroundColor(item.severityColor)
        holder.tvEmoji.text = item.emoji
        holder.tvTime.text  = item.time
        holder.tvDescription.text = item.description

        holder.tvSeverity.text = item.severityLabel
        holder.tvSeverity.background = GradientDrawable().apply {
            setColor(item.severityColor)
            cornerRadius = 12f
        }

        holder.scoreBar.max      = 15
        holder.scoreBar.progress = item.score
        holder.scoreBar.progressTintList = ColorStateList.valueOf(item.severityColor)

        holder.tvScore.text = "${item.score}/15"

        if (showTechDetails) {
            val parts = mutableListOf<String>()
            if (item.cape > 200)          parts += "CAPE ${item.cape.toInt()} J/kg"
            if (item.liftedIndex < -1)    parts += "LI ${"%.1f".format(item.liftedIndex)}"
            if (item.freezingLevel in 500.0..2999.0) parts += "❄️ ${item.freezingLevel.toInt()}m"
            if (item.windGusts > 40)      parts += "💨 ${item.windGusts.toInt()} km/h"
            if (item.precipitation > 0.1) parts += "${String.format("%.1f", item.precipitation)}mm"
            if (item.precipitationProbability > 0) parts += "${item.precipitationProbability}%"
            holder.tvTech.text       = parts.joinToString(" · ")
            holder.tvTech.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
        } else {
            val parts = mutableListOf<String>()
            if (item.precipitation > 0.1)          parts += "${String.format("%.1f", item.precipitation)}mm"
            if (item.precipitationProbability > 0) parts += "${item.precipitationProbability}%"
            holder.tvTech.text       = parts.joinToString(" · ")
            holder.tvTech.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun getItemCount() = items.size
}
