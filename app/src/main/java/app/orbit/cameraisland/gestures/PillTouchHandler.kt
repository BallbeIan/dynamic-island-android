package app.orbit.cameraisland.gestures

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.orbit.cameraisland.settings.Prefs
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.abs

/**
 * Turns raw touches on a pill into tap / double tap / long press / swipes.
 * Child views that handle their own touches (buttons, seek bars) never reach this.
 */
class PillTouchHandler(
    private val ctx: Context,
    private val needsDoubleTap: () -> Boolean,
    private val onGesture: (Gesture) -> Unit,
) : View.OnTouchListener {

    private val handler = Handler(Looper.getMainLooper())
    private val prefs = Prefs.get(ctx)
    /** The Tap action already ran on touch-down; the rest of this touch is ignored. */
    private var touchFired = false
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val swipeMin = slop * 3
    private val longMs = ViewConfiguration.getLongPressTimeout().toLong()
    private val doubleMs = 220L

    private var downX = 0f
    private var downY = 0f
    private var longFired = false
    private var pendingTap: Runnable? = null

    private val longRunnable = Runnable {
        longFired = true
        onGesture(Gesture.LONG_PRESS)
    }

    override fun onTouch(v: View, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY; longFired = false
                // optional: expand the instant the pill is touched (a swipe starting here will expand it too)
                touchFired = prefs.getBoolean(Prefs.TAP_ON_TOUCH, false) && !needsDoubleTap() &&
                    Gesture.TAP.action(ctx) == GestureAction.EXPAND
                if (touchFired) onGesture(Gesture.TAP) else handler.postDelayed(longRunnable, longMs)
                // instant acknowledgement of the touch: the pill dips under the finger
                v.animate().cancel()
                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70).setInterpolator(DecelerateInterpolator()).start()
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.rawX - downX) > slop || abs(e.rawY - downY) > slop) handler.removeCallbacks(longRunnable)
            }
            MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(longRunnable); release(v) }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longRunnable)
                release(v)
                if (touchFired || longFired) return true
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                if (maxOf(abs(dx), abs(dy)) > swipeMin) {
                    cancelPendingTap()
                    onGesture(
                        if (abs(dx) > abs(dy)) (if (dx < 0) Gesture.SWIPE_LEFT else Gesture.SWIPE_RIGHT)
                        else (if (dy > 0) Gesture.SWIPE_DOWN else Gesture.SWIPE_UP)
                    )
                } else {
                    tap()
                }
            }
        }
        return true
    }

    /** Springs the pill back out of its pressed state. */
    private fun release(v: View) {
        v.animate().cancel()
        v.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(OvershootInterpolator(2.2f)).start()
    }

    private fun tap() {
        if (!needsDoubleTap()) {
            onGesture(Gesture.TAP)
            return
        }
        if (pendingTap != null) {
            cancelPendingTap()
            onGesture(Gesture.DOUBLE_TAP)
        } else {
            val r = Runnable { pendingTap = null; onGesture(Gesture.TAP) }
            pendingTap = r
            handler.postDelayed(r, doubleMs)
        }
    }

    private fun cancelPendingTap() {
        pendingTap?.let { handler.removeCallbacks(it) }
        pendingTap = null
    }
}
