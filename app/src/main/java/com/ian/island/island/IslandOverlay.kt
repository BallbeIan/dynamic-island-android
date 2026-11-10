package com.ian.island.island

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.ian.island.gestures.Gesture
import com.ian.island.gestures.GestureAction
import com.ian.island.gestures.PillTouchHandler
import com.ian.island.settings.Prefs
import com.ian.island.settings.ScreenPrefs
import com.ian.island.settings.ScreenProfile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The little round pill that appears beside the main one when two activities are live. */
private class SecondaryPill(val ctx: Context) : FrameLayout(ctx) {
    private val bg = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    private val icon = ImageView(ctx)
    private val ring = RingView(ctx)
    var activityId: String? = null

    init {
        background = bg
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = false
        icon.scaleType = ImageView.ScaleType.CENTER_CROP
        roundClip(icon, 0f, oval = true)
        val pad = ctx.dpi(7)
        addView(icon, LayoutParams(-1, -1).apply { setMargins(pad, pad, pad, pad) })
        addView(ring, LayoutParams(-1, -1).apply { setMargins(ctx.dpi(2), ctx.dpi(2), ctx.dpi(2), ctx.dpi(2)) })
        ring.visibility = GONE
        isClickable = true
        setOnClickListener { activityId?.let { IslandController.swapTo(it) } }
    }

    fun bind(a: IslandActivity, accent: Int, bgColor: Int, stroke: Int) {
        activityId = a.id
        bg.setColor(bgColor)
        bg.setStroke(if (stroke != 0) ctx.dpi(2) else 0, stroke)
        icon.setImageDrawable(a.icon?.invoke() ?: ColorDrawable(Color.DKGRAY))
        val t = a.trailing
        if (t is Trailing.Ring) { ring.visibility = VISIBLE; ring.color = accent; ring.fraction = t.fraction } else ring.visibility = GONE
    }
}

/**
 * The floating island: owns its window and all of its views.
 * Modes: HIDDEN -> COLLAPSED (small pill) <-> EXPANDED, plus BANNER (transient/announce).
 * [windowType] is TYPE_ACCESSIBILITY_OVERLAY when hosted by the accessibility service,
 * otherwise TYPE_APPLICATION_OVERLAY.
 */
class IslandOverlay(private val ctx: Context, private val windowType: Int) {

    enum class Mode { HIDDEN, COLLAPSED, EXPANDED, BANNER }

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val prefs = Prefs.get(ctx)
    /** Position / shape / colour settings, kept separately per screen on a foldable. */
    private var scopeKey: String? = ScreenProfile.scopeOrNull(ctx)
    private val sp = ScreenPrefs(prefs) { scopeKey }
    private val handler = Handler(Looper.getMainLooper())

    private var attached = false
    private var hiding = false
    private var paused = false
    private var suppressed = false
    var mode = Mode.HIDDEN
        private set
    private var primary: IslandActivity? = null
    private var secondary: IslandActivity? = null
    private var dots: List<Int> = emptyList()
    private var anim: ValueAnimator? = null
    private var wallpaperAccent: Int? = null
    private var showing: View? = null

    // camera cutout (px), learned from the window insets once attached
    private var cutoutHalf = 0
    private var cutoutBottom = 0

    // current animated geometry
    private var curW = 0
    private var curH = 0
    private var curR = 0f
    private var curL = 0
    private var curRt = 0

