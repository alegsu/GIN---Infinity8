package it.shinyup.meteoradar.data.models

data class GeocodingResponse(
    val results: List<GeocodingResult>?
)

data class GeocodingResult(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val admin1: String?,
    val country_code: String?
) {
    fun displayName(): String = buildString {
        append(name)
        admin1?.let { append(", $it") }
        country?.let { append(" ($it)") }
    }
}
