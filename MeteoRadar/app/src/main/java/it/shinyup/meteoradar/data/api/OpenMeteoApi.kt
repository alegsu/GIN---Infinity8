package it.shinyup.meteoradar.data.api

import it.shinyup.meteoradar.data.models.ModelComparisonResponse
import it.shinyup.meteoradar.data.models.OpenMeteoResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String =
            "precipitation,weathercode,cape,precipitation_probability," +
            "lifted_index,windgusts_10m,freezing_level_height,showers",
        @Query("current_weather") currentWeather: Boolean = true,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_hours") forecastHours: Int = 24
    ): OpenMeteoResponse

    @GET("v1/forecast")
    suspend fun getDailyForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String =
            "temperature_2m_max,temperature_2m_min,weathercode," +
            "precipitation_sum,precipitation_probability_max," +
            "apparent_temperature_max,apparent_temperature_min," +
            "windspeed_10m_max,relative_humidity_2m_max",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7
    ): OpenMeteoResponse

    @GET("v1/forecast")
    suspend fun getPastDaysData(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,weathercode,precipitation_sum,precipitation_probability_max",
        @Query("past_days") pastDays: Int = 7,
        @Query("forecast_days") forecastDays: Int = 0,
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoResponse

    /** Fetches temperature_2m_max from 3 independent models to compute inter-model spread. */
    @GET("v1/forecast")
    suspend fun getModelComparison(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String = "temperature_2m_max",
        @Query("models") models: String = "gfs_seamless,ecmwf_ifs025,icon_seamless",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7
    ): ModelComparisonResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
    }
}
