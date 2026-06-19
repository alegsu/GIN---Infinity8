package it.shinyup.meteoradar.ui.analysis

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ForecastEvolutionChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class DataPoint(
        val xLabel: String,
        val tempMax: Float,
        val tempMin: Float,
        val location: String,
        val apparentMax: Float = 0f,
        val apparentMin: Float = 0f,
        val windSpeed: Float = 0f,
        val humidity: Int = 0
    )
    data class ScaleRange(val maxFloor: Float, val maxCeil: Float, val minFloor: Float, val minCeil: Float)

    private var points: List<DataPoint> = emptyList()
    private var fixedScale: ScaleRange? = null
    private var showApparentTemp: Boolean = false
    private var showWind: Boolean = false
    private var showHumidity: Boolean = false

    private val linePaintMax = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F44336")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val linePaintMin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#42A5F5")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaintMax = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F44336")
        style = Paint.Style.FILL
    }
    private val dotPaintMin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#42A5F5")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B949E")
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#20FFFFFF")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30FFFFFF")
        strokeWidth = 1f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    private val dashedMaxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF9800") // orange for apparent max
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val dashedMinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00BCD4") // cyan for apparent min
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val overlayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0BEC5")
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    fun setScale(scale: ScaleRange?) {
        fixedScale = scale
    }

    fun setData(data: List<DataPoint>) {
        points = data
        invalidate()
    }

    fun setOverlays(apparentTemp: Boolean, wind: Boolean, humidity: Boolean) {
        showApparentTemp = apparentTemp
        showWind = wind
        showHumidity = humidity
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (points.size < 2) {
            canvas.drawColor(Color.TRANSPARENT)
            return
        }

        val leftPad   = 20f
        val rightPad  = 20f
        val topPad    = 60f
        val bottomPad = 55f
        val chartWidth  = width  - leftPad - rightPad
        val chartHeight = height - topPad  - bottomPad

        // Split layout: Tmax top 45%, gap 10%, Tmin bottom 45%
        val bandH = chartHeight * 0.44f
        val gapH  = chartHeight * 0.12f
        val minBandTop = topPad + bandH + gapH

        val scale = fixedScale
        val maxBandMin: Float
        val maxBandRange: Float
        val minBandMin: Float
        val minBandRange: Float

        if (scale != null) {
            maxBandMin = scale.maxFloor
            maxBandRange = scale.maxCeil - scale.maxFloor
            minBandMin = scale.minFloor
            minBandRange = scale.minCeil - scale.minFloor
        } else {
            val maxTemps = points.map { it.tempMax }
            val minTemps = points.map { it.tempMin }
            val maxCenter    = (maxTemps.max() + maxTemps.min()) / 2f
            val maxHalfRange = maxOf((maxTemps.max() - maxTemps.min()) / 2f + 0.8f, 2f)
            maxBandMin   = maxCenter - maxHalfRange
            maxBandRange = maxHalfRange * 2f
            val minCenter    = (minTemps.max() + minTemps.min()) / 2f
            val minHalfRange = maxOf((minTemps.max() - minTemps.min()) / 2f + 0.8f, 2f)
            minBandMin   = minCenter - minHalfRange
            minBandRange = minHalfRange * 2f
        }

        fun xOf(i: Int)    = leftPad + i * chartWidth / (points.size - 1)
        fun yOfMax(t: Float) = topPad     + bandH * (1f - (t - maxBandMin) / maxBandRange)
        fun yOfMin(t: Float) = minBandTop + bandH * (1f - (t - minBandMin) / minBandRange)

        // Grid lines every 1° inside each band
        for (g in kotlin.math.floor(maxBandMin.toDouble()).toInt()..
                  kotlin.math.ceil((maxBandMin + maxBandRange).toDouble()).toInt()) {
            val y = yOfMax(g.toFloat())
            if (y in topPad..(topPad + bandH))
                canvas.drawLine(leftPad, y, leftPad + chartWidth, y, gridPaint)
        }
        for (g in kotlin.math.floor(minBandMin.toDouble()).toInt()..
                  kotlin.math.ceil((minBandMin + minBandRange).toDouble()).toInt()) {
            val y = yOfMin(g.toFloat())
            if (y in minBandTop..(minBandTop + bandH))
                canvas.drawLine(leftPad, y, leftPad + chartWidth, y, gridPaint)
        }

        // Dashed separator between the two bands
        val sepY = topPad + bandH + gapH / 2f
        canvas.drawLine(leftPad, sepY, leftPad + chartWidth, sepY, separatorPaint)

        // Draw lines
        val pathMax = Path()
        val pathMin = Path()
        points.forEachIndexed { i, p ->
            val x = xOf(i)
            if (i == 0) { pathMax.moveTo(x, yOfMax(p.tempMax)); pathMin.moveTo(x, yOfMin(p.tempMin)) }
            else        { pathMax.lineTo(x, yOfMax(p.tempMax)); pathMin.lineTo(x, yOfMin(p.tempMin)) }
        }
        canvas.drawPath(pathMin, linePaintMin)
        canvas.drawPath(pathMax, linePaintMax)

        // Apparent temp dashed overlay
        if (showApparentTemp) {
            val pathAppMax = Path()
            val pathAppMin = Path()
            points.forEachIndexed { i, p ->
                val x = xOf(i)
                if (i == 0) {
                    pathAppMax.moveTo(x, yOfMax(p.apparentMax))
                    pathAppMin.moveTo(x, yOfMin(p.apparentMin))
                } else {
                    pathAppMax.lineTo(x, yOfMax(p.apparentMax))
                    pathAppMin.lineTo(x, yOfMin(p.apparentMin))
                }
            }
            canvas.drawPath(pathAppMax, dashedMaxPaint)
            canvas.drawPath(pathAppMin, dashedMinPaint)
        }

        // Dots + 1-decimal labels + x-axis labels + overlay info
        points.forEachIndexed { i, p ->
            val x    = xOf(i)
            val yMax = yOfMax(p.tempMax)
            val yMin = yOfMin(p.tempMin)

            canvas.drawCircle(x, yMax, 8f, dotPaintMax)
            canvas.drawCircle(x, yMin, 8f, dotPaintMin)

            canvas.drawText("${"%.1f".format(p.tempMax)}°", x, yMax - 18f, textPaint)
            canvas.drawText("${"%.1f".format(p.tempMin)}°", x, yMin + 38f, textPaint)
            canvas.drawText(p.xLabel, x, height.toFloat() - 8f, labelPaint)

            // Wind speed text next to max dot
            if (showWind && p.windSpeed > 0f) {
                canvas.drawText("${"%.0f".format(p.windSpeed)} km/h", x, yMax - 42f, overlayTextPaint)
            }

            // Humidity text next to min dot
            if (showHumidity && p.humidity > 0) {
                canvas.drawText("${p.humidity}%", x, yMin + 58f, overlayTextPaint)
            }
        }
    }
}
