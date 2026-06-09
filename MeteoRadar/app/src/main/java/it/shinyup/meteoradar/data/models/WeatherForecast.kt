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
    @SerializedName("precipitation_probability") val precipitationProbability: List<Int>
)

// WMO weather codes interpretation
object WeatherCode {
    fun isThunderstorm(code: Int) = code >= 95
    fun hasHail(code: Int) = code == 96 || code == 99
    fun isHeavyHail(code: Int) = code == 99
    fun isHeavyRain(code: Int) = code in 65..67 || code in 82..82
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
}
