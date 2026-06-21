package it.shinyup.meteoradar.ui.history

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.shinyup.meteoradar.R
import it.shinyup.meteoradar.data.models.WeatherCode
import kotlin.math.roundToInt

class HistoryAdapter : ListAdapter<DayHistory, HistoryAdapter.VH>(DIFF) {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvDate: TextView = v.findViewById(R.id.tvHistoryDate)
        val tvLatest: TextView = v.findViewById(R.id.tvHistoryLatest)
        val tvPrev: TextView = v.findViewById(R.id.tvHistoryPrev)
        val tvChange: TextView = v.findViewById(R.id.tvHistoryChange)
        val tvReadings: TextView = v.findViewById(R.id.tvHistoryReadings)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_history_day, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.tvDate.text = item.label

        val latest = item.snapshots.last()
        val prev = item.snapshots[item.snapshots.size - 2]

        val latestEmoji = WeatherCode.emoji(latest.weatherCode)
        val latestLoc = if (latest.locationName.isNotBlank()) " · 📍${latest.locationName}" else ""
        holder.tvLatest.text = "Ora: $latestEmoji  ${latest.minTemp.roundToInt()}° / ${latest.maxTemp.roundToInt()}°  💧${latest.precipProb}%$latestLoc"

        val prevTime = formatTime(prev.fetchedAt)
        val prevLoc = if (prev.locationName.isNotBlank() && prev.locationName != latest.locationName) " · 📍${prev.locationName}" else ""
        holder.tvPrev.text = "$prevTime: ${WeatherCode.emoji(prev.weatherCode)}  ${prev.minTemp.roundToInt()}° / ${prev.maxTemp.roundToInt()}°$prevLoc"

        val deltaMax = latest.maxTemp - prev.maxTemp
        when {
            deltaMax >= 1.0 -> {
                holder.tvChange.text = "Max ↑${deltaMax.roundToInt()}° rispetto a $prevTime"
                holder.tvChange.setTextColor(Color.parseColor("#F44336"))
            }
            deltaMax <= -1.0 -> {
                holder.tvChange.text = "Max ↓${(-deltaMax).roundToInt()}° rispetto a $prevTime"
                holder.tvChange.setTextColor(Color.parseColor("#42A5F5"))
            }
            else -> {
                holder.tvChange.text = "Stabile rispetto a $prevTime"
                holder.tvChange.setTextColor(Color.parseColor("#8B949E"))
            }
        }

        holder.tvReadings.text = "${item.snapshots.size} rilevazioni"
    }

    private fun formatTime(ms: Long): String {
        val now = System.currentTimeMillis()
        val diffH = (now - ms) / (60 * 60 * 1000L)
        return when {
            diffH < 1  -> "< 1h fa"
            diffH < 24 -> "${diffH}h fa"
            else -> "${diffH / 24}g fa"
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<DayHistory>() {
            override fun areItemsTheSame(a: DayHistory, b: DayHistory) = a.date == b.date
            override fun areContentsTheSame(a: DayHistory, b: DayHistory) = a == b
        }
    }
}
