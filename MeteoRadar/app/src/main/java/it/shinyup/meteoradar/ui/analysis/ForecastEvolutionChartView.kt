package it.shinyup.meteoradar.ui.analysis

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import it.shinyup.meteoradar.utils.HumidexUtil

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
        val humidity: Int = 0,        // daily max relative humidity %
        val humidityMin: Int = 0,     // daily min relative humidity %
        val humidex: Float = 0f,      // daily max humidex
        val humidexMin: Float = 0f    // daily min humidex
    )
    data class ScaleRange(val maxFloor: Float, val maxCeil: Float, val minFloor: Float, val minCeil: Float)

    // Density helpers so text/spacing are consistent (readable) on every screen.
    private val dens = context.resources.displayMetrics.density
    private val sd = context.resources.displayMetrics.scaledDensity
    private fun dp(v: Float) = v * dens
    private fun sp(v: Float) = v * sd

    /** Minimum horizontal space per point; drives the scrollable width. */
    private val perPointPx = (64f * dens).toInt()

    private var points: List<DataPoint> = emptyList()
    private var fixedScale: ScaleRange? = null
    private var showApparentTemp: Boolean = false
    private var showWind: Boolean = false
    private var showHumidity: Boolean = false
    private var showHumidex: Boolean = false

    private val linePaintMax = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F44336")
        strokeWidth = dp(2.5f)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val linePaintMin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#42A5F5")
        strokeWidth = dp(2.5f)
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
        textSize = sp(15f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B949E")
        textSize = sp(11f)
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
        strokeWidth = dp(1.6f)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val dashedMinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00BCD4")
        strokeWidth = dp(1.6f)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val apparentTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF9800")
        textSize = sp(12f)
        textAlign = Paint.Align.CENTER
    }
    private val windBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66BB6A")
        style = Paint.Style.FILL
    }
    private val windTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A5D6A7")
        textSize = sp(12f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val humidityTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FC3F7")
        textSize = sp(12f)
        textAlign = Paint.Align.CENTER
    }
    private val humidexTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF7043")
        textSize = sp(13f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    fun setScale(scale: ScaleRange?) {
        fixedScale = scale
    }

    fun setData(data: List<DataPoint>) {
        points = data
        requestLayout()
        invalidate()
    }

    fun setOverlays(apparentTemp: Boolean, wind: Boolean, humidity: Boolean, humidex: Boolean = false) {
        showApparentTemp = apparentTemp
        showWind = wind
        showHumidity = humidity
        showHumidex = humidex
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // Widen the canvas when there are many points so labels get room and the
        // parent HorizontalScrollView lets the user pan across a readable chart.
        val desiredW = if (points.size >= 2) points.size * perPointPx else measuredWidth
        setMeasuredDimension(maxOf(desiredW, measuredWidth), measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        if (points.size < 2) {
            canvas.drawColor(Color.TRANSPARENT)
            return
        }

        val leftPad   = dp(30f)
        val rightPad  = dp(30f)
        val topPad    = dp(34f)
        val bottomPad = dp(26f)
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

        // With the scrollable width every point has room, so only thin labels
        // for very large series.
        val labelStep = if (points.size > 48) 2 else 1

        // Wind histogram in the gap
        if (showWind) {
            val maxWind = points.maxOf { it.windSpeed }.coerceAtLeast(1f)
            val gapTop = topPad + bandHMax + 8f
            val gapBottom = minBandTop - 8f
            val barMaxH = gapBottom - gapTop - 24f
            val barWidth = dp(14f)

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
                    if (i % labelStep == 0 || i == points.size - 1) {
                        canvas.drawText(
                            "${"%.0f".format(p.windSpeed)}",
                            x,
                            gapBottom - barH - 6f,
                            windTextPaint
                        )
                    }
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

        // Dots + labels
        val dotR = dp(3.5f)
        points.forEachIndexed { i, p ->
            val x    = xOf(i)
            val yMax = yOfMax(p.tempMax)
            val yMin = yOfMin(p.tempMin)

            canvas.drawCircle(x, yMax, dotR, dotPaintMax)
            canvas.drawCircle(x, yMin, dotR, dotPaintMin)

            val showLabel = i % labelStep == 0 || i == points.size - 1

            if (showLabel) {
                canvas.drawText("${"%.0f".format(p.tempMax)}°", x, yMax - dp(8f), textPaint)
                canvas.drawText("${"%.0f".format(p.tempMin)}°", x, yMin + dp(18f), textPaint)

                if (showApparentTemp) {
                    canvas.drawText("${"%.0f".format(p.apparentMax)}°", x, yMax - dp(24f), apparentTextPaint)
                    canvas.drawText("${"%.0f".format(p.apparentMin)}°", x, yMin + dp(32f), apparentTextPaint)
                }

                if (showHumidex && p.humidex > 0f) {
                    val hxMaxY = if (showApparentTemp) yMax - dp(40f) else yMax - dp(24f)
                    humidexTextPaint.color = HumidexUtil.color(p.humidex.toDouble())
                    canvas.drawText("H${p.humidex.toInt()}", x, hxMaxY, humidexTextPaint)
                    if (p.humidexMin > 0f) {
                        val hxMinY = if (showApparentTemp) yMin + dp(46f) else yMin + dp(32f)
                        humidexTextPaint.color = HumidexUtil.color(p.humidexMin.toDouble())
                        canvas.drawText("H${p.humidexMin.toInt()}", x, hxMinY, humidexTextPaint)
                    }
                }

                if (showHumidity && p.humidity > 0) {
                    val hMaxY = when {
                        showApparentTemp && showHumidex -> yMax - dp(56f)
                        showApparentTemp || showHumidex -> yMax - dp(40f)
                        else -> yMax - dp(24f)
                    }
                    canvas.drawText("${p.humidity}%", x, hMaxY, humidityTextPaint)
                    if (p.humidityMin > 0) {
                        val hMinY = when {
                            showApparentTemp && showHumidex -> yMin + dp(60f)
                            showApparentTemp || showHumidex -> yMin + dp(46f)
                            else -> yMin + dp(32f)
                        }
                        canvas.drawText("${p.humidityMin}%", x, hMinY, humidityTextPaint)
                    }
                }

                canvas.drawText(p.xLabel, x, height.toFloat() - dp(6f), labelPaint)
            }
        }
    }
}
