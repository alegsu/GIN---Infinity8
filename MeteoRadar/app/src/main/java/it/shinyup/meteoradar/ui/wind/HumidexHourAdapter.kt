package it.shinyup.meteoradar.ui.wind

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import it.shinyup.meteoradar.R
import it.shinyup.meteoradar.utils.HumidexUtil
import kotlin.math.roundToInt

data class HumidexHourItem(
    val time: String,
    val temperature: Double,
    val humidity: Int,
    val humidex: Double,
    val isCurrentHour: Boolean = false
)

class HumidexHourAdapter : RecyclerView.Adapter<HumidexHourAdapter.ViewHolder>() {

    private var items: List<HumidexHourItem> = emptyList()

    fun submitList(list: List<HumidexHourItem>) {
        items = list
        notifyDataSetChanged()
    }

    fun currentHourPosition(): Int = items.indexOfFirst { it.isCurrentHour }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val strip: View = view.findViewById(R.id.hxStrip)
        val tvTime: TextView = view.findViewById(R.id.tvHxTime)
        val tvTemp: TextView = view.findViewById(R.id.tvHxTemp)
        val tvHum: TextView = view.findViewById(R.id.tvHxHum)
        val tvHumidex: TextView = view.findViewById(R.id.tvHxValue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_humidex_hour, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val color = HumidexUtil.color(item.humidex)

        holder.tvTemp.text = "${item.temperature.roundToInt()}°"
        holder.tvHum.text = "${item.humidity}%"
        holder.tvHumidex.text = "${item.humidex.roundToInt()}"
        holder.tvHumidex.setTextColor(color)
        holder.strip.setBackgroundColor(color)

        if (item.isCurrentHour) {
            holder.itemView.setBackgroundColor(Color.parseColor("#1A42A5F5"))
            holder.tvTime.setTextColor(Color.parseColor("#42A5F5"))
            holder.tvTime.text = "▸ ${item.time}"
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            holder.tvTime.setTextColor(Color.parseColor("#8B949E"))
            holder.tvTime.text = item.time
        }
    }

    override fun getItemCount() = items.size
}
