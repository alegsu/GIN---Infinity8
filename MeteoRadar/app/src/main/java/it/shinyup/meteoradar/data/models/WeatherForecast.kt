package it.shinyup.meteoradar.data.models

import com.google.gson.annotations.SerializedName
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class OpenMeteoResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    @SerializedName("current_weather") val currentWeather: CurrentWeather?,
    val hourly: HourlyData?,
    val daily: DailyData?
)

data class CurrentWeather(
    val temperature: Double,
    val windspeed: Double,
    val winddirection: Double,
    val weathercode: Int,
    val time: String
)

data class HourlyData(
    val time: List<String>,
    val precipitation: List<Double>,
    @SerializedName("weathercode") val weatherCode: List<Int>,
    val cape: List<Double>,
    @SerializedName("precipitation_probability") val precipitationProbability: List<Int>,
    @SerializedName("lifted_index") val liftedIndex: List<Double>?,
    @SerializedName("windgusts_10m") val windGusts: List<Double>?,
    @SerializedName("freezing_level_height") val freezingLevelHeight: List<Double>?,
    val showers: List<Double>?
)

data class DailyData(
    val time: List<String>,
    @SerializedName("temperature_2m_max") val temperatureMax: List<Double>,
    @SerializedName("temperature_2m_min") val temperatureMin: List<Double>,
    @SerializedName("weathercode") val weatherCode: List<Int>,
    @SerializedName("precipitation_sum") val precipitationSum: List<Double>,
    @SerializedName("precipitation_probability_max") val precipitationProbabilityMax: List<Int>,
    @SerializedName("apparent_temperature_max") val apparentTemperatureMax: List<Double>?,
    @SerializedName("apparent_temperature_min") val apparentTemperatureMin: List<Double>?,
    @SerializedName("windspeed_10m_max") val windSpeedMax: List<Double>?,
    @SerializedName("relative_humidity_2m_max") val humidityMax: List<Int>?
)

/** Response from multi-model comparison endpoint */
data class ModelComparisonResponse(
    val daily: ModelComparisonDaily?
)

data class ModelComparisonDaily(
    val time: List<String>,
    @SerializedName("temperature_2m_max_gfs_seamless")    val maxGfs: List<Double>?,
    @SerializedName("temperature_2m_max_ecmwf_ifs025")    val maxEcmwf: List<Double>?,
    @SerializedName("temperature_2m_max_icon_seamless")   val maxIcon: List<Double>?
) {
    /** Returns reliability % for day [i] based on inter-model spread. */
    fun reliability(i: Int): Int {
        val values = listOfNotNull(
            maxGfs?.getOrNull(i),
            maxEcmwf?.getOrNull(i),
            maxIcon?.getOrNull(i)
        ).filterNot { it.isNaN() }
        if (values.size < 2) return -1 // unavailable
        val spread = values.max() - values.min()
        // 0°C spread = 100%, 8°C spread = 50%
        return maxOf(50, minOf(100, (100 - spread * 6.25).toInt()))
    }
}

/** UI model for one row in the 7-day tab */
data class DayForecastItem(
    val dateLabel: String,        // "Lun 10 giu"
    val isToday: Boolean,
    val emoji: String,
    val temperatureMax: Double,
    val temperatureMin: Double,
    val maxTrend: Int,            // -1 down, 0 same, +1 up vs previous day
    val minTrend: Int,
    val avgMax: Double,           // 7-day mean
    val avgMin: Double,
    val precipitationSum: Double,
    val precipitationProbabilityMax: Int,
    val reliabilityPct: Int,          // % from inter-model spread (real) or empirical decay (fallback)
    val reliabilityFromModels: Boolean // true = GFS/ECMWF/ICON spread; false = empirical
)

object WeatherCode {
    fun isThunderstorm(code: Int) = code >= 95
    fun hasHail(code: Int) = code == 96 || code == 99
    fun isHeavyHail(code: Int) = code == 99
    fun isHeavyRain(code: Int) = code in 65..67 || code == 82
    fun isRain(code: Int) = code in 51..82

    fun description(code: Int): String = when (code) {
        0 -> "Sereno"
        1, 2, 3 -> "Parzialmente nuvoloso"
        45, 48 -> "Nebbia"
        51, 53, 55 -> "Pioggerella"
        61, 63, 65 -> "Pioggia"
        71, 73, 75 -> "Neve"
        77 -> "Nevischio"
        80, 81, 82 -> "Rovesci di pioggia"
        85, 86 -> "Rovesci di neve"
        95 -> "Temporale"
        96 -> "Temporale con grandine lieve"
        99 -> "Temporale con grandine forte"
        else -> "Sconosciuto"
    }

    fun emoji(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55 -> "🌦️"
        61, 63, 65 -> "🌧️"
        71, 73, 75 -> "🌨️"
        77 -> "🌨️"
        80, 81, 82 -> "🌦️"
        85, 86 -> "❄️"
        95 -> "⛈️"
        96, 99 -> "⛈️"
        else -> "🌡️"
    }

    /**
     * Multi-source severity score (0–15):
     *  WMO code: 0–5 | CAPE: 0–3 | Lifted Index: 0–2 |
     *  Freezing level: 0–2 | Wind gusts: 0–1 | Showers: 0–1 | Heavy precip: 0–1
     *
     * Score thresholds: >=7 EXTREME, >=5 DANGER, >=3 WARNING, >=1 INFO
     */
    fun computeSeverityScore(
        code: Int,
        cape: Double,
        liftedIndex: Double,
        windGusts: Double,
        freezingLevel: Double,
        precip: Double,
        showers: Double,
        precipProb: Int
    ): Int {
        var score = 0

        score += when {
            code == 99 -> 5
            code == 96 -> 4
            code >= 95 -> 2
            else -> 0
        }

        score += when {
            cape > 3000 -> 3
            cape > 2000 -> 2
            cape > 1000 -> 1
            else -> 0
        }

        score += when {
            liftedIndex < -6.0 -> 2
            liftedIndex < -3.0 -> 1
            else -> 0
        }

        score += when {
            freezingLevel in 500.0..1999.9 -> 2
            freezingLevel in 2000.0..2499.9 -> 1
            else -> 0
        }

        if (windGusts > 90) score++
        if (showers > 5) score++
        if (precip > 10 && precipProb > 50) score++

        return score
    }
}
