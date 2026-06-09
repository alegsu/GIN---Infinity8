package it.shinyup.meteoradar.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object LocationHelper {

    // Default location: Roma, Italia
    const val DEFAULT_LAT = 41.9028
    const val DEFAULT_LON = 12.4964

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location? =
        suspendCancellableCoroutine { cont ->
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()

            cont.invokeOnCancellation { cts.cancel() }

            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { location -> cont.resume(location) }
                .addOnFailureListener { cont.resume(null) }
        }
}
