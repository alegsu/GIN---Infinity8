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
        textSize = 42f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B949E")
        textSize = 30f
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
        color = Color.parseColor("#FF9800")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val dashedMinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00BCD4")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val apparentTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF9800")
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }
    private val windBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66BB6A")
        style = Paint.Style.FILL
    }
    private val windTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A5D6A7")
        textSize = 30f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val humidityTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FC3F7")
        textSize = 32f
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

        val leftPad   = 80f
        val rightPad  = 80f
        val topPad    = 75f
        val bottomPad = 65f
        val chartWidth  = width  - leftPad - rightPad
        val chartHeight = height - topPad  - bottomPad

        val hasGapContent = showWind
        val maxBandPct = if (hasGapContent) 0.38f else 0.44f
        val gapPct     = if (hasGapContent) 0.24f else 0.12f
        val minBandPct = if (hasGapContent) 0.38f else 0.44f

        val bandHMax = chartHeight * maxBandPct
        val gapH     = chartHeight * gapPct
        val bandHMin = chartHeight * minBandPct
        val minBandTop = topPad + bandHMax + gapH

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

        fun xOf(i: Int) = leftPad + i * chartWidth / (points.size - 1)
        fun yOfMax(t: Float) = topPad + bandHMax * (1f - (t - maxBandMin) / maxBandRange)
        fun yOfMin(t: Float) = minBandTop + bandHMin * (1f - (t - minBandMin) / minBandRange)

        // Grid lines
        for (g in kotlin.math.floor(maxBandMin.toDouble()).toInt()..
                  kotlin.math.ceil((maxBandMin + maxBandRange).toDouble()).toInt()) {
            val y = yOfMax(g.toFloat())
            if (y in topPad..(topPad + bandHMax))
                canvas.drawLine(leftPad, y, leftPad + chartWidth, y, gridPaint)
        }
        for (g in kotlin.math.floor(minBandMin.toDouble()).toInt()..
                  kotlin.math.ceil((minBandMin + minBandRange).toDouble()).toInt()) {
            val y = yOfMin(g.toFloat())
            if (y in minBandTop..(minBandTop + bandHMin))
                canvas.drawLine(leftPad, y, leftPad + chartWidth, y, gridPaint)
        }

        // Separator
        val sepY = topPad + bandHMax + gapH / 2f
        canvas.drawLine(leftPad, sepY, leftPad + chartWidth, sepY, separatorPaint)

        // Wind histogram in the gap
        if (showWind) {
            val maxWind = points.maxOf { it.windSpeed }.coerceAtLeast(1f)
            val gapTop = topPad + bandHMax + 8f
            val gapBottom = minBandTop - 8f
            val barMaxH = gapBottom - gapTop - 24f
            val barWidth = (chartWidth / points.size * 0.5f).coerceAtMost(40f)

            points.forEachIndexed { i, p ->
                if (p.windSpeed > 0f) {
                    val x = xOf(i)
                    val barH = (p.windSpeed / maxWind) * barMaxH
                    val rect = RectF(
                        x - barWidth / 2f,
                        gapBottom - barH,
                        x + barWidth / 2f,
                        gapBottom
                    )
                    canvas.drawRoundRect(rect, 4f, 4f, windBarPaint)
                    canvas.drawText(
                        "${"%.0f".format(p.windSpeed)}",
                        x,
                        gapBottom - barH - 6f,
                        windTextPaint
                    )
                }
            }
        }

        // Main temperature lines
        val pathMax = Path()
        val pathMin = Path()
        points.forEachIndexed { i, p ->
            val x = xOf(i)
            if (i == 0) { pathMax.moveTo(x, yOfMax(p.tempMax)); pathMin.moveTo(x, yOfMin(p.tempMin)) }
            else        { pathMax.lineTo(x, yOfMax(p.tempMax)); pathMin.lineTo(x, yOfMin(p.tempMin)) }
        }
        canvas.drawPath(pathMin, linePaintMin)
        canvas.drawPath(pathMax, linePaintMax)

        // Apparent temp dashed overlay + values
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

        // Dots + labels
        points.forEachIndexed { i, p ->
            val x    = xOf(i)
            val yMax = yOfMax(p.tempMax)
            val yMin = yOfMin(p.tempMin)

            canvas.drawCircle(x, yMax, 9f, dotPaintMax)
            canvas.drawCircle(x, yMin, 9f, dotPaintMin)

            // Main temperature values
            canvas.drawText("${"%.0f".format(p.tempMax)}°", x, yMax - 22f, textPaint)
            canvas.drawText("${"%.0f".format(p.tempMin)}°", x, yMin + 50f, textPaint)

            // Apparent temp values (below max label, above min label)
            if (showApparentTemp) {
                canvas.drawText(
                    "${"%.0f".format(p.apparentMax)}°",
                    x, yMax - 58f, apparentTextPaint
                )
                canvas.drawText(
                    "${"%.0f".format(p.apparentMin)}°",
                    x, yMin + 82f, apparentTextPaint
                )
            }

            // Humidity below Tmin apparent (or below Tmin value)
            if (showHumidity && p.humidity > 0) {
                val humY = if (showApparentTemp) yMin + 112f else yMin + 82f
                canvas.drawText("${p.humidity}%", x, humY, humidityTextPaint)
            }

            // X-axis labels
            canvas.drawText(p.xLabel, x, height.toFloat() - 10f, labelPaint)
        }
    }
}