    // ---- Views ----
    private val root = FrameLayout(ctx)
    private val row = LinearLayout(ctx)
    private val leftSlot = FrameLayout(ctx)
    private val rightSlot = FrameLayout(ctx)
    private val leftSec = SecondaryPill(ctx)
    private val rightSec = SecondaryPill(ctx)
    private val pill = FrameLayout(ctx)
    private val pillBg = GradientDrawable().apply { setColor(Color.BLACK) }
    private val collapsed = CollapsedView(ctx)
    /** Off-screen twin used only to measure how wide another activity's collapsed pill would be. */
    private val probe = CollapsedView(ctx)
    private val banner = BannerView(ctx) { setFocusable(true) }
    private val expandedHost = FrameLayout(ctx)
    private val mediaExp = MediaExpandedView(ctx)
    private val genericExp = GenericExpandedView(ctx, { setFocusable(true) }, { setFocusable(false) })

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        windowType,
        BASE_FLAGS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        title = "Island"
    }

    private val ticker = object : Runnable {
        override fun run() {
            collapsed.tick(); genericExp.tick(); mediaExp.tick()
            if (needsTicker()) handler.postDelayed(this, 500)
        }
    }
    private val bannerEnd = Runnable {
        val p = primary ?: return@Runnable
        if (mode == Mode.BANNER && !p.isTransient) setMode(Mode.COLLAPSED, false)
    }

    private val wallpaperListener = WallpaperManager.OnColorsChangedListener { _, _ ->
        handler.post { refreshAppearance() }
    }

    init {
        buildViews()
        refreshWallpaper()
        if (Build.VERSION.SDK_INT >= 27) {
            try {
                WallpaperManager.getInstance(ctx).addOnColorsChangedListener(wallpaperListener, handler)
            } catch (_: Exception) {}
        }
    }

    // ================= Public API =================

    fun render(p: IslandActivity?, s: IslandActivity?, dotColors: List<Int>, announce: Boolean) {
        val oldP = primary
        val oldS = secondary
        val wantSwap = swapPending
        swapPending = false
        secondary = s
        dots = dotColors
        if (swapping) { primary = p; return }          // a swap animation is running; it finishes with the latest state
        if (p == null) {
            primary = null
            hide()
            return
        }
        val wasHidden = mode == Mode.HIDDEN || hiding
        val changed = primary?.id != p.id
        primary = p
        if (wantSwap && trySwap(oldP, oldS, p, s)) return
        bindContent(p)

        val target = when {
            wasHidden || changed || announce -> entryMode(p, announce)
            else -> mode
        }
        if (suppressed) {
            // tucked into the camera: remember the state; the drop spreads out again when un-suppressed
            mode = target
            attach()
            return
        }
        setMode(target, contentChanged = changed || announce || wasHidden)
    }

    private fun entryMode(p: IslandActivity, announce: Boolean): Mode {
        if (announce) {
            return when (p.presentation) {
                Presentation.EXPANDED -> Mode.EXPANDED
                Presentation.BANNER -> Mode.BANNER
                Presentation.COLLAPSED -> if (p.isTransient) Mode.BANNER else Mode.COLLAPSED
            }
        }
        return if (p.isTransient) Mode.BANNER else Mode.COLLAPSED
    }

    /** Diameter of the "drop": about the camera hole, so it appears to emerge from / slip into it. */
    private fun dropSize(): Int {
        val d = if (cutoutHalf > 0) (cutoutHalf - ctx.dpi(4)) * 2 else ctx.dpi(14)
        return d.coerceIn(ctx.dpi(10), pillHeight())
    }

    /**
     * Water-drop exit: the pill pinches into a round drop centred on the camera (width a touch ahead of
     * height), then the drop slips into the hole. [onEnd] runs when it has fully gone (or was interrupted).
     */
    private fun dropOut(onEnd: () -> Unit) {
        anim?.cancel()
        pill.animate().cancel()
        val h = pillHeight()
        val d = dropSize()
        val fw = curW; val fh = curH; val fr = curR; val fl = curL; val frt = curRt
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                val wf = min(1f, f * 1.3f)
                curW = max(1, (fw + (d - fw) * wf).roundToInt())
                curH = max(1, (fh + (d - fh) * f).roundToInt())
                curR = fr + (d / 2f - fr) * f
                curL = max(0, (fl * (1f - f)).roundToInt())
                curRt = max(0, (frt * (1f - f)).roundToInt())
                applyGeometry(h)
                val a = (1f - f * 2.2f).coerceIn(0f, 1f)
                collapsed.alpha = a; expandedHost.alpha = a; banner.alpha = a
                leftSec.alpha = a; rightSec.alpha = a
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(an: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(an: android.animation.Animator) {
                    if (cancelled) return
                    // the drop slips into the camera
                    pill.animate().scaleX(0f).scaleY(0f).setDuration(120).withEndAction { onEnd() }.start()
                }
            })
            start()
        }
    }

    /** Hide: the island drops into the camera. Show: a drop comes back out and spreads into the pill. */
    fun setSuppressed(s: Boolean) {
        if (suppressed == s) return
        suppressed = s
        applyTouchable()
        if (s) {
            if (!attached || mode == Mode.HIDDEN || hiding) { root.visibility = View.INVISIBLE; return }
            dropOut { if (suppressed) root.visibility = View.INVISIBLE }
        } else {
            root.visibility = View.VISIBLE
            resetPillTransform()
            if (attached && primary != null && mode != Mode.HIDDEN) setMode(mode, contentChanged = true, fromDrop = true)
        }
    }

    // ================= Swapping the two pills =================

    private var swapPending = false
    private var swapping = false

    /** Called right before the controller re-orders after the user tapped the small pill. */
    fun noteSwap() { swapPending = true }

    /** The stand-in used while a pill travels: shape + icon only, sized exactly like the real views. */
    private inner class Ghost(icon: android.graphics.drawable.Drawable?) : FrameLayout(ctx) {
        private val bg = GradientDrawable()
        private val iconView = ImageView(ctx)

        init {
            background = bg
            bg.setColor(bgColor())
            val stroke = strokeColor(primary)
            bg.setStroke(if (stroke != 0) ctx.dpi(2) else 0, stroke)
            iconView.scaleType = ImageView.ScaleType.CENTER_CROP
            roundClip(iconView, 0f, oval = true)
            iconView.setImageDrawable(icon ?: ColorDrawable(Color.DKGRAY))
            addView(iconView, LayoutParams(ctx.dpi(24), ctx.dpi(24), Gravity.START or Gravity.CENTER_VERTICAL))
        }

        /**
         * [hh] = current height (smaller than the normal pill height while it is still a drop), centred on the
         * camera line. [k] = how pill-like it is: 1 = pill (24dp icon at the left), 0 = circle (icon inset, centred).
         */
        fun place(w: Int, hh: Int, centerOffset: Float, k: Float, iconAlpha: Float = 1f) {
            val full = pillHeight()
            layoutParams = LayoutParams(w, hh, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = max(0, (full - hh) / 2) }
            translationX = centerOffset
            bg.cornerRadius = min(w, hh) / 2f * roundness()
            val circleIcon = max(ctx.dpi(10), min(hh, full) - ctx.dpi(14))
            val size = (circleIcon + (ctx.dpi(24) - circleIcon) * k).roundToInt()
            iconView.layoutParams = LayoutParams(size, size, Gravity.START or Gravity.CENTER_VERTICAL)
            iconView.translationX = (w - size) / 2f * (1f - k) + ctx.dp(6f) * k
            iconView.alpha = iconAlpha
        }
    }

    /** Width of the collapsed pill for [p], measuring its trailing content on [probe]. */
    private fun collapsedWidth(p: IslandActivity, probe: CollapsedView, h: Int): Int {
        val screenW = ctx.resources.displayMetrics.widthPixels
        val secExtra = if (secondary != null && sp.bool(Prefs.DOUBLE_PILL, true)) h + ctx.dpi(sp.int(Prefs.DOUBLE_GAP, 6)) else 0
        val wide = min(screenW - ctx.dpi(16) - 2 * secExtra, ctx.dpi(420))
        val pref = min(ctx.dpi(sp.int(Prefs.WIDTH, Prefs.DEFAULT_WIDTH)), wide)
        if (p.type == ActivityType.IDLE) return ctx.dpi(96)
        val need = 2 * (cutoutHalf + probe.trailingWidthPx() + ctx.dpi(10))
        return min(max(pref, need), wide)
    }

    /**
     * The two pills trade places in three beats: the small pill shrinks inward and disappears; the main pill slides
     * to its side and shrinks into the small pill; the other activity then spreads out of the camera.
     * Returns false (and does nothing) when the situation isn't a plain collapsed <-> collapsed swap.
     */
    private fun trySwap(oldP: IslandActivity?, oldS: IslandActivity?, p: IslandActivity, s: IslandActivity?): Boolean {
        val h = pillHeight()
        if (oldP == null || oldS == null || s == null) return false
        if (p.id != oldS.id || s.id != oldP.id) return false
        if (mode != Mode.COLLAPSED || !attached || hiding || suppressed || p.isTransient) return false
        if (!sp.bool(Prefs.DOUBLE_PILL, true) || curH != h) return false

        val sign = if (sp.str(Prefs.DOUBLE_SIDE, "right") == "left") -1f else 1f
        val gap = ctx.dpi(sp.int(Prefs.DOUBLE_GAP, 6)).toFloat()
        val oldW = curW
        swapping = true
        anim?.cancel()

        // final width of the incoming main pill, measured on a throwaway view (the real one stays untouched)
        probe.bindTrailing(p, accentOf(p), dots)
        val finalW = collapsedWidth(p, probe, h)
        fun secOffset(pillW: Int) = sign * (pillW / 2f + gap + h / 2f)

        var started = false

        fun begin() {
            if (started) return
            started = true
            // the small pill's ghost is identical to the real one, so the real one steps aside at once; the main
            // pill cross-dissolves into its ghost (so its timer / label fades instead of popping off), and the new
            // content is bound only once the real pill is fully dissolved
            leftSec.alpha = 0f; rightSec.alpha = 0f
            var bound = false

            val ghostA = Ghost(oldP.icon?.invoke())      // old main pill  -> shrinks into the small pill
            val ghostB = Ghost(oldS.icon?.invoke())      // old small pill -> shrinks away and disappears
            val ghostC = Ghost(p.icon?.invoke())         // new main pill  -> spreads out of the camera
            val drop = dropSize()
            val sideOff = secOffset(finalW)
            ghostA.place(oldW, h, 0f, 1f); ghostA.alpha = 0f
            ghostB.place(h, h, secOffset(oldW), 0f)
            ghostC.place(drop, drop, 0f, 0f, 0f)
            ghostC.visibility = View.INVISIBLE
            // draw order: B (leaves first), C (emerges), A on top while it crosses
            root.addView(ghostB); root.addView(ghostC); root.addView(ghostA)

            // timeline (fractions of the whole): 1) B shrinks away  2) A slides out and shrinks into the circle
            // 3) C spreads out of the camera like a drop
            fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
            val drop3 = OvershootInterpolator(1.4f)
            val shrink1 = AccelerateInterpolator(1.4f)

            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 700
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val t = it.animatedValue as Float
                    val dissolve = seg(t, 0f, 0.08f)
                    ghostA.alpha = dissolve
                    pill.alpha = 1f - dissolve
                    if (dissolve >= 1f && !bound) { bound = true; bindContent(p) }

                    // 1) small pill shrinks inward and disappears
                    val b = shrink1.getInterpolation(seg(t, 0f, 0.2f))
                    ghostB.scaleX = 1f - b; ghostB.scaleY = 1f - b; ghostB.alpha = 1f - b
                    if (b >= 1f) ghostB.visibility = View.INVISIBLE

                    // 2) main pill moves to the side and shrinks into the small pill
                    val f = SPRING.getInterpolation(seg(t, 0.1f, 0.55f))
                    val k = f.coerceIn(0f, 1f)
                    ghostA.place((oldW + (h - oldW) * f).roundToInt().coerceAtLeast(h), h, sideOff * f, 1f - k)

                    // 3) the other activity spreads out from the camera
                    val u = seg(t, 0.3f, 1f)
                    if (u > 0f) {
                        ghostC.visibility = View.VISIBLE
                        val g = drop3.getInterpolation(u)
                        val w = (drop + (finalW - drop) * g).roundToInt().coerceAtLeast(drop)
                        val hh = (drop + (h - drop) * g).roundToInt().coerceIn(drop, h + ctx.dpi(4))
                        ghostC.place(w, hh, 0f, g.coerceIn(0f, 1f), seg(u, 0.35f, 0.9f))
                    }
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(an: android.animation.Animator) {
                        swapping = false
                        if (!bound) { bound = true; primary?.let { bindContent(it) } }
                        // real views take over at the final geometry (underneath the ghosts), then the ghosts
                        // fade away so the real content (trailing wave, ring...) is revealed smoothly
                        if (primary != null && attached) {
                            leftSec.alpha = 1f; rightSec.alpha = 1f
                            setMode(Mode.COLLAPSED, contentChanged = false)
                            anim?.end()
                        }
                        pill.alpha = 1f
                        val ghosts = listOf(ghostA, ghostB, ghostC)
                        ghosts.forEach { g ->
                            g.animate().alpha(0f).setDuration(170).withEndAction {
                                root.removeView(g)
                            }.start()
                        }
                    }
                })
                start()
            }
        }

        begin()
        return true
    }

    fun setPaused(p: Boolean) {
        paused = p
        if (p) handler.removeCallbacks(ticker) else startTickerIfNeeded()
    }

    fun toggleExpand() {
        val p = primary ?: return
        when (mode) {
            Mode.COLLAPSED, Mode.BANNER ->
                if (p.expandable) setMode(Mode.EXPANDED, false, byUser = true) else p.onOpen?.invoke()
            Mode.EXPANDED -> collapse()
            Mode.HIDDEN -> {}
        }
    }

    /** Expanded -> collapsed (transients are dismissed instead). */
    fun collapse() {
        val p = primary ?: return
        if (mode != Mode.EXPANDED) return
        if (p.isTransient) IslandController.remove(p.id) else setMode(Mode.COLLAPSED, false)
    }

    fun collapseBanner() { if (mode == Mode.BANNER) setMode(Mode.COLLAPSED, false) }

    fun updateOffset() {
        params.y = windowY()
        params.x = targetX(curW + curL + curRt + 2 * glowPad())
        if (attached) try { wm.updateViewLayout(root, params) } catch (_: Exception) {}
    }

    fun refreshAppearance() {
        scopeKey = ScreenProfile.scopeOrNull(ctx)
        refreshWallpaper()      // a binder call, so only here and not on every render
        applyAppearance()
        updateOffset()
        primary?.let { bindContent(it); setMode(if (mode == Mode.HIDDEN) Mode.HIDDEN else mode, contentChanged = false) }
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        if (Build.VERSION.SDK_INT >= 27) {
            try { WallpaperManager.getInstance(ctx).removeOnColorsChangedListener(wallpaperListener) } catch (_: Exception) {}
        }
        detach()
        mode = Mode.HIDDEN
    }

    // camera, as located for the screen the island is on (see CameraLocator)
    private var camOffsetPx = 0           // camera centre relative to the screen centre
    private var camKnown = false

    private var screenW = CameraLocator.screenWidth(ctx, wm)
    private var screenSignature = 0L

    /**
     * Re-detects the camera after the screen changes (rotation, fold / unfold, resolution). This is called on every
     * window-insets update, which during an animation means every frame, so it returns at once when nothing changed.
     */
    @Suppress("DEPRECATION")
    private fun refreshCamera() {
        val dm = ctx.resources.displayMetrics
        val sig = (dm.widthPixels.toLong() shl 40) xor (dm.heightPixels.toLong() shl 20) xor
            (dm.densityDpi.toLong() shl 4) xor (wm.defaultDisplay?.rotation ?: 0).toLong()
        if (sig == screenSignature) return
        screenSignature = sig
        scopeKey = ScreenProfile.scopeOrNull(ctx)
        screenW = CameraLocator.screenWidth(ctx, wm)
        val cam = try { CameraLocator.locate(ctx, wm) } catch (_: Throwable) { null }
        val half = if (cam != null) cam.width / 2 + ctx.dpi(4) else 0
        val bottom = cam?.bottom ?: 0
        val off = if (cam != null) cam.centerX - screenW / 2 else 0
        val known = cam != null
        if (half == cutoutHalf && bottom == cutoutBottom && off == camOffsetPx && known == camKnown) return
        cutoutHalf = half; cutoutBottom = bottom; camOffsetPx = off; camKnown = known
        handler.post { if (attached) { updateOffset(); refreshAppearance() } }
    }

    /**
     * Horizontal window offset from the screen centre: the detected camera (if auto-align is on) plus the user's
     * fine-tune, clamped so a pill of [rowW] pixels never leaves the screen.
     */
    private fun targetX(rowW: Int): Int {
        val raw = (if (camKnown && sp.bool(Prefs.AUTO_CAMERA, true)) camOffsetPx else 0) + ctx.dpi(sp.int(Prefs.XOFFSET, 0))
        val limit = ((screenW - rowW) / 2 - ctx.dpi(4)).coerceAtLeast(0)
        return raw.coerceIn(-limit, limit)
    }

    /** Extra top padding so banner / expanded content sits below the camera hole. */
    private fun topInset(): Int =
        if (cutoutBottom == 0) 0 else max(0, cutoutBottom + ctx.dpi(6) - ctx.dpi(sp.int(Prefs.OFFSET, Prefs.DEFAULT_OFFSET)))

    // ================= Appearance =================

    private fun pillHeight() = ctx.dpi(sp.int(Prefs.PILL_HEIGHT, 36))
    private fun roundness() = sp.int(Prefs.CORNER, 100) / 100f
    private fun glowPad() = if (sp.bool(Prefs.GLOW, false)) ctx.dpi(14) else 0
    private fun windowY() = ctx.dpi(sp.int(Prefs.OFFSET, Prefs.DEFAULT_OFFSET)) - glowPad()

    private fun bgColor(): Int {
        val base = sp.int(Prefs.BG_COLOR, Color.BLACK)
        val a = (sp.int(Prefs.BG_ALPHA, 100) * 255 / 100).coerceIn(0, 255)
        return Color.argb(a, Color.red(base), Color.green(base), Color.blue(base))
    }

    private fun refreshWallpaper() {
        wallpaperAccent = if (Build.VERSION.SDK_INT >= 27 && sp.bool(Prefs.WALLPAPER_ACCENT, false)) {
            try {
                WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                    ?.primaryColor?.toArgb()?.let { vivid(it) }
            } catch (_: Exception) { null }
        } else null
    }

    private fun accentOf(a: IslandActivity): Int =
        if (sp.bool(Prefs.WALLPAPER_ACCENT, false)) wallpaperAccent ?: a.accent else a.accent

    private fun strokeColor(a: IslandActivity?): Int =
        if (sp.str(Prefs.OUTLINE, "none") == "accent" && a != null) accentOf(a) else 0

    private fun applyAppearance() {
        val a = primary
        pillBg.setColor(bgColor())
        val stroke = strokeColor(a)
        pillBg.setStroke(if (stroke != 0) ctx.dpi(2) else 0, stroke)
        val pad = glowPad()
        root.setPadding(pad, pad, pad, pad)
        if (pad > 0 && a != null) {
            pill.elevation = ctx.dp(10f)
            if (Build.VERSION.SDK_INT >= 28) {
                pill.outlineAmbientShadowColor = accentOf(a)
                pill.outlineSpotShadowColor = accentOf(a)
            }
        } else pill.elevation = 0f
    }

    private fun radiusFor(m: Mode, h: Int): Float =
        roundness() * if (m == Mode.COLLAPSED || m == Mode.HIDDEN) h / 2f else ctx.dp(30f)

    // ================= Modes & animation =================

    private fun setMode(m: Mode, contentChanged: Boolean, byUser: Boolean = false, fromDrop: Boolean = false) {
        if (m == Mode.HIDDEN) { hide(); return }
        if (hiding) {
            hiding = false
            pill.animate().cancel()
            resetPillTransform()
        }
        if (!attach()) return
        applyAppearance()

        val prev = mode
        val p = primary ?: return
        mode = m
        if (prev == Mode.EXPANDED && m != Mode.EXPANDED) setFocusable(false)
        if (prev != m) {
            IslandController.onOverlayExpanded(p.id, m == Mode.EXPANDED, byUser)
        }

        val h = pillHeight()
        val (tw, th) = targetSize(m, p, h)
        val secW = h + ctx.dpi(sp.int(Prefs.DOUBLE_GAP, 6))
        // the second pill tucks away while the main one is expanded, and returns when it closes
        val showSec = m != Mode.EXPANDED && secondary != null && sp.bool(Prefs.DOUBLE_PILL, true)
        val left = sp.str(Prefs.DOUBLE_SIDE, "right") == "left"
        // Reserve the same width on both sides so the main pill stays centred on the camera. The slot is wider than
        // the circle by `spare`, sized so that  pill width + 2 * spare  is the same whichever of the two activities
        // is the main one. The window therefore never changes width when the pills swap (a step resize makes the
        // whole island lurch while Android repositions the window).
        val spare = if (showSec) spareFor(tw, secondary!!, h) else 0
        val tl = if (showSec) secW + spare else 0
        val tr2 = if (showSec) secW + spare else 0
        if (showSec) bindSecondary(secondary!!, left, h)

        // nothing changed (e.g. a navigation tick): just keep the rebound content, no animation restart
        if (prev == m && !contentChanged && prev != Mode.HIDDEN && curW == tw && curH == th &&
            curL == tl && curRt == tr2 && anim?.isRunning != true) {
            updateSlotVisibility(showSec, left)
            startTickerIfNeeded()
            return
        }

        // the content view for this mode gets its final size at once; the pill clips it while it morphs
        when (m) {
            Mode.EXPANDED -> expandedHost.layoutParams = FrameLayout.LayoutParams(tw, th, Gravity.CENTER)
            Mode.BANNER -> banner.layoutParams = FrameLayout.LayoutParams(tw, th, Gravity.CENTER)
            else -> {}
        }
        showOnly(m, p, animateIn = prev != m || contentChanged, quick = fromDrop)

        // appearing: start as a round drop at the camera and spread out
        val fromHidden = prev == Mode.HIDDEN || fromDrop
        val fw = if (fromHidden) dropSize() else curW
        val fh = if (fromHidden) dropSize() else curH
        val fr = if (fromHidden) dropSize() / 2f else curR
        val fl = if (fromHidden) 0 else curL
        val frt = if (fromHidden) 0 else curRt
        val tr = radiusFor(m, h)

        anim?.cancel()
        // Apple-style: a soft spring where the leading dimension (width when opening, height when closing)
        // gets ahead and the other follows a beat later. Appearing from the camera keeps its own overshoot.
        val opening = th >= fh
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (fromDrop) 330 else if (fromHidden) 480 else if (opening) 620 else 560
            interpolator = if (fromHidden) OvershootInterpolator(1.5f) else LinearInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                val fW: Float
                val fH: Float
                val fSlot: Float
                if (fromHidden) { fW = t; fH = t; fSlot = min(t, 1f) }
                else {
                    val lead = SPRING.getInterpolation(t)
                    val lag = SPRING.getInterpolation(((t - 0.08f) / 0.92f).coerceIn(0f, 1f))
                    fW = if (opening) lead else lag
                    fH = if (opening) lag else lead
                    fSlot = min(lead, 1f)
                }
                curW = max(1, (fw + (tw - fw) * fW).roundToInt())
                curH = max(1, (fh + (th - fh) * fH).roundToInt())
                curR = max(0f, fr + (tr - fr) * min(fH, 1f))
                curL = max(0, (fl + (tl - fl) * fSlot).roundToInt())
                curRt = max(0, (frt + (tr2 - frt) * fSlot).roundToInt())
                applyGeometry(h)
            }
            start()
        }
        updateSlotVisibility(showSec, left)

        handler.removeCallbacks(bannerEnd)
        if (m == Mode.BANNER && !p.isTransient) handler.postDelayed(bannerEnd, p.bannerMs)
        if (m == Mode.BANNER && (prev != Mode.BANNER || contentChanged)) playBannerAnim(p)
        startTickerIfNeeded()
    }

    private fun applyGeometry(h: Int) {
        val lp = pill.layoutParams as LinearLayout.LayoutParams
        lp.width = curW; lp.height = curH
        lp.topMargin = max(0, (h - curH) / 2)
        pill.layoutParams = lp
        pillBg.cornerRadius = curR
        pill.invalidateOutline()
        leftSlot.layoutParams = LinearLayout.LayoutParams(curL, h)
        rightSlot.layoutParams = LinearLayout.LayoutParams(curRt, h)
        // near a screen edge the window slides inwards as the pill grows, so it never runs off-screen
        val nx = targetX(curW + curL + curRt + 2 * glowPad())
        if (attached && nx != params.x) { params.x = nx; try { wm.updateViewLayout(root, params) } catch (_: Exception) {} }
    }

    /** Fades the second pill in/out (it tucks away while the main pill is expanded). */
    private fun updateSlotVisibility(show: Boolean, left: Boolean) {
        for ((sec, isLeft) in listOf(leftSec to true, rightSec to false)) {
            val want = show && isLeft == left
            sec.animate().cancel()
            if (want) {
                if (sec.visibility != View.VISIBLE) { sec.alpha = 0f; sec.visibility = View.VISIBLE }
                sec.animate().alpha(1f).setStartDelay(160).setDuration(200).start()
            } else if (sec.visibility == View.VISIBLE) {
                sec.animate().alpha(0f).setStartDelay(0).setDuration(110).withEndAction {
                    if (sec.alpha == 0f) sec.visibility = View.GONE
                }.start()
            }
        }
    }

    /** Invisible extra slot width on each side; see the comment in [setMode]. */
    private fun spareFor(mainW: Int, other: IslandActivity, h: Int): Int {
        probe.bindTrailing(other, accentOf(other), dots)
        val otherW = collapsedWidth(other, probe, h)
        return (max(mainW, otherW) - mainW) / 2 + ctx.dpi(12)
    }

    private fun bindSecondary(s: IslandActivity, left: Boolean, h: Int) {
        val pillView = if (left) leftSec else rightSec
        val gap = ctx.dpi(sp.int(Prefs.DOUBLE_GAP, 6))
        // the circle hugs the main pill (inner edge of its slot); the spare room is on the outside
        pillView.layoutParams = FrameLayout.LayoutParams(h, h, if (left) Gravity.END else Gravity.START).apply {
            if (left) marginEnd = gap else marginStart = gap
        }
        pillView.bind(s, accentOf(s), bgColor(), strokeColor(s))
    }

    private fun showOnly(m: Mode, p: IslandActivity, animateIn: Boolean, quick: Boolean = false) {
        val exp: View = if (p.kind == Kind.MEDIA) mediaExp else genericExp
        val target: View? = when (m) {
            Mode.COLLAPSED -> collapsed
            Mode.EXPANDED -> expandedHost
            Mode.BANNER -> banner
            Mode.HIDDEN -> null
        }
        mediaExp.visibility = if (exp === mediaExp) View.VISIBLE else View.GONE
        genericExp.visibility = if (exp === genericExp) View.VISIBLE else View.GONE
        showing = target
        for (v in listOf<View>(collapsed, expandedHost, banner)) {
            contentAnims.remove(v)?.cancel()
            if (v === target) {
                v.visibility = View.VISIBLE
                if (animateIn) animateContent(v, appear = true, quick = quick)
                else { v.alpha = 1f; v.scaleX = 1f; v.scaleY = 1f; blur(v, 0f) }
            } else if (animateIn && v.visibility == View.VISIBLE && v.alpha > 0f) {
                animateContent(v, appear = false) { if (showing !== v) v.visibility = View.GONE }
            } else {
                v.visibility = View.GONE
            }
        }
    }

    private val contentAnims = HashMap<View, ValueAnimator>()

    /** Radius in px; no-op before Android 12. */
    private fun blur(v: View, px: Float) {
        if (Build.VERSION.SDK_INT < 31) return
        v.setRenderEffect(if (px < 0.5f) null
        else android.graphics.RenderEffect.createBlurEffect(px, px, android.graphics.Shader.TileMode.CLAMP))
    }

    /**
     * Apple-style content swap. Leaving: shrinks a little, blurs and fades quickly.
     * Arriving: starts a beat later, from slightly small and blurred, and resolves into focus.
     */
    private fun animateContent(v: View, appear: Boolean, quick: Boolean = false, onEnd: (() -> Unit)? = null) {
        val maxBlur = ctx.dp(10f)
        if (appear) { v.alpha = 0f; v.scaleX = 0.86f; v.scaleY = 0.86f; blur(v, maxBlur) }
        val a = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (appear) (if (quick) 200 else 360) else 150
            startDelay = if (appear) (if (quick) 25 else 110) else 0
            interpolator = if (appear) DecelerateInterpolator(1.6f) else AccelerateInterpolator(1.3f)
            addUpdateListener {
                val p = it.animatedValue as Float
                val vis = if (appear) p else 1f - p
                v.alpha = vis
                val sc = 0.86f + 0.14f * vis
                v.scaleX = sc; v.scaleY = sc
                blur(v, (1f - vis) * maxBlur)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(an: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(an: android.animation.Animator) {
                    contentAnims.remove(v)
                    if (cancelled) return
                    if (appear) { v.alpha = 1f; v.scaleX = 1f; v.scaleY = 1f; blur(v, 0f) }
                    else { blur(v, 0f); v.scaleX = 1f; v.scaleY = 1f; onEnd?.invoke() }
                }
            })
        }
        contentAnims[v] = a
        a.start()
    }

    private fun playBannerAnim(p: IslandActivity) {
        val icon = banner.icon
        icon.animate().cancel()
        icon.scaleX = 1f; icon.scaleY = 1f; banner.translationY = 0f
        (icon.drawable as? android.graphics.drawable.Animatable)?.start()
        when (p.anim) {
            AnimStyle.POP -> {
                icon.scaleX = 0.3f; icon.scaleY = 0.3f
                icon.animate().scaleX(1f).scaleY(1f).setStartDelay(150).setDuration(380)
                    .setInterpolator(OvershootInterpolator(2.4f)).start()
            }
            AnimStyle.SLIDE -> {
                banner.translationY = -ctx.dp(24f)
                banner.animate().translationY(0f).setStartDelay(120).setDuration(320).start()
            }
            AnimStyle.PULSE -> {
                ObjectAnimator.ofPropertyValuesHolder(
                    icon,
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.28f, 1f),
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.28f, 1f),
                ).apply { duration = 520; startDelay = 150; repeatCount = 2 }.start()
            }
        }
    }

    private fun targetSize(m: Mode, p: IslandActivity, h: Int): Pair<Int, Int> {
        val screenW = ctx.resources.displayMetrics.widthPixels
        val secExtra = if (m != Mode.EXPANDED && secondary != null && sp.bool(Prefs.DOUBLE_PILL, true)) h + ctx.dpi(sp.int(Prefs.DOUBLE_GAP, 6)) else 0
        val wide = min(screenW - ctx.dpi(16) - 2 * secExtra, ctx.dpi(420))
        val collapsedW = min(ctx.dpi(sp.int(Prefs.WIDTH, Prefs.DEFAULT_WIDTH)), wide)
        return when (m) {
            Mode.COLLAPSED -> collapsedWidth(p, collapsed, h) to h
            Mode.EXPANDED -> {
                val v: View = if (p.kind == Kind.MEDIA) mediaExp else genericExp
                v.measure(View.MeasureSpec.makeMeasureSpec(wide, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                wide to max(v.measuredHeight, ctx.dpi(84))
            }
            Mode.BANNER -> {
                banner.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                val w = if (p.kind == Kind.VOLUME) wide else min(wide, max(collapsedW, banner.measuredWidth + ctx.dpi(4)))
                banner.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                w to max(banner.measuredHeight, ctx.dpi(60))
            }
            Mode.HIDDEN -> ctx.dpi(40) to h
        }
    }

    fun hide() {
        handler.removeCallbacks(bannerEnd)
        handler.removeCallbacks(ticker)
        if (!attached || hiding) { mode = Mode.HIDDEN; return }
        hiding = true
        dropOut {
            if (hiding) {
                detach()
                mode = Mode.HIDDEN
            }
            resetPillTransform()
        }
    }

    private fun attach(): Boolean {
        if (attached) return true
        if (windowType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY && !Settings.canDrawOverlays(ctx)) return false
        updateOffsetParams()
        applyTouchableFlag()
        return try {
            root.visibility = if (suppressed) View.INVISIBLE else View.VISIBLE
            wm.addView(root, params)
            attached = true
            true
        } catch (e: Exception) {
            android.util.Log.w("IslandCam", "attach failed: $e")
            false
        }
    }

    private fun updateOffsetParams() {
        refreshCamera()
        params.y = windowY()
        params.x = targetX(ctx.dpi(60) + 2 * glowPad())
    }

    private fun detach() {
        anim?.cancel()
        handler.removeCallbacks(ticker)
        hiding = false
        if (attached) {
            try { wm.removeView(root) } catch (_: Exception) {}
            attached = false
        }
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }

    private fun resetPillTransform() {
        pill.scaleX = 1f; pill.scaleY = 1f; pill.alpha = 1f
    }

    private fun applyTouchableFlag() {
        params.flags = if (suppressed) params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        else params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
    }

    private fun applyTouchable() {
        applyTouchableFlag()
        if (attached) try { wm.updateViewLayout(root, params) } catch (_: Exception) {}
    }

    /** The reply field needs a focusable window to show the keyboard. */
    private fun setFocusable(focusable: Boolean) {
        val f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val has = params.flags and f != 0
        if (focusable == !has) return
        params.flags = if (focusable) params.flags and f.inv() else params.flags or f
        if (attached) try { wm.updateViewLayout(root, params) } catch (_: Exception) {}
        if (!focusable) {
            (ctx.getSystemService(android.view.inputmethod.InputMethodManager::class.java))
                ?.hideSoftInputFromWindow(root.windowToken, 0)
            genericExp.clearReply()
        }
    }

    // ---- ticker ----

    private fun needsTicker(): Boolean {
        val p = primary ?: return false
        if (!attached || paused || hiding) return false
        return (mode == Mode.EXPANDED && (p.kind == Kind.MEDIA || genericExp.hasTimer)) ||
            (mode != Mode.EXPANDED && collapsed.hasTimer)
    }

    private fun startTickerIfNeeded() {
        handler.removeCallbacks(ticker)
        if (needsTicker()) handler.post(ticker)
    }

    // ================= Binding =================

    private fun bindContent(p: IslandActivity) {
        val accent = accentOf(p)
        val inset = topInset()
        banner.setTopInset(inset); mediaExp.setTopInset(inset); genericExp.setTopInset(inset)
        collapsed.bind(p, accent, dots)
        banner.bind(p, accent)
        if (p.kind == Kind.MEDIA) mediaExp.bind(p, accent) else genericExp.bind(p, accent)
        mediaExp.header.setOnClickListener { p.onOpen?.invoke() }
        applyAppearance()
    }

    // ================= View construction =================

    private fun buildViews() {
        root.clipChildren = false
        root.clipToPadding = false
        root.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) collapse()
            false
        }
        root.setOnApplyWindowInsetsListener { v, insets ->
            refreshCamera()
            v.onApplyWindowInsets(insets)
        }

        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.TOP
        row.clipChildren = false
        root.addView(row, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL))

        leftSlot.clipChildren = false
        rightSlot.clipChildren = false
        leftSlot.addView(leftSec)
        rightSlot.addView(rightSec)
        leftSec.visibility = View.GONE
        rightSec.visibility = View.GONE

        pill.background = pillBg
        pill.outlineProvider = ViewOutlineProvider.BACKGROUND
        pill.clipToOutline = true
        pill.isClickable = true
        pill.setOnTouchListener(PillTouchHandler(
            ctx,
            needsDoubleTap = { Gesture.DOUBLE_TAP.action(ctx) != GestureAction.NONE },
            onGesture = { IslandController.onGesture(it) },
        ))

        row.addView(leftSlot, LinearLayout.LayoutParams(0, ctx.dpi(36)))
        row.addView(pill, LinearLayout.LayoutParams(ctx.dpi(40), ctx.dpi(36)))
        row.addView(rightSlot, LinearLayout.LayoutParams(0, ctx.dpi(36)))

        collapsed.setPadding(ctx.dpi(6), 0, ctx.dpi(12), 0)
        pill.addView(collapsed, FrameLayout.LayoutParams(-1, -1))

        expandedHost.addView(mediaExp, FrameLayout.LayoutParams(-1, -2))
        expandedHost.addView(genericExp, FrameLayout.LayoutParams(-1, -2))
        expandedHost.visibility = View.GONE
        pill.addView(expandedHost, FrameLayout.LayoutParams(ctx.dpi(300), ctx.dpi(184), Gravity.CENTER))

        banner.visibility = View.GONE
        pill.addView(banner, FrameLayout.LayoutParams(ctx.dpi(300), ctx.dpi(68), Gravity.CENTER))

        // the pill grows from the left edge of the row's first slot; keep the row layout static
        row.layoutTransition = null
    }

    companion object {
        /** Damped spring: quick start, one small overshoot (~4%), then settles. */
        private val SPRING = android.animation.TimeInterpolator { t ->
            if (t >= 1f) 1f else (1.0 - Math.exp(-7.0 * t) * Math.cos(7.0 * t)).toFloat()
        }

        private const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
    }
}
