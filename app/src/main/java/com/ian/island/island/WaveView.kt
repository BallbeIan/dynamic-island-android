package com.ian.island.island

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/** Little animated equalizer bars. Flat when paused. */
class WaveView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
    }
    private val start = SystemClock.uptimeMillis()

    var playing = false
        set(v) { field = v; invalidate() }

    var barColor: Int = Color.WHITE
        set(v) { field = v; paint.color = v; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val bars = 4
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val barW = w / (bars * 2 - 1)
        paint.strokeWidth = barW
        val t = (SystemClock.uptimeMillis() - start) / 1000f
        for (i in 0 until bars) {
            val frac = if (playing) 0.25f + 0.75f * abs(sin(t * (2.6f + i * 1.1f) + i * 0.9f)) else 0.2f
            val bh = (h - barW) * frac
            val x = barW / 2 + i * barW * 2
            canvas.drawLine(x, h / 2 - bh / 2, x, h / 2 + bh / 2, paint)
        }
        if (playing && isAttachedToWindow) postInvalidateOnAnimation()
    }
}
