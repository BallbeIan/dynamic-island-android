package com.ian.island.settings

import android.app.Activity
import android.app.WallpaperManager
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.View
import com.ian.island.island.dp
import com.ian.island.island.vivid

/** Live preview of the island's look, redrawn whenever a setting changes. */
class PillPreview(private val act: Activity) : View(act), SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs = Prefs.get(act)
    private val scopeKey: String? by lazy { ScreenProfile.scopeOrNull(act) }
    private val sp = ScreenPrefs(prefs) { scopeKey }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private fun dp(v: Float) = act.dp(v)

    override fun onAttachedToWindow() { super.onAttachedToWindow(); prefs.registerOnSharedPreferenceChangeListener(this) }
    override fun onDetachedFromWindow() { prefs.unregisterOnSharedPreferenceChangeListener(this); super.onDetachedFromWindow() }
    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) = invalidate()

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), dp(120f).toInt())

    private fun accent(): Int {
        if (sp.bool(Prefs.WALLPAPER_ACCENT, false) && Build.VERSION.SDK_INT >= 27) {
            try {
                WallpaperManager.getInstance(act).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
                    ?.let { return vivid(it) }
            } catch (_: Exception) {}
        }
        return Color.rgb(48, 209, 88)
    }

    override fun onDraw(c: Canvas) {
        // fake wallpaper
        paint.shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
            Color.rgb(60, 70, 110), Color.rgb(120, 70, 100), android.graphics.Shader.TileMode.CLAMP)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        c.drawRoundRect(rect, dp(16f), dp(16f), paint)
        paint.shader = null

        val h = dp(sp.int(Prefs.PILL_HEIGHT, 36).toFloat())
        val w = dp(sp.int(Prefs.WIDTH, Prefs.DEFAULT_WIDTH).toFloat()).coerceAtMost(width * 0.7f)
        val rad = sp.int(Prefs.CORNER, 100) / 100f * h / 2f
        val double = sp.bool(Prefs.DOUBLE_PILL, true)
        val gap = dp(sp.int(Prefs.DOUBLE_GAP, 6).toFloat())
        val left = sp.str(Prefs.DOUBLE_SIDE, "right") == "left"
        val extra = if (double) h + gap else 0f
        val bg = sp.int(Prefs.BG_COLOR, Color.BLACK)
        val alpha = sp.int(Prefs.BG_ALPHA, 100) * 255 / 100
        val acc = accent()
        val outline = sp.str(Prefs.OUTLINE, "none") == "accent"
        val glow = sp.bool(Prefs.GLOW, false)

        val total = w + extra
        val dx = dp(sp.int(Prefs.XOFFSET, 0).toFloat()).coerceIn(-width / 4f, width / 4f)
        var x = (width - total) / 2f + dx + if (double && left) extra else 0f
        val top = dp(14f) + dp(sp.int(Prefs.OFFSET, Prefs.DEFAULT_OFFSET).toFloat()).coerceAtMost(dp(40f)) / 2f

        fun body(l: Float, wd: Float, r: Float) {
            rect.set(l, top, l + wd, top + h)
            if (glow) { paint.style = Paint.Style.FILL; paint.color = Color.argb(70, Color.red(acc), Color.green(acc), Color.blue(acc))
                val g = RectF(rect); g.inset(-dp(5f), -dp(5f)); c.drawRoundRect(g, r + dp(5f), r + dp(5f), paint) }
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(alpha, Color.red(bg), Color.green(bg), Color.blue(bg))
            c.drawRoundRect(rect, r, r, paint)
            if (outline) { paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f); paint.color = acc; c.drawRoundRect(rect, r, r, paint) }
            paint.style = Paint.Style.FILL
        }
        body(x, w, rad)
        // art circle + equalizer bars
        paint.color = acc
        c.drawCircle(x + h / 2f, top + h / 2f, h * 0.33f, paint)
        for (i in 0 until 4) {
            val bh = h * (0.25f + 0.12f * ((i * 3) % 4))
            rect.set(x + w - dp(14f) + i * dp(3.4f) - dp(6f), top + h / 2 - bh / 2, x + w - dp(14f) + i * dp(3.4f) - dp(4.4f), top + h / 2 + bh / 2)
            c.drawRoundRect(rect, dp(1f), dp(1f), paint)
        }
        if (double) {
            val sx = if (left) x - gap - h else x + w + gap
            body(sx, h, h / 2f * (sp.int(Prefs.CORNER, 100) / 100f).coerceAtLeast(0.15f))
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2.4f); paint.color = Color.rgb(255, 159, 10)
            rect.set(sx + h * 0.28f, top + h * 0.28f, sx + h * 0.72f, top + h * 0.72f)
            c.drawArc(rect, -90f, 250f, false, paint)
            paint.style = Paint.Style.FILL
        }
    }
}
