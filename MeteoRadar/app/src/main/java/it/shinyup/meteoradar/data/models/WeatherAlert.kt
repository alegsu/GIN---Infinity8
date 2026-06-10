package it.shinyup.meteoradar.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AlertLevel(val label: String, val colorHex: String) {
    INFO("Informazione", "#2196F3"),
    WARNING("Attenzione", "#FF9800"),
    DANGER("Pericolo", "#F44336"),
    EXTREME("Estremo", "#9C27B0")
}

enum class AlertType(val label: String) {
    THUNDERSTORM("Temporale"),
    HEAVY_RAIN("Pioggia intensa"),
    HAIL("Grandine"),
    HEAVY_HAIL("Grandine forte"),
    HAIL_FORECAST("Previsione grandine"),
    STRONG_WIND("Vento forte")
}

@Entity(tableName = "alerts")
data class WeatherAlert(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: AlertType,
    val level: AlertLevel,
    val title: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val precipitation: Double = 0.0,
    val cape: Double = 0.0,
    val weatherCode: Int = 0,
    val isRead: Boolean = false
)
