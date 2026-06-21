package it.shinyup.meteoradar.ui.analysis

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.shinyup.meteoradar.R
import kotlin.math.roundToInt

class PastWeatherAdapter : ListAdapter<PastDayItem, PastWeatherAdapter.VH>(DIFF) {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvDate: TextView     = v.findViewById(R.id.tvPastDate)
        val tvEmoji: TextView    = v.findViewById(R.id.tvPastEmoji)
        val tvDesc: TextView     = v.findViewById(R.id.tvPastDesc)
        val tvTemps: TextView    = v.findViewById(R.id.tvPastTemps)
        val tvForecast: TextView = v.findViewById(R.id.tvPastForecast)
        val tvAccuracy: TextView = v.findViewById(R.id.tvPastAccuracy)
        val tvPrecip: TextView   = v.findViewById(R.id.tvPastPrecip)
        val highlight: View      = v.findViewById(R.id.pastHighlight)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_past_weather, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val d = getItem(position)
        holder.tvDate.text  = d.dateLabel
        holder.tvEmoji.text = d.emoji
        holder.tvDesc.text  = d.description
        holder.tvTemps.text = "${d.tempMin.roundToInt()}° / ${d.tempMax.roundToInt()}°"

        if (d.forecastMax != null && d.forecastMin != null) {
            holder.tvForecast.text = "Prev: ${d.forecastMin.roundToInt()}° / ${d.forecastMax.roundToInt()}°"
            holder.tvForecast.visibility = View.VISIBLE
        } else {
            holder.tvForecast.visibility = View.GONE
        }

        if (d.accuracyPct != null) {
            holder.tvAccuracy.text = "Accuratezza: ${d.accuracyPct}%"
            holder.tvAccuracy.setTextColor(when {
                d.accuracyPct >= 90 -> Color.parseColor("#4CAF50")
                d.accuracyPct >= 75 -> Color.parseColor("#FFC107")
                d.accuracyPct >= 50 -> Color.parseColor("#FF9800")
                else -> Color.parseColor("#F44336")
            })
            holder.tvAccuracy.visibility = View.VISIBLE
        } else {
            holder.tvAccuracy.visibility = View.GONE
        }

        holder.tvPrecip.text = if (d.precipSum > 0.1) "${"%.1f".format(d.precipSum)} mm" else "–"
        holder.tvPrecip.setTextColor(if (d.precipSum > 0.1) Color.parseColor("#42A5F5") else Color.parseColor("#8B949E"))
        holder.highlight.visibility = if (d.isYesterday) View.VISIBLE else View.GONE
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<PastDayItem>() {
            override fun areItemsTheSame(a: PastDayItem, b: PastDayItem) = a.dateLabel == b.dateLabel
            override fun areContentsTheSame(a: PastDayItem, b: PastDayItem) = a == b
        }
    }
}
