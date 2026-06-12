package it.shinyup.meteoradar.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forecast_snapshots")
data class ForecastSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fetchedAt: Long,
    val targetDate: String,
    val locationName: String,
    val minTemp: Double,
    val maxTemp: Double,
    val weatherCode: Int,
    val precipProb: Int,
    val precipSum: Double
)
