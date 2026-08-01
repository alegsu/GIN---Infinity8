package it.shinyup.meteoradar.utils

import android.graphics.Color
import kotlin.math.exp
import kotlin.math.ln

/**
 * Humidex (Canadian heat-comfort index) computation and comfort bands.
 *
 * Humidex = T + 0.5555 * (e - 10), where e is the vapor pressure derived from
 * the dew point. Below the air temperature the index is not meaningful, so it
 * is clamped to the air temperature.
 */
object HumidexUtil {

    /** Humidex from air temperature and dew point (both °C). */
    fun fromDewPoint(tempC: Double, dewPointC: Double): Double {
        val dewK = dewPointC + 273.15
        val e = 6.11 * exp(5417.7530 * (1.0 / 273.16 - 1.0 / dewK))
        val h = tempC + 0.5555 * (e - 10.0)
        return if (h < tempC) tempC else h
    }

    /** Humidex from air temperature (°C) and relative humidity (%). */
    fun fromHumidity(tempC: Double, humidityPct: Double): Double {
        if (humidityPct <= 0.0) return tempC
        val rh = humidityPct.coerceIn(1.0, 100.0)
        val gamma = ln(rh / 100.0) + 17.625 * tempC / (243.04 + tempC)
        val dewPoint = 243.04 * gamma / (17.625 - gamma)
        return fromDewPoint(tempC, dewPoint)
    }

    /** Comfort band index 0..4 (benessere → colpo di calore). */
    fun levelIndex(humidex: Double): Int = when {
        humidex < 30 -> 0
        humidex < 40 -> 1
        humidex < 46 -> 2
        humidex < 54 -> 3
        else         -> 4
    }

    fun color(humidex: Double): Int = when (levelIndex(humidex)) {
        0 -> Color.parseColor("#66BB6A")
        1 -> Color.parseColor("#FFC107")
        2 -> Color.parseColor("#FF9800")
        3 -> Color.parseColor("#F44336")
        else -> Color.parseColor("#9C27B0")
    }
}
