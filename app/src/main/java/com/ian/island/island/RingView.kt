package com.ian.island.island

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Circular progress ring. fraction < 0 draws a spinning indeterminate arc. */
class RingView(context: Context) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.argb(60, 255, 255, 255); strokeCap = Paint.Cap.ROUND
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.WHITE; strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    var fraction = 0f
        set(v) { field = v; invalidate() }
    var color: Int = Color.WHITE
        set(v) { field = v; arc.color = v; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val sw = width * 0.16f
        track.strokeWidth = sw; arc.strokeWidth = sw
        val inset = sw / 2f
        rect.set(inset, inset, width - inset, height - inset)
        canvas.drawOval(rect, track)
        if (fraction < 0f) {
            val a = (System.currentTimeMillis() % 1100L) / 1100f * 360f
            canvas.drawArc(rect, a, 100f, false, arc)
            if (isAttachedToWindow) postInvalidateOnAnimation()
        } else {
            canvas.drawArc(rect, -90f, 360f * fraction.coerceIn(0f, 1f), false, arc)
        }
    }
}
