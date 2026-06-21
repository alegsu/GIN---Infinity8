package it.shinyup.meteoradar.data

import it.shinyup.meteoradar.data.api.GeocodingApi
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

    private val geocodingApi: GeocodingApi = Retrofit.Builder()
        .baseUrl(GeocodingApi.BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GeocodingApi::class.java)

    suspend fun searchCities(query: String): List<GeocodingResult>? = try {
        geocodingApi.search(name = query).results
    } catch (e: Exception) {
        null
    }

    suspend fun getRadarFrames(): Result<RainViewerResponse> = runCatching {
        rainViewerApi.getWeatherMaps()
    }

    suspend fun getForecast(latitude: Double, longitude: Double, forecastDays: Int = 3): Result<OpenMeteoResponse> =
        runCatching {
            openMeteoApi.getForecast(latitude, longitude, forecastDays = forecastDays)
        }

    suspend fun getDailyForecast(latitude: Double, longitude: Double): Result<OpenMeteoResponse> =
        runCatching {
            openMeteoApi.getDailyForecast(latitude, longitude)
        }

    suspend fun getModelComparison(latitude: Double, longitude: Double): Result<ModelComparisonResponse> =
        runCatching {
            openMeteoApi.getModelComparison(latitude, longitude)
        }

    suspend fun getPastDaysData(latitude: Double, longitude: Double): Result<OpenMeteoResponse> =
        runCatching {
            openMeteoApi.getPastDaysData(latitude, longitude)
        }

    /** Merge multiple forecasts taking worst-case values per hour slot. */
    fun mergeForecasts(forecasts: List<OpenMeteoResponse>): OpenMeteoResponse {
        val base = forecasts[0]
        val baseHourly = base.hourly ?: return base
        val n = baseHourly.time.size

        val codes  = MutableList(n) { i -> baseHourly.weatherCode.getOrElse(i) { 0 } }
        val precip = MutableList(n) { i -> baseHourly.precipitation.getOrElse(i) { 0.0 } }
        val cape   = MutableList(n) { i -> baseHourly.cape.getOrElse(i) { 0.0 } }
        val pp     = MutableList(n) { i -> baseHourly.precipitationProbability.getOrElse(i) { 0 } }
        val li     = MutableList(n) { i -> baseHourly.liftedIndex?.getOrElse(i) { 0.0 } ?: 0.0 }
        val gusts  = MutableList(n) { i -> baseHourly.windGusts?.getOrElse(i) { 0.0 } ?: 0.0 }
        val fz     = MutableList(n) { i -> baseHourly.freezingLevelHeight?.getOrElse(i) { 3000.0 } ?: 3000.0 }
        val shower = MutableList(n) { i -> baseHourly.showers?.getOrElse(i) { 0.0 } ?: 0.0 }

        for (f in forecasts.drop(1)) {
            val h = f.hourly ?: continue
            for (i in 0 until n) {
                val c = h.weatherCode.getOrElse(i) { 0 }; if (c > codes[i]) codes[i] = c
                val pr = h.precipitation.getOrElse(i) { 0.0 }; if (pr > precip[i]) precip[i] = pr
                val ca = h.cape.getOrElse(i) { 0.0 }; if (ca > cape[i]) cape[i] = ca
                val p = h.precipitationProbability.getOrElse(i) { 0 }; if (p > pp[i]) pp[i] = p
                // LI: lower (more negative) = more unstable = worst case
                val l = h.liftedIndex?.getOrElse(i) { 0.0 } ?: 0.0; if (l < li[i]) li[i] = l
                val g = h.windGusts?.getOrElse(i) { 0.0 } ?: 0.0; if (g > gusts[i]) gusts[i] = g
                // Freezing level: lower = more hail-prone = worst case
                val fzl = h.freezingLevelHeight?.getOrElse(i) { 3000.0 } ?: 3000.0; if (fzl < fz[i]) fz[i] = fzl
                val sh = h.showers?.getOrElse(i) { 0.0 } ?: 0.0; if (sh > shower[i]) shower[i] = sh
            }
        }

        return base.copy(
            hourly = HourlyData(
                time = baseHourly.time,
                weatherCode = codes,
                precipitation = precip,
                cape = cape,
                precipitationProbability = pp,
                liftedIndex = li,
                windGusts = gusts,
                freezingLevelHeight = fz,
                showers = shower
            )
        )
    }

    /**
     * Assess weather alerts using multi-source cross-referenced scoring.
     * Returns at most one alert per call (the worst within the next 6 hours).
     */
    fun assessAlerts(forecast: OpenMeteoResponse, lat: Double, lon: Double): List<WeatherAlert> {
        val hourly = forecast.hourly ?: return emptyList()
        val nextHours = minOf(6, hourly.time.size)

        var maxScore = 0
        var worstHour = 0
        var worstCode = 0
        var worstCape = 0.0
        var worstPrecip = 0.0
        var worstLi = 0.0
        var worstGusts = 0.0
        var worstFz = 3000.0

        for (i in 0 until nextHours) {
            val code     = hourly.weatherCode.getOrElse(i) { 0 }
            val cape     = hourly.cape.getOrElse(i) { 0.0 }
            val li       = hourly.liftedIndex?.getOrElse(i) { 0.0 } ?: 0.0
            val gusts    = hourly.windGusts?.getOrElse(i) { 0.0 } ?: 0.0
            val fz       = hourly.freezingLevelHeight?.getOrElse(i) { 3000.0 } ?: 3000.0
            val precip   = hourly.precipitation.getOrElse(i) { 0.0 }
            val showers  = hourly.showers?.getOrElse(i) { 0.0 } ?: 0.0
            val precipProb = hourly.precipitationProbability.getOrElse(i) { 0 }

            val score = WeatherCode.computeSeverityScore(
                code, cape, li, gusts, fz, precip, showers, precipProb
            )
            if (score > maxScore) {
                maxScore = score; worstHour = i
                worstCode = code; worstCape = cape
                worstPrecip = precip; worstLi = li
                worstGusts = gusts; worstFz = fz
            }
        }

        if (maxScore == 0) return emptyList()

        val level = when {
            maxScore >= 7 -> AlertLevel.EXTREME
            maxScore >= 5 -> AlertLevel.DANGER
            maxScore >= 3 -> AlertLevel.WARNING
            else          -> AlertLevel.INFO
        }

        val type = when {
            WeatherCode.isHeavyHail(worstCode)   -> AlertType.HEAVY_HAIL
            WeatherCode.hasHail(worstCode)        -> AlertType.HAIL
            WeatherCode.isThunderstorm(worstCode) -> AlertType.THUNDERSTORM
            worstCape > 2000                      -> AlertType.THUNDERSTORM
            worstPrecip > 10                      -> AlertType.HEAVY_RAIN
            else                                  -> AlertType.THUNDERSTORM
        }

        val hourLabel = when (worstHour) {
            0    -> "in corso"
            1    -> "nella prossima ora"
            else -> "tra ${worstHour}–${worstHour + 1}h"
        }

        val description = buildString {
            append(WeatherCode.description(worstCode)).append(" – $hourLabel.")
            if (worstCape > 500)            append(" CAPE: ${worstCape.toInt()} J/kg.")
            if (worstLi < -2.0)             append(" LI: ${"%.1f".format(worstLi)}.")
            if (worstFz in 500.0..2499.0)   append(" Livello zero: ${worstFz.toInt()} m.")
            if (worstGusts > 60)            append(" Raffiche: ${worstGusts.toInt()} km/h.")
            if (worstPrecip > 0)            append(" Precip: ${"%.1f".format(worstPrecip)} mm.")
            append(" (score $maxScore/15)")
        }

        return listOf(
            WeatherAlert(
                type = type,
                level = level,
                title = alertTitle(type, level),
                description = description,
                latitude = lat,
                longitude = lon,
                precipitation = worstPrecip,
                cape = worstCape,
                weatherCode = worstCode
            )
        )
    }

    private fun alertTitle(type: AlertType, level: AlertLevel): String = when (type) {
        AlertType.HEAVY_HAIL  -> "⛈ Grandine forte prevista"
        AlertType.HAIL        -> "⛈ Rischio grandine"
        AlertType.THUNDERSTORM -> when (level) {
            AlertLevel.EXTREME, AlertLevel.DANGER -> "⚡ Temporale intenso previsto"
            else -> "⚡ Alta instabilità atmosferica"
        }
        AlertType.HEAVY_RAIN  -> "🌧 Pioggia intensa prevista"
        AlertType.STRONG_WIND -> "💨 Vento forte previsto"
        AlertType.HAIL_FORECAST -> "Previsione grandine"
    }
}
