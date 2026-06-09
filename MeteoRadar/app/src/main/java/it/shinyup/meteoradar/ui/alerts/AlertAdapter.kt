package it.shinyup.meteoradar.ui.alerts

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.shinyup.meteoradar.data.models.AlertLevel
import it.shinyup.meteoradar.data.models.WeatherAlert
import it.shinyup.meteoradar.databinding.ItemAlertBinding
import java.text.SimpleDateFormat
import java.util.*

class AlertAdapter(
    private val onItemClick: (WeatherAlert) -> Unit
) : ListAdapter<WeatherAlert, AlertAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.ITALY)

    inner class ViewHolder(private val binding: ItemAlertBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(alert: WeatherAlert) {
            binding.tvAlertTitle.text = alert.title
            binding.tvAlertDesc.text = alert.description
            binding.tvAlertTime.text = dateFormat.format(Date(alert.timestamp))
            binding.tvAlertLevel.text = alert.level.label

            val levelColor = Color.parseColor(alert.level.colorHex)
            binding.viewLevelIndicator.setBackgroundColor(levelColor)
            binding.tvAlertLevel.setTextColor(levelColor)

            val alpha = if (alert.isRead) 0.6f else 1.0f
            binding.root.alpha = alpha

            binding.root.setOnClickListener { onItemClick(alert) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemAlertBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    private object DiffCallback : DiffUtil.ItemCallback<WeatherAlert>() {
        override fun areItemsTheSame(a: WeatherAlert, b: WeatherAlert) = a.id == b.id
        override fun areContentsTheSame(a: WeatherAlert, b: WeatherAlert) = a == b
    }
}
