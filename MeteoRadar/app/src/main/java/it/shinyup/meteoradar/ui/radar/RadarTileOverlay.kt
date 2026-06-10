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
import org.osmdroid.views.overlay.Overlay
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

class RadarTileOverlay(
    private val frames: List<RadarFrame>,
    private val host: String
) : Overlay() {

    var currentFrameIndex: Int = 0

    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 204 }
    private val labelFill = Paint().apply {
        color = Color.WHITE
        textSize = 34f
        isAntiAlias = true
    }
    private val labelStroke = Paint().apply {
        color = Color.BLACK
        textSize = 34f
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val versionPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        isAntiAlias = true
        alpha = 180
    }

    private val cache = LruCache<String, Bitmap>(400)
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val executor = Executors.newFixedThreadPool(6)
    private var mapRef = WeakReference<MapView>(null)
    private val prefetchDone = AtomicBoolean(false)
    private val totalLoaded = AtomicInteger(0)
    private val totalFailed = AtomicInteger(0)

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !isEnabled) return
        mapRef = WeakReference(mapView)

        val frame = frames.getOrNull(currentFrameIndex) ?: return
        val projection = mapView.projection
        val zoom = projection.zoomLevel.toInt().coerceIn(0, 18)
        val numTiles = 1 shl zoom
        val bbox = projection.boundingBox

        if (bbox.lonWest.isNaN() || bbox.latNorth.isNaN()) return

        val xMin = lonToTileX(bbox.lonWest, zoom).coerceIn(0, numTiles - 1)
        val xMax = lonToTileX(bbox.lonEast, zoom).coerceIn(0, numTiles - 1)
        val yMin = latToTileY(bbox.latNorth, zoom).coerceIn(0, numTiles - 1)
        val yMax = latToTileY(bbox.latSouth, zoom).coerceIn(0, numTiles - 1)

        var tilesDrawn = 0
        val totalVisible = (xMax - xMin + 1) * (yMax - yMin + 1)

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

        // Pre-fetch tiles for all frames on the first real draw
        if (prefetchDone.compareAndSet(false, true) && zoom in 3..10) {
            prefetchAllFrames(zoom, xMin, xMax, yMin, yMax)
        }

        // Status label — visible until all tiles for current frame are loaded
        val statusY = canvas.height - 80f
        if (tilesDrawn < totalVisible) {
            val msg = "Radar in caricamento... ($tilesDrawn/$totalVisible)"
            canvas.drawText(msg, 20f, statusY, labelStroke)
            canvas.drawText(msg, 20f, statusY, labelFill)
        }

        // Version label bottom-right
        val ver = "v${BuildConfig.VERSION_NAME}"
        val verW = versionPaint.measureText(ver)
        canvas.drawText(ver, canvas.width - verW - 16f, statusY, versionPaint)
    }

    private fun prefetchAllFrames(zoom: Int, xMin: Int, xMax: Int, yMin: Int, yMax: Int) {
        for (frame in frames) {
            for (x in xMin..xMax) {
                for (y in yMin..yMax) {
                    val key = "${frame.time}_${zoom}_${x}_${y}"
                    if (cache[key] == null) fetchTile(frame, zoom, x, y, key)
                }
            }
        }
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
                            totalLoaded.incrementAndGet()
                            mapRef.get()?.post { mapRef.get()?.invalidate() }
                        } else {
                            totalFailed.incrementAndGet()
                        }
                    } else {
                        totalFailed.incrementAndGet()
                    }
                }
            } catch (_: Exception) {
                totalFailed.incrementAndGet()
            } finally {
                pending.remove(key)
            }
        }
    }

    private fun drawTile(canvas: Canvas, projection: org.osmdroid.views.Projection,
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
