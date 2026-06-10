package it.shinyup.meteoradar.data

import it.shinyup.meteoradar.data.api.OpenMeteoApi
import it.shinyup.meteoradar.data.api.RainViewerApi
import it.shinyup.meteoradar.data.models.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class WeatherRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val rainViewerApi: RainViewerApi = Retrofit.Builder()
        .baseUrl(RainViewerApi.BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RainViewerApi::class.java)

    private val openMeteoApi: OpenMeteoApi = Retrofit.Builder()
        .baseUrl(OpenMeteoApi.BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenMeteoApi::class.java)

    suspend fun getRadarFrames(): Result<RainViewerResponse> = runCatching {
        rainViewerApi.getWeatherMaps()
    }

    suspend fun getForecast(latitude: Double, longitude: Double): Result<OpenMeteoResponse> =
        runCatching {
            openMeteoApi.getForecast(latitude, longitude)
        }

    fun assessAlerts(forecast: OpenMeteoResponse, lat: Double, lon: Double): List<WeatherAlert> {
        val alerts = mutableListOf<WeatherAlert>()
        val hourly = forecast.hourly ?: return alerts
        val currentCode = forecast.currentWeather?.weathercode ?: 0

        // Check current conditions first
        if (WeatherCode.isThunderstorm(currentCode)) {
            val level = if (WeatherCode.hasHail(currentCode)) AlertLevel.DANGER else AlertLevel.WARNING
            val type = if (WeatherCode.isHeavyHail(currentCode)) AlertType.HEAVY_HAIL
                       else if (WeatherCode.hasHail(currentCode)) AlertType.HAIL
                       else AlertType.THUNDERSTORM
            alerts.add(WeatherAlert(
                type = type,
                level = level,
                title = buildAlertTitle(type, level),
                description = buildAlertDescription(currentCode, 0.0, 0.0),
                latitude = lat,
                longitude = lon,
                weatherCode = currentCode
            ))
        }

        // Check next 6 hours for upcoming severe weather
        val nextHours = minOf(6, hourly.time.size)
        for (i in 0 until nextHours) {
            val code = hourly.weatherCode.getOrElse(i) { 0 }
            val precip = hourly.precipitation.getOrElse(i) { 0.0 }
            val cape = hourly.cape.getOrElse(i) { 0.0 }

            when {
                WeatherCode.isHeavyHail(code) -> {
                    alerts.add(WeatherAlert(
                        type = AlertType.HEAVY_HAIL,
                        level = AlertLevel.EXTREME,
                        title = "Grandine forte prevista",
                        description = "Temporale con grandine intensa previsto nelle prossime ${i + 1} ore. " +
                                      "CAPE: ${cape.toInt()} J/kg. Mettere al riparo veicoli.",
                        latitude = lat,
                        longitude = lon,
                        precipitation = precip,
                        cape = cape,
                        weatherCode = code
                    ))
                    break
                }
                WeatherCode.hasHail(code) && alerts.none { it.type == AlertType.HAIL } -> {
                    alerts.add(WeatherAlert(
                        type = AlertType.HAIL,
                        level = AlertLevel.DANGER,
                        title = "Rischio grandine",
                        description = "Possibile grandine nelle prossime ${i + 1} ore. " +
                                      "CAPE: ${cape.toInt()} J/kg.",
                        latitude = lat,
                        longitude = lon,
                        precipitation = precip,
                        cape = cape,
                        weatherCode = code
                    ))
                }
                cape > 2000 && alerts.none { it.type == AlertType.THUNDERSTORM } -> {
                    alerts.add(WeatherAlert(
                        type = AlertType.THUNDERSTORM,
                        level = AlertLevel.WARNING,
                        title = "Alta instabilità atmosferica",
                        description = "Energia convettiva elevata (CAPE ${cape.toInt()} J/kg). " +
                                      "Possibili temporali intensi nelle prossime ore.",
                        latitude = lat,
                        longitude = lon,
                        cape = cape,
                        weatherCode = code
                    ))
                }
                precip > 10.0 && alerts.none { it.type == AlertType.HEAVY_RAIN } -> {
                    alerts.add(WeatherAlert(
                        type = AlertType.HEAVY_RAIN,
                        level = AlertLevel.WARNING,
                        title = "Pioggia intensa prevista",
                        description = "Previsti ${precip.toInt()} mm di pioggia nelle prossime ${i + 1} ore.",
                        latitude = lat,
                        longitude = lon,
                        precipitation = precip,
                        weatherCode = code
                    ))
                }
            }
        }

        // Always include hail forecast timeline (sent at most once/hour via deduplication)
        val hailTimeline = buildHailTimeline(hourly)
        val maxHailLevel = computeMaxHailLevel(hourly)
        alerts.add(WeatherAlert(
            type = AlertType.HAIL_FORECAST,
            level = maxHailLevel,
            title = "Previsione grandine – prossime 6h",
            description = hailTimeline,
            latitude = lat,
            longitude = lon
        ))

        return alerts
    }

    private fun buildHailTimeline(hourly: HourlyData): String {
        val sb = StringBuilder()
        val count = minOf(6, hourly.time.size)
        for (i in 0 until count) {
            val code = hourly.weatherCode.getOrElse(i) { 0 }
            val cape = hourly.cape.getOrElse(i) { 0.0 }
            val precipProb = hourly.precipitationProbability.getOrElse(i) { 0 }

            val risk = when {
                WeatherCode.isHeavyHail(code) -> "ALTO ⛈"
                WeatherCode.hasHail(code) -> "PERICOLO ⚡"
                cape > 2000 -> "MODERATO ⚡"
                cape > 500 && precipProb > 40 -> "POSSIBILE 🌧"
                else -> "BASSO ✓"
            }

            val label = if (i == 0) "Prossima ora" else "Tra ${i}-${i + 1}h"
            sb.appendLine("• $label: $risk")
        }
        return sb.toString().trimEnd()
    }

    private fun computeMaxHailLevel(hourly: HourlyData): AlertLevel {
        for (i in 0 until minOf(6, hourly.weatherCode.size)) {
            val code = hourly.weatherCode[i]
            val cape = hourly.cape.getOrElse(i) { 0.0 }
            if (WeatherCode.isHeavyHail(code)) return AlertLevel.EXTREME
            if (WeatherCode.hasHail(code)) return AlertLevel.DANGER
            if (cape > 2000) return AlertLevel.WARNING
        }
        return AlertLevel.INFO
    }

    private fun buildAlertTitle(type: AlertType, level: AlertLevel): String = when (type) {
        AlertType.HEAVY_HAIL -> "⛈ GRANDINE FORTE IN CORSO"
        AlertType.HAIL -> "⛈ Grandine in corso"
        AlertType.HAIL_FORECAST -> "Previsione grandine"
        AlertType.THUNDERSTORM -> "⚡ Temporale in corso"
        AlertType.HEAVY_RAIN -> "🌧 Pioggia intensa"
        AlertType.STRONG_WIND -> "💨 Vento forte"
    }

    private fun buildAlertDescription(code: Int, precip: Double, cape: Double): String {
        val desc = WeatherCode.description(code)
        return buildString {
            append(desc)
            if (precip > 0) append(". Precipitazioni: ${precip.toInt()} mm")
            if (cape > 0) append(". CAPE: ${cape.toInt()} J/kg")
        }
    }
}
