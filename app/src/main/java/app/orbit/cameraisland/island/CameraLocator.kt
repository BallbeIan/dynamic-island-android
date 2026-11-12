package app.orbit.cameraisland.island

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Finds where the front camera is on the screen the island is currently on, in pixels, for the current orientation.
 *
 * 1. The system's display cutout (punch hole / notch) when it reports one.
 * 2. Otherwise the under-display-camera shape that phone makers ship as a framework resource
 *    (`config_mainBuiltInDisplayCutoutForUDC`), which is invisible to the normal cutout API.
 *
 * Only a camera on the top edge is returned: the island lives at the top of the screen.
 */
object CameraLocator {

    /** All values in display pixels. [centerX] is measured from the left edge, [bottom] from the top. */
    class Camera(val centerX: Int, val bottom: Int, val width: Int)

    fun screenWidth(ctx: Context, wm: WindowManager): Int =
        if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.width() else ctx.resources.displayMetrics.widthPixels

    fun locate(ctx: Context, wm: WindowManager): Camera? {
        val screenW = screenWidth(ctx, wm)
        val topBand = (60 * ctx.resources.displayMetrics.density).toInt()

        if (Build.VERSION.SDK_INT >= 30) {
            val cutout = try { wm.maximumWindowMetrics.windowInsets.displayCutout } catch (_: Throwable) { null }
            val hole = cutout?.boundingRects
                ?.filter { it.top <= topBand && it.width() > 0 && it.height() > 0 }
                ?.minByOrNull { abs(it.centerX() - screenW / 2) }
            if (hole != null) return Camera(hole.centerX(), hole.bottom, hole.width())
        }
        return underDisplay(ctx, wm, screenW)
    }

    @Suppress("DEPRECATION")
    private fun underDisplay(ctx: Context, wm: WindowManager, screenW: Int): Camera? {
        val display = wm.defaultDisplay ?: return null
        // The resource describes the main (default) built-in display in its natural orientation.
        if (display.displayId != Display.DEFAULT_DISPLAY || display.rotation != Surface.ROTATION_0) return null
        val sys = Resources.getSystem()
        val id = sys.getIdentifier("config_mainBuiltInDisplayCutoutForUDC", "string", "android")
        if (id == 0) return null
        val spec = try { sys.getString(id) } catch (_: Throwable) { return null }
        if (spec.isBlank()) return null
        val parsed = CutoutSpec.parse(spec) ?: return null
        if ("@bottom" in parsed.markers) return null
        val density = if (parsed.markers.contains("@dp")) ctx.resources.displayMetrics.density else 1f
        val originX = when {
            "@left" in parsed.markers -> 0f
            "@right" in parsed.markers -> screenW.toFloat()
            else -> screenW / 2f                     // default origin: top-centre
        }
        val b = parsed.bounds
        val left = originX + b.left * density
        val right = originX + b.right * density
        return Camera(((left + right) / 2f).toInt(), (b.bottom * density).toInt(), (right - left).toInt().coerceAtLeast(1))
    }
}

/** Minimal parser for the SVG-like cutout path strings used by Android's `config_*DisplayCutout` resources. */
object CutoutSpec {
    class Bounds(var left: Float = Float.MAX_VALUE, var top: Float = Float.MAX_VALUE,
                 var right: Float = -Float.MAX_VALUE, var bottom: Float = -Float.MAX_VALUE) {
        fun add(x: Float, y: Float) { left = min(left, x); right = max(right, x); top = min(top, y); bottom = max(bottom, y) }
        val valid get() = left <= right && top <= bottom
    }

    class Parsed(val bounds: Bounds, val markers: Set<String>)

    fun parse(raw: String): Parsed? {
        val markers = Regex("@[a-z_]+").findAll(raw).map { it.value }.toSet()
        val text = raw.replace(Regex("@[a-z_]+"), " ")
        val tokens = Regex("[MmLlHhVvAaZz]|-?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?").findAll(text).map { it.value }.toList()
        val b = Bounds()
        var i = 0
        var cx = 0f; var cy = 0f; var sx = 0f; var sy = 0f
        var pendingMove = false
        fun num(): Float = tokens[i++].toFloat()
        fun draw(x: Float, y: Float) {
            if (pendingMove) { b.add(cx, cy); pendingMove = false }
            b.add(x, y); cx = x; cy = y
        }
        var cmd = ' '
        try {
            while (i < tokens.size) {
                if (tokens[i][0].isLetter()) cmd = tokens[i++][0]
                when (cmd) {
                    'M', 'm' -> { val x = num(); val y = num(); cx = if (cmd == 'm') cx + x else x; cy = if (cmd == 'm') cy + y else y; sx = cx; sy = cy; pendingMove = true; cmd = if (cmd == 'm') 'l' else 'L' }
                    'L', 'l' -> { val x = num(); val y = num(); draw(if (cmd == 'l') cx + x else x, if (cmd == 'l') cy + y else y) }
                    'H', 'h' -> { val x = num(); draw(if (cmd == 'h') cx + x else x, cy) }
                    'V', 'v' -> { val y = num(); draw(cx, if (cmd == 'v') cy + y else y) }
                    'A', 'a' -> {
                        val rx = num(); val ry = num(); num(); val large = num(); num()
                        val x = num(); val y = num()
                        val ex = if (cmd == 'a') cx + x else x
                        val ey = if (cmd == 'a') cy + y else y
                        // circular arcs only (punch holes): include the whole circle when the arc is a half or more
                        val chord = sqrt((ex - cx) * (ex - cx) + (ey - cy) * (ey - cy))
                        if (pendingMove) { b.add(cx, cy); pendingMove = false }
                        if (rx > 0 && abs(rx - ry) < 0.01f && (large >= 1f || chord >= rx * 1.8f)) {
                            val mx = (cx + ex) / 2f; val my = (cy + ey) / 2f
                            val h2 = rx * rx - chord * chord / 4f
                            val d = if (h2 > 0) sqrt(h2) else 0f
                            // centre lies on the perpendicular bisector; for a semicircle d = 0
                            val nx = if (chord > 0) -(ey - cy) / chord else 0f
                            val ny = if (chord > 0) (ex - cx) / chord else 0f
                            val ccx = mx + nx * d * (if (large >= 1f) 1f else -1f)
                            val ccy = my + ny * d * (if (large >= 1f) 1f else -1f)
                            b.add(ccx - rx, ccy - rx); b.add(ccx + rx, ccy + rx)
                        }
                        b.add(ex, ey); cx = ex; cy = ey
                    }
                    'Z', 'z' -> { draw(sx, sy) }
                    else -> return null
                }
            }
        } catch (_: Throwable) { return null }
        return if (b.valid) Parsed(b, markers) else null
    }
}
