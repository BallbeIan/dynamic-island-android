package com.ian.island.island

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.TextView
import kotlin.math.roundToInt

fun Context.dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
fun Context.dpi(v: Int): Int = dp(v.toFloat()).roundToInt()

val GRAY_TEXT = Color.argb(170, 255, 255, 255)
val GREEN = Color.rgb(48, 209, 88)
val RED = Color.rgb(255, 69, 58)
val BLUE = Color.rgb(10, 132, 255)
val ORANGE = Color.rgb(255, 159, 10)
val YELLOW = Color.rgb(255, 214, 10)
val PURPLE = Color.rgb(191, 90, 242)

fun styleText(v: TextView, sizeSp: Float, color: Int, bold: Boolean = false) {
    v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    v.setTextColor(color)
    v.isSingleLine = true
    v.ellipsize = TextUtils.TruncateAt.END
    if (bold) v.typeface = Typeface.DEFAULT_BOLD
}

/** Clips a view to a circle (or rounded rect) with a grey placeholder background. */
fun roundClip(v: View, radius: Float, oval: Boolean = false) {
    v.background = GradientDrawable().apply {
        setColor(Color.DKGRAY)
        if (oval) shape = GradientDrawable.OVAL else cornerRadius = radius
    }
    v.outlineProvider = ViewOutlineProvider.BACKGROUND
    v.clipToOutline = true
}

fun fmtClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}

fun timerText(t: Trailing.Timer, now: Long = System.currentTimeMillis()): String =
    fmtClock(if (t.countDown) (t.baseMs - now).coerceAtLeast(0) else now - t.baseMs)

/** Strong, readable accent colour from an arbitrary colour. */
fun vivid(color: Int): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    hsv[1] = maxOf(hsv[1], 0.45f)
    hsv[2] = maxOf(hsv[2], 0.9f)
    return Color.HSVToColor(hsv)
}
