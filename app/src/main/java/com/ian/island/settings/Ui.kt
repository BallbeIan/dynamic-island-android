package com.ian.island.settings

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ian.island.island.dp
import com.ian.island.island.dpi

/** Design tokens and small view helpers shared by every settings screen. */
object Ui {
    val BG = Color.rgb(13, 14, 17)
    val SURFACE = Color.rgb(24, 26, 32)
    val SURFACE2 = Color.rgb(36, 39, 47)
    val DIVIDER = Color.rgb(38, 41, 50)
    val TEXT = Color.rgb(242, 243, 245)
    val TEXT2 = Color.rgb(139, 144, 155)
    val ACCENT = Color.rgb(124, 140, 255)
    val OK = Color.rgb(52, 211, 153)
    val WARN = Color.rgb(251, 191, 36)
    val BAD = Color.rgb(248, 113, 113)

    fun round(c: Context, color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = c.dp(radiusDp)
    }

    fun ripple(content: Drawable?): Drawable =
        RippleDrawable(ColorStateList.valueOf(Color.argb(36, 255, 255, 255)), content, ColorDrawable(Color.WHITE))

    fun text(c: Context, sp: Float, color: Int, medium: Boolean = false) = TextView(c).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun chevron(c: Context, left: Boolean = false, color: Int = TEXT2) = ImageView(c).apply {
        setImageDrawable(ChevronDrawable(left, color))
        layoutParams = LinearLayout.LayoutParams(c.dpi(20), c.dpi(20))
    }

    /** Edge-to-edge: content scrolls under the bars, so pad the screen by the system-bar insets. */
    @Suppress("DEPRECATION")
    fun padForInsets(act: Activity, root: View) {
        if (Build.VERSION.SDK_INT >= 30) act.window.setDecorFitsSystemWindows(false)
        root.setOnApplyWindowInsetsListener { v, ins ->
            v.setPadding(0, ins.systemWindowInsetTop, 0, ins.systemWindowInsetBottom)
            ins
        }
        root.requestApplyInsets()
    }

    fun topBar(act: Activity, title: String): View {
        val bar = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(act.dpi(6), 0, act.dpi(16), 0)
        }
        val back = FrameLayout(act).apply {
            background = ripple(null)
            setOnClickListener { act.finish() }
            addView(chevron(act, left = true, color = TEXT).apply {
                layoutParams = FrameLayout.LayoutParams(act.dpi(24), act.dpi(24), Gravity.CENTER)
            })
        }
        bar.addView(back, LinearLayout.LayoutParams(act.dpi(48), act.dpi(48)))
        bar.addView(text(act, 20f, TEXT, medium = true).apply { text = title; isSingleLine = true },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = act.dpi(4) })
        return bar.also { it.minimumHeight = act.dpi(56) }
    }
}

class ChevronDrawable(private val left: Boolean, color: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; this.color = color; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    override fun draw(c: Canvas) {
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        p.strokeWidth = w * 0.11f
        path.reset()
        if (left) { path.moveTo(w * 0.62f, h * 0.2f); path.lineTo(w * 0.34f, h * 0.5f); path.lineTo(w * 0.62f, h * 0.8f) }
        else { path.moveTo(w * 0.38f, h * 0.2f); path.lineTo(w * 0.66f, h * 0.5f); path.lineTo(w * 0.38f, h * 0.8f) }
        c.save(); c.translate(bounds.left.toFloat(), bounds.top.toFloat()); c.drawPath(path, p); c.restore()
    }
    override fun setAlpha(a: Int) { p.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
