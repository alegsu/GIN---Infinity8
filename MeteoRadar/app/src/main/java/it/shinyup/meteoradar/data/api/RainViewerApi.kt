package it.shinyup.meteoradar.data.api

import it.shinyup.meteoradar.data.models.RainViewerResponse
import retrofit2.http.GET

interface RainViewerApi {

    @GET("public/weather-maps.json")
    suspend fun getWeatherMaps(): RainViewerResponse

    companion object {
        const val BASE_URL = "https://api.rainviewer.com/"
        const val TILE_HOST = "https://tilecache.rainviewer.com"

        // Color scheme 2 = classic precipitation palette
        // Options: smooth=1, snow=1
        fun tileUrl(host: String, path: String, z: Int, x: Int, y: Int): String =
            "$host$path/256/$z/$x/$y/2/1_1.png"
    }
}
