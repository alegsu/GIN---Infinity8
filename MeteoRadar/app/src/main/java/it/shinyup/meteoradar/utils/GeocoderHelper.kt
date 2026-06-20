package it.shinyup.meteoradar.utils

import android.content.Context
import android.location.Geocoder
import java.util.Locale

object GeocoderHelper {
    fun cityName(context: Context, lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.let { addr ->
                addr.locality ?: addr.subAdminArea ?: addr.adminArea
            } ?: "%.2f°, %.2f°".format(lat, lon)
        } catch (e: Exception) {
            "%.2f°, %.2f°".format(lat, lon)
        }
    }
}
