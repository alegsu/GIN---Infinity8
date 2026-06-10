package it.shinyup.meteoradar.data.api

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

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
    }
}
