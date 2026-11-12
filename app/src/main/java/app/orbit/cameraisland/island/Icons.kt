package app.orbit.cameraisland.island

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.animation.DecelerateInterpolator

/** Built-in vector-style icons, drawn on a 24x24 grid so any alert can use any of them. */
enum class Icon(val key: String, val label: String, val inPicker: Boolean = true) {
    WIFI("wifi", "Wi-Fi"), BLUETOOTH("bluetooth", "Bluetooth"), HEADPHONES("headphones", "Headphones"),
    HOTSPOT("hotspot", "Hotspot"), VPN("vpn", "VPN shield"), ALARM("alarm", "Alarm clock"),
    VOLUME("volume", "Speaker"), BELL("bell", "Bell"), DND("dnd", "Do not disturb"),
    RECORD("record", "Record"), MIC("mic", "Microphone"), CAMERA("camera", "Camera"),
    AIRPLANE("airplane", "Airplane"), NFC("nfc", "NFC"), DATA("data", "Data arrows"),
    FLASHLIGHT("flashlight", "Flashlight"), BOLT("bolt", "Lightning"), BATTERY("battery", "Battery"),
    CHECK("check", "Check mark"),
    // used by the settings UI only
    PALETTE("palette", "Palette", false), MUSIC("music", "Music note", false), TOUCH("touch", "Touch", false),
    PLAY("play", "Play", false), PAUSE("pause", "Pause", false), PREVIOUS("previous", "Previous", false), NEXT("next", "Next", false);

    companion object { fun from(key: String?, def: Icon) = values().firstOrNull { it.key == key } ?: def }
}

class IconDrawable(private val icon: Icon, private val color: Int = Color.WHITE) : Drawable() {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val r = RectF()

    init { stroke.color = color; fill.color = color }

    private fun arcs(cx: Float, cy: Float, radii: FloatArray, start: Float, sweep: Float, c: Canvas) {
        for (rad in radii) { r.set(cx - rad, cy - rad, cx + rad, cy + rad); c.drawArc(r, start, sweep, false, stroke) }
    }

    private fun poly(c: Canvas, p: Paint, vararg xy: Float, close: Boolean = false) {
        path.reset(); path.moveTo(xy[0], xy[1])
        var i = 2
        while (i < xy.size) { path.lineTo(xy[i], xy[i + 1]); i += 2 }
        if (close) path.close()
        c.drawPath(path, p)
    }

