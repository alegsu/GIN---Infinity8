package it.shinyup.meteoradar.ui.analysis

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ForecastEvolutionChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class DataPoint(val xLabel: String, val tempMax: Float, val tempMin: Float, val location: String)

    private var points: List<DataPoint> = emptyList()

    private val linePaintMax = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F44336")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val linePaintMin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#42A5F5")
        strokeWidth = 4f
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
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B949E")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#20FFFFFF")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    fun setData(data: List<DataPoint>) {
        points = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (points.size < 2) {
            canvas.drawColor(Color.TRANSPARENT)
            return
        }

        val leftPad = 20f
        val rightPad = 20f
        val topPad = 50f
        val bottomPad = 50f
        val chartWidth = width - leftPad - rightPad
        val chartHeight = height - topPad - bottomPad

        val allTemps = points.flatMap { listOf(it.tempMax, it.tempMin) }
        val dataMin = allTemps.min()
        val dataMax = allTemps.max()
        // Enforce a minimum visible range (10°) so small variations don't look dramatic
        val center = (dataMin + dataMax) / 2f
        val halfRange = maxOf((dataMax - dataMin) / 2f + 2f, 6f)
        val minTemp = center - halfRange
        val maxTemp = center + halfRange
        val range = maxTemp - minTemp

        fun xOf(i: Int) = leftPad + i * chartWidth / (points.size - 1)
        fun yOf(temp: Float) = topPad + chartHeight * (1f - (temp - minTemp) / range)

        // Horizontal grid lines every 2 degrees
        val gridStep = 2
        val gridStart = (minTemp.toInt() / gridStep) * gridStep
        for (g in gridStart..maxTemp.toInt() step gridStep) {
            val y = yOf(g.toFloat())
            canvas.drawLine(leftPad, y, leftPad + chartWidth, y, gridPaint)
        }

        // Draw min line
        val pathMin = Path()
        points.forEachIndexed { i, p ->
            val x = xOf(i); val y = yOf(p.tempMin)
            if (i == 0) pathMin.moveTo(x, y) else pathMin.lineTo(x, y)
        }
        canvas.drawPath(pathMin, linePaintMin)

        // Draw max line
        val pathMax = Path()
        points.forEachIndexed { i, p ->
            val x = xOf(i); val y = yOf(p.tempMax)
            if (i == 0) pathMax.moveTo(x, y) else pathMax.lineTo(x, y)
        }
        canvas.drawPath(pathMax, linePaintMax)

        // Dots + temperature labels + x-axis labels
        points.forEachIndexed { i, p ->
            val x = xOf(i)
            val yMax = yOf(p.tempMax)
            val yMin = yOf(p.tempMin)

            canvas.drawCircle(x, yMax, 7f, dotPaintMax)
            canvas.drawCircle(x, yMin, 7f, dotPaintMin)

            canvas.drawText("${p.tempMax.toInt()}°", x, yMax - 14f, textPaint)
            canvas.drawText("${p.tempMin.toInt()}°", x, yMin + 30f, textPaint)
            canvas.drawText(p.xLabel, x, height.toFloat() - 8f, labelPaint)
        }
    }
}
