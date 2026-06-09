package it.shinyup.meteoradar.data.models

import com.google.gson.annotations.SerializedName

data class RainViewerResponse(
    val version: String,
    val generated: Long,
    val host: String,
    val radar: RadarInfo,
    val satellite: SatelliteInfo?
)

data class RadarInfo(
    val past: List<RadarFrame>,
    val nowcast: List<RadarFrame>
)

data class SatelliteInfo(
    val infrared: List<RadarFrame>
)

data class RadarFrame(
    val time: Long,
    val path: String
)