    /** A filled triangle whose corners are softened by a round-joined stroke of the same colour. */
    private fun rounded(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        path.reset(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close()
        c.drawPath(path, fill)
        val w = stroke.strokeWidth; stroke.strokeWidth = 2.2f; c.drawPath(path, stroke); stroke.strokeWidth = w
    }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        c.save()
        c.translate(b.left.toFloat(), b.top.toFloat())
        c.scale(b.width() / 24f, b.height() / 24f)
        when (icon) {
            Icon.WIFI -> { arcs(12f, 19f, floatArrayOf(4.5f, 9f, 13.5f), 225f, 90f, c); c.drawCircle(12f, 19f, 1.7f, fill) }
            Icon.BLUETOOTH -> poly(c, stroke, 7f, 8f, 17f, 16f, 12f, 21f, 12f, 3f, 17f, 8f, 7f, 16f)
            Icon.HEADPHONES -> {
                r.set(5f, 4f, 19f, 18f); c.drawArc(r, 180f, 180f, false, stroke)
                r.set(4f, 12f, 8f, 20f); c.drawRoundRect(r, 1.5f, 1.5f, fill)
                r.set(16f, 12f, 20f, 20f); c.drawRoundRect(r, 1.5f, 1.5f, fill)
            }
            Icon.HOTSPOT -> {
                c.drawCircle(12f, 12f, 2f, fill)
                for (rad in floatArrayOf(5.5f, 9.5f)) {
                    r.set(12 - rad, 12 - rad, 12 + rad, 12 + rad)
                    c.drawArc(r, -50f, 100f, false, stroke); c.drawArc(r, 130f, 100f, false, stroke)
                }
            }
            Icon.VPN -> {
                path.reset(); path.moveTo(12f, 3f); path.lineTo(19f, 6f); path.lineTo(19f, 12f)
                path.cubicTo(19f, 16.5f, 16f, 19.5f, 12f, 21f); path.cubicTo(8f, 19.5f, 5f, 16.5f, 5f, 12f)
                path.lineTo(5f, 6f); path.close(); c.drawPath(path, stroke)
                c.drawCircle(12f, 10.5f, 2f, stroke); c.drawLine(12f, 12.5f, 12f, 16f, stroke)
            }
            Icon.ALARM -> {
                c.drawCircle(12f, 13f, 7f, stroke)
                c.drawLine(12f, 13f, 12f, 9f, stroke); c.drawLine(12f, 13f, 15f, 14.5f, stroke)
                c.drawLine(3.5f, 6.5f, 6.5f, 3.5f, stroke); c.drawLine(20.5f, 6.5f, 17.5f, 3.5f, stroke)
            }
            Icon.VOLUME -> {
                poly(c, fill, 4f, 9f, 8f, 9f, 13f, 5f, 13f, 19f, 8f, 15f, 4f, 15f, close = true)
                arcs(13f, 12f, floatArrayOf(4.5f, 8.5f), -45f, 90f, c)
            }
            Icon.BELL -> {
                path.reset(); path.moveTo(6f, 17f); path.lineTo(6f, 11f); r.set(6f, 5f, 18f, 17f)
                path.arcTo(r, 180f, 180f); path.lineTo(18f, 17f); path.lineTo(20f, 18.5f); path.lineTo(4f, 18.5f); path.close()
                c.drawPath(path, fill); c.drawCircle(12f, 21f, 1.5f, fill)
            }
            Icon.DND -> {
                c.drawCircle(12f, 12f, 9f, stroke); r.set(7f, 10.8f, 17f, 13.2f); c.drawRect(r, fill)
            }
            Icon.RECORD -> { c.drawCircle(12f, 12f, 5.5f, fill); c.drawCircle(12f, 12f, 9f, stroke) }
            Icon.MIC -> {
                r.set(9f, 3f, 15f, 14f); c.drawRoundRect(r, 3f, 3f, fill)
                r.set(6f, 7f, 18f, 19f); c.drawArc(r, 0f, 180f, false, stroke)
                c.drawLine(12f, 19f, 12f, 22f, stroke); c.drawLine(9f, 22f, 15f, 22f, stroke)
            }
            Icon.CAMERA -> {
                r.set(3f, 7f, 21f, 19f); c.drawRoundRect(r, 2.5f, 2.5f, stroke)
                r.set(9f, 4f, 15f, 7.5f); c.drawRoundRect(r, 1f, 1f, fill)
                c.drawCircle(12f, 13f, 3.5f, stroke)
            }
            Icon.AIRPLANE -> poly(c, fill, 12f, 2f, 14f, 9f, 22f, 14f, 22f, 16f, 14f, 13.5f, 13.5f, 19f, 16f, 20.5f,
                16f, 22f, 12f, 21f, 8f, 22f, 8f, 20.5f, 10.5f, 19f, 10f, 13.5f, 2f, 16f, 2f, 14f, 10f, 9f, close = true)
            Icon.NFC -> arcs(4f, 12f, floatArrayOf(6f, 10f, 14f), -45f, 90f, c)
            Icon.DATA -> {
                c.drawLine(8f, 20f, 8f, 5f, stroke); poly(c, stroke, 4.5f, 8.5f, 8f, 5f, 11.5f, 8.5f)
                c.drawLine(16f, 4f, 16f, 19f, stroke); poly(c, stroke, 12.5f, 15.5f, 16f, 19f, 19.5f, 15.5f)
            }
            Icon.FLASHLIGHT -> poly(c, fill, 8f, 2f, 16f, 2f, 16f, 7f, 14f, 10f, 14f, 22f, 10f, 22f, 10f, 10f, 8f, 7f, close = true)
            Icon.BOLT -> poly(c, fill, 13f, 2f, 5f, 13f, 11f, 13f, 10f, 22f, 19f, 10f, 13f, 10f, close = true)
            Icon.BATTERY -> {
                r.set(3f, 7f, 20f, 17f); c.drawRoundRect(r, 2.5f, 2.5f, stroke)
                r.set(5.5f, 9.5f, 14f, 14.5f); c.drawRoundRect(r, 1f, 1f, fill)
                r.set(20.8f, 10.5f, 22.2f, 13.5f); c.drawRect(r, fill)
            }
            Icon.CHECK -> poly(c, stroke, 5f, 12.5f, 10f, 17.5f, 19f, 7f)
            Icon.PALETTE -> {
                c.drawCircle(12f, 12f, 9f, stroke)
                c.drawCircle(8f, 10f, 1.5f, fill); c.drawCircle(12f, 7.5f, 1.5f, fill)
                c.drawCircle(16f, 10f, 1.5f, fill); c.drawCircle(8.5f, 15f, 1.5f, fill)
            }
            Icon.MUSIC -> {
                r.set(5.5f, 15f, 12f, 20.5f); c.drawOval(r, fill)
                c.drawLine(12f, 18f, 12f, 4.5f, stroke); poly(c, stroke, 12f, 4.5f, 18.5f, 7.5f)
            }
            Icon.PLAY -> rounded(c, 8.5f, 5.5f, 8.5f, 18.5f, 18.5f, 12f)
            Icon.PAUSE -> {
                r.set(7f, 5.5f, 10.5f, 18.5f); c.drawRoundRect(r, 1.4f, 1.4f, fill)
                r.set(13.5f, 5.5f, 17f, 18.5f); c.drawRoundRect(r, 1.4f, 1.4f, fill)
            }
            Icon.PREVIOUS -> {
                r.set(5.5f, 5.5f, 8.2f, 18.5f); c.drawRoundRect(r, 1.2f, 1.2f, fill)
                rounded(c, 18.5f, 5.5f, 18.5f, 18.5f, 9.5f, 12f)
            }
            Icon.NEXT -> {
                r.set(15.8f, 5.5f, 18.5f, 18.5f); c.drawRoundRect(r, 1.2f, 1.2f, fill)
                rounded(c, 5.5f, 5.5f, 5.5f, 18.5f, 14.5f, 12f)
            }
            Icon.TOUCH -> {
                c.drawCircle(12f, 12f, 2.6f, fill); c.drawCircle(12f, 12f, 6.8f, stroke)
                arcs(12f, 12f, floatArrayOf(10.6f), -35f, 70f, c); arcs(12f, 12f, floatArrayOf(10.6f), 145f, 70f, c)
            }
        }
        c.restore()
    }

