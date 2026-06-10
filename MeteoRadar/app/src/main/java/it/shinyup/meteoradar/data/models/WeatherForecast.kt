package it.shinyup.meteoradar.data.models

import com.google.gson.annotations.SerializedName

data class OpenMeteoResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    @SerializedName("current_weather") val currentWeather: CurrentWeather?,
    val hourly: HourlyData?
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

// WMO weather codes + multi-source severity scoring
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

        // Lifted Index: negative = unstable; < -3 = severe
        score += when {
            liftedIndex < -6.0 -> 2
            liftedIndex < -3.0 -> 1
            else -> 0
        }

        // Freezing level: 500–2000m = hail very likely; 2000–2500m = somewhat likely
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
