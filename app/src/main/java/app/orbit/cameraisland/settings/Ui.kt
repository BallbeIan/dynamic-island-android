package app.orbit.cameraisland.settings

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
import app.orbit.cameraisland.island.dp
import app.orbit.cameraisland.island.dpi

/** Design tokens and small view helpers shared by every settings screen. */
object Ui {
    /** Follows the system light / dark setting; call [init] before building any screen. */
    var dark = true
        private set

    fun init(c: Context) {
        dark = when (Prefs.get(c).getString(Prefs.THEME, null)) {
            "light" -> false
            "dark" -> true
            else -> (c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) !=
                android.content.res.Configuration.UI_MODE_NIGHT_NO
        }
    }

    /** The sun / moon button for the top right corner: switches between light and dark and redraws the screen. */
    fun themeButton(act: Activity) = FrameLayout(act).apply {
        background = ripple(round(act, Color.TRANSPARENT, 24f))
        contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode"
        setOnClickListener {
            Prefs.get(act).edit().putString(Prefs.THEME, if (dark) "light" else "dark").apply()
            act.recreate()
        }
        addView(ImageView(act).apply { setImageDrawable(ThemeIcon(dark, TEXT)) },
            FrameLayout.LayoutParams(act.dpi(24), act.dpi(24), Gravity.CENTER))
    }

    private fun pick(d: Int, l: Int) = if (dark) d else l
    val BG get() = pick(Color.rgb(13, 14, 17), Color.rgb(242, 243, 247))
    val SURFACE get() = pick(Color.rgb(24, 26, 32), Color.WHITE)
    val SURFACE2 get() = pick(Color.rgb(36, 39, 47), Color.rgb(228, 230, 236))
    val DIVIDER get() = pick(Color.rgb(38, 41, 50), Color.rgb(226, 228, 234))
    val TEXT get() = pick(Color.rgb(242, 243, 245), Color.rgb(20, 22, 28))
    val TEXT2 get() = pick(Color.rgb(139, 144, 155), Color.rgb(104, 109, 122))
    val ACCENT get() = pick(Color.rgb(124, 140, 255), Color.rgb(74, 90, 230))
    val OK = Color.rgb(52, 211, 153)
    val WARN = Color.rgb(251, 191, 36)
    val BAD = Color.rgb(248, 113, 113)
    val SWITCH_OFF_TRACK get() = pick(Color.rgb(58, 62, 74), Color.rgb(200, 204, 214))
    val SWITCH_OFF_THUMB get() = pick(Color.rgb(182, 186, 196), Color.WHITE)
    val SLIDER_TRACK get() = pick(Color.rgb(64, 68, 80), Color.rgb(208, 211, 220))

    /** Dark icons on the system bars when the screen is light. */
    @Suppress("DEPRECATION")
    fun systemBars(act: Activity) {
        val w = act.window
        w.setBackgroundDrawable(ColorDrawable(BG))
        w.statusBarColor = Color.TRANSPARENT; w.navigationBarColor = Color.TRANSPARENT
        var f = w.decorView.systemUiVisibility
        val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        f = if (dark) f and light.inv() else f or light
        w.decorView.systemUiVisibility = f
    }

    fun round(c: Context, color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = c.dp(radiusDp)
    }

    fun ripple(content: Drawable?): Drawable =
        RippleDrawable(ColorStateList.valueOf(if (dark) Color.argb(36, 255, 255, 255) else Color.argb(28, 0, 0, 0)), content, ColorDrawable(Color.WHITE))

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
        systemBars(act)
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
            setPadding(act.dpi(6), 0, act.dpi(10), 0)
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
        bar.addView(themeButton(act), LinearLayout.LayoutParams(act.dpi(48), act.dpi(48)))
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

/** Sun (shown in dark mode: tap for light) or moon (shown in light mode: tap for dark). */
class ThemeIcon(private val dark: Boolean, color: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeCap = Paint.Cap.ROUND }
    override fun draw(c: Canvas) {
        val w = bounds.width().toFloat(); val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
        if (dark) {
            p.style = Paint.Style.FILL
            c.drawCircle(cx, cy, w * 0.2f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.08f
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val (x, y) = Math.cos(a).toFloat() to Math.sin(a).toFloat()
                c.drawLine(cx + x * w * 0.32f, cy + y * w * 0.32f, cx + x * w * 0.45f, cy + y * w * 0.45f, p)
            }
        } else {
            p.style = Paint.Style.FILL
            val moon = Path().apply { addCircle(cx, cy, w * 0.4f, Path.Direction.CW) }
            val bite = Path().apply { addCircle(cx + w * 0.22f, cy - w * 0.16f, w * 0.34f, Path.Direction.CW) }
            moon.op(bite, Path.Op.DIFFERENCE)
            c.drawPath(moon, p)
        }
    }
    override fun setAlpha(a: Int) { p.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