    override fun setAlpha(a: Int) { stroke.alpha = a; fill.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) { stroke.colorFilter = cf; fill.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = 96
    override fun getIntrinsicHeight() = 96
}

/** Battery body that fills up when started (used for plug-in / unplug / alerts). */
class BatteryDrawable(private val level: Int, private val charging: Boolean, private val color: Int) : Drawable(), Animatable {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f; color = Color.WHITE }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = this@BatteryDrawable.color }
    private val boltP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
    private val r = RectF()
    private val path = Path()
    private var f = 1f
    private var animator: ValueAnimator? = null

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        c.save()
        c.translate(b.left.toFloat(), b.top.toFloat()); c.scale(b.width() / 24f, b.height() / 24f)
        // body (portrait-less: landscape battery)
        r.set(2.5f, 6.5f, 20f, 17.5f); c.drawRoundRect(r, 3f, 3f, body)
        r.set(21f, 10.5f, 22.5f, 13.5f); c.drawRect(r, boltP)
        val w = (level.coerceIn(0, 100) / 100f) * f * (20f - 2.5f - 3.6f)
        r.set(4.1f, 8.1f, 4.1f + w, 15.9f); c.drawRoundRect(r, 1.6f, 1.6f, fillP)
        if (charging) {
            path.reset()
            path.moveTo(12.8f, 7.2f); path.lineTo(8.6f, 12.4f); path.lineTo(11.4f, 12.4f)
            path.lineTo(10.6f, 16.8f); path.lineTo(15.2f, 11.2f); path.lineTo(12.4f, 11.2f); path.close()
            val a = boltP.alpha; boltP.alpha = (255 * f).toInt(); c.drawPath(path, boltP); boltP.alpha = a
        }
        c.restore()
    }

    override fun start() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900; startDelay = 150; interpolator = DecelerateInterpolator()
            addUpdateListener { f = it.animatedValue as Float; invalidateSelf() }
            f = 0f; start()
        }
    }
    override fun stop() { animator?.cancel(); f = 1f; invalidateSelf() }
    override fun isRunning() = animator?.isRunning == true
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = 96
    override fun getIntrinsicHeight() = 96
}
