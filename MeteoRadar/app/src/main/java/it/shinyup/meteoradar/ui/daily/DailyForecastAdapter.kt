package it.shinyup.meteoradar.ui.daily

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import it.shinyup.meteoradar.R
import it.shinyup.meteoradar.data.models.DayForecastItem
import kotlin.math.roundToInt

class DailyForecastAdapter : RecyclerView.Adapter<DailyForecastAdapter.ViewHolder>() {

    private var items: List<DayForecastItem> = emptyList()

    fun submitList(list: List<DayForecastItem>) {
        items = list
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate:     TextView = view.findViewById(R.id.tvDayDate)
        val tvEmoji:    TextView = view.findViewById(R.id.tvDayEmoji)
        val tvMax:      TextView = view.findViewById(R.id.tvMaxTemp)
        val tvMaxArrow: TextView = view.findViewById(R.id.tvMaxArrow)
        val tvMin:      TextView = view.findViewById(R.id.tvMinTemp)
        val tvMinArrow: TextView = view.findViewById(R.id.tvMinArrow)
        val tvAvg:      TextView = view.findViewById(R.id.tvAvgRef)
        val tvProb:     TextView = view.findViewById(R.id.tvPrecipProb)
        val tvPrecip:   TextView = view.findViewById(R.id.tvPrecipSum)
        val tvReliab:   TextView = view.findViewById(R.id.tvReliability)
        val highlight:  View     = view.findViewById(R.id.todayHighlight)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_daily_forecast, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val d = items[position]

        holder.highlight.visibility = if (d.isToday) View.VISIBLE else View.GONE
        holder.tvDate.text  = d.dateLabel
        holder.tvEmoji.text = d.emoji

        holder.tvMax.text = "${d.temperatureMax.roundToInt()}°"
        holder.tvMin.text = "${d.temperatureMin.roundToInt()}°"

        holder.tvMaxArrow.text = trendArrow(d.maxTrend)
        holder.tvMaxArrow.setTextColor(trendColor(d.maxTrend))
        holder.tvMinArrow.text = trendArrow(d.minTrend)
        holder.tvMinArrow.setTextColor(trendColor(d.minTrend))

        val deltaMax = (d.temperatureMax - d.avgMax).toInt()
        val sign = if (deltaMax >= 0) "+" else ""
        val vsAvg = holder.itemView.context.getString(R.string.vs_weekly_avg)
        holder.tvAvg.text = "${sign}${deltaMax}° $vsAvg"
        holder.tvAvg.setTextColor(when {
            deltaMax >= 3  -> Color.parseColor("#F44336")
            deltaMax <= -3 -> Color.parseColor("#42A5F5")
            else           -> Color.parseColor("#8B949E")
        })

        // Precipitation probability colour-coded
        val prob = d.precipitationProbabilityMax
        holder.tvProb.text = "$prob%"
        holder.tvProb.setTextColor(when {
            prob >= 70 -> Color.parseColor("#F44336")
            prob >= 40 -> Color.parseColor("#FF9800")
            prob >= 20 -> Color.parseColor("#FFC107")
            else       -> Color.parseColor("#4CAF50")
        })

        if (d.precipitationSum > 0.1) {
            holder.tvPrecip.text = "${"%.1f".format(d.precipitationSum)} mm"
            holder.tvPrecip.visibility = View.VISIBLE
        } else {
            holder.tvPrecip.visibility = View.GONE
        }

        // ± = inter-model spread (GFS/ECMWF/ICON/ICON-EU/ICON-D2), ~ = empirical fallback
        val suffix = holder.itemView.context.getString(R.string.reliability_suffix)
        val reliabLabel = if (d.reliabilityFromModels) "±${d.reliabilityPct}% $suffix" else "~${d.reliabilityPct}% $suffix"
        holder.tvReliab.text = reliabLabel
        holder.tvReliab.setTextColor(when {
            d.reliabilityPct >= 85 -> Color.parseColor("#4CAF50")
            d.reliabilityPct >= 70 -> Color.parseColor("#FFC107")
            else                   -> Color.parseColor("#FF9800")
        })
    }

    private fun trendArrow(trend: Int): String = when (trend) {
        1  -> "↑"
        -1 -> "↓"
        else -> "─"
    }

    private fun trendColor(trend: Int): Int = when (trend) {
        1  -> Color.parseColor("#F44336")   // up = red/orange
        -1 -> Color.parseColor("#42A5F5")   // down = blue
        else -> Color.parseColor("#8B949E")
    }

    override fun getItemCount() = items.size
}
