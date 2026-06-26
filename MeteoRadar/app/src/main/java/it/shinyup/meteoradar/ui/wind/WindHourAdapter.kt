package it.shinyup.meteoradar.ui.wind

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import it.shinyup.meteoradar.R
import kotlin.math.roundToInt

data class WindHourItem(
    val time: String,
    val temperature: Double,
    val windSpeed: Double,
    val windDirection: Int,
    val windGusts: Double
)

class WindHourAdapter : RecyclerView.Adapter<WindHourAdapter.ViewHolder>() {

    private var items: List<WindHourItem> = emptyList()

    fun submitList(list: List<WindHourItem>) {
        items = list
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val strip: View = view.findViewById(R.id.windStrip)
        val tvTime: TextView = view.findViewById(R.id.tvWindTime)
        val tvTemp: TextView = view.findViewById(R.id.tvWindTemp)
        val tvSpeed: TextView = view.findViewById(R.id.tvWindSpeed)
        val tvDir: TextView = view.findViewById(R.id.tvWindDir)
        val tvGust: TextView = view.findViewById(R.id.tvWindGust)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wind_hour, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.tvTime.text = item.time
        holder.tvTemp.text = "${item.temperature.roundToInt()}°"
        holder.tvSpeed.text = "${item.windSpeed.roundToInt()} km/h"
        holder.tvDir.text = directionLabel(item.windDirection)
        holder.tvGust.text = "↟${item.windGusts.roundToInt()}"

        val color = speedColor(item.windSpeed)
        holder.strip.setBackgroundColor(color)
        holder.tvSpeed.setTextColor(color)
    }

    override fun getItemCount() = items.size

    private fun directionLabel(degrees: Int): String {
        val dirs = arrayOf("↓ N", "↙ NE", "← E", "↖ SE", "↑ S", "↗ SW", "→ W", "↘ NW")
        return dirs[((degrees + 22) % 360) / 45]
    }

    private fun speedColor(speed: Double): Int = when {
        speed >= 40 -> Color.parseColor("#F44336")
        speed >= 25 -> Color.parseColor("#FF9800")
        speed >= 15 -> Color.parseColor("#FFC107")
        else        -> Color.parseColor("#66BB6A")
    }
}
