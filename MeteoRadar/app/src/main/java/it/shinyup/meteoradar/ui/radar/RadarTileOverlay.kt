package it.shinyup.meteoradar.ui.radar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import it.shinyup.meteoradar.BuildConfig
import it.shinyup.meteoradar.data.models.RadarFrame
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

class RadarTileOverlay(
    private val frames: List<RadarFrame>,
    private val host: String,
    private val mapView: MapView
) : Overlay() {

    var currentFrameIndex: Int = 0

    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 204 }
    private val labelFill = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
    }
    private val labelStroke = Paint().apply {
        color = Color.BLACK
        textSize = 32f
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val cache = LruCache<String, Bitmap>(400)
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val executor = Executors.newFixedThreadPool(6)

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Canonical OSMDroid extension point — always invoked by the overlay manager.
    override fun draw(canvas: Canvas, projection: Projection) {
        if (!isEnabled) return

        // Clamp tile zoom 3-8: prevents blank screen when zooming out (new zoom = cache miss)
        // and avoids fetching thousands of tiny tiles when zoomed in past z8.
        val zoom = projection.zoomLevel.toInt().coerceIn(3, 8)
        val frame = frames.getOrNull(currentFrameIndex)

        var tilesDrawn = 0
        var totalVisible = 0

        if (frame != null) {
            val numTiles = 1 shl zoom
            val bbox = projection.boundingBox
            if (!bbox.lonWest.isNaN() && !bbox.latNorth.isNaN()) {
                val xMin = lonToTileX(bbox.lonWest, zoom).coerceIn(0, numTiles - 1)
                val xMax = lonToTileX(bbox.lonEast, zoom).coerceIn(0, numTiles - 1)
                val yMin = latToTileY(bbox.latNorth, zoom).coerceIn(0, numTiles - 1)
                val yMax = latToTileY(bbox.latSouth, zoom).coerceIn(0, numTiles - 1)

                totalVisible = (xMax - xMin + 1) * (yMax - yMin + 1)

                for (x in xMin..xMax) {
                    for (y in yMin..yMax) {
                        val key = "${frame.time}_${zoom}_${x}_${y}"
                        val bmp = cache[key]
                        if (bmp != null) {
                            drawTile(canvas, projection, zoom, x, y, bmp)
                            tilesDrawn++
                        } else {
                            fetchTile(frame, zoom, x, y, key)
                        }
                    }
                }
            }
        }

        // Status line near top — always visible regardless of UI chrome below.
        val y = 80f
        val status = when {
            frames.isEmpty() -> "Radar: nessun frame"
            tilesDrawn == 0 && totalVisible > 0 -> "Radar: caricamento... (0/$totalVisible)"
            tilesDrawn < totalVisible -> "Radar: caricamento... ($tilesDrawn/$totalVisible)"
            tilesDrawn > 0 -> ""   // tiles loaded — rain visible if present
            else -> "Radar: nessuna precipitazione nell'area"
        }
        if (status.isNotEmpty()) {
            canvas.drawText(status, 20f, y, labelStroke)
            canvas.drawText(status, 20f, y, labelFill)
        }

        // Version — top-right corner, always drawn
        val ver = "v${BuildConfig.VERSION_NAME}"
        val verW = labelFill.measureText(ver)
        canvas.drawText(ver, canvas.width - verW - 16f, y, labelStroke)
        canvas.drawText(ver, canvas.width - verW - 16f, y, labelFill)
    }

    private fun fetchTile(frame: RadarFrame, z: Int, x: Int, y: Int, key: String) {
        if (!pending.add(key)) return
        val url = "$host${frame.path}/256/$z/$x/$y/2/1_1.png"
        executor.submit {
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    val bytes = resp.body?.bytes()
                    if (resp.isSuccessful && bytes != null && bytes.isNotEmpty()) {
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            cache.put(key, bmp)
                            mapView.postInvalidate()
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                pending.remove(key)
            }
        }
    }

    private fun drawTile(canvas: Canvas, projection: Projection,
                         zoom: Int, x: Int, y: Int, bmp: Bitmap) {
        val numTiles = 1 shl zoom
        val topLat = tileYToLat(y, zoom)
        val bottomLat = tileYToLat(y + 1, zoom)
        val leftLon = x.toDouble() / numTiles * 360.0 - 180.0
        val rightLon = (x + 1).toDouble() / numTiles * 360.0 - 180.0

        val topLeft = projection.toPixels(GeoPoint(topLat, leftLon), null)
        val bottomRight = projection.toPixels(GeoPoint(bottomLat, rightLon), null)

        canvas.drawBitmap(
            bmp, null,
            RectF(topLeft.x.toFloat(), topLeft.y.toFloat(),
                  bottomRight.x.toFloat(), bottomRight.y.toFloat()),
            tilePaint
        )
    }

    private fun lonToTileX(lon: Double, zoom: Int): Int =
        ((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()

    private fun latToTileY(lat: Double, zoom: Int): Int {
        val latRad = Math.toRadians(lat)
        return ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)).toInt()
    }

    private fun tileYToLat(y: Int, zoom: Int): Double {
        val n = PI - 2.0 * PI * y.toDouble() / (1 shl zoom)
        return Math.toDegrees(atan(sinh(n)))
    }

    fun destroy() {
        executor.shutdown()
        cache.evictAll()
        pending.clear()
    }
}
