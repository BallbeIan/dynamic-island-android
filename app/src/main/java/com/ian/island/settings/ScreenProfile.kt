package com.ian.island.settings

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min

/**
 * Identifies the physical screen the island is on, so a foldable can keep separate position / size / colour
 * settings for each of its screens. On a phone with a single screen nothing is scoped.
 */
object ScreenProfile {

    /** True for devices with more than one built-in screen (foldables, flips). */
    @Volatile private var foldableCache: Boolean? = null

    fun foldable(ctx: Context): Boolean = foldableCache ?: computeFoldable(ctx).also { foldableCache = it }

    private fun computeFoldable(ctx: Context): Boolean {
        if (ctx.packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle")) return true
        return try {
            ctx.getSystemService(DisplayManager::class.java).displays.count { it.name == "Built-in Screen" } >= 2
        } catch (_: Throwable) { false }
    }

    private fun bounds(ctx: Context): Rect {
        val wm = ctx.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds
        else Rect(0, 0, ctx.resources.displayMetrics.widthPixels, ctx.resources.displayMetrics.heightPixels)
    }

    /** "1848x2448": the screen's size independent of rotation; changes between a foldable's screens. */
    fun key(ctx: Context): String {
        val b = bounds(ctx)
        return "${min(b.width(), b.height())}x${max(b.width(), b.height())}"
    }

    /** The key to scope settings with, or null when settings are shared (not a foldable). */
    fun scopeOrNull(ctx: Context): String? = if (foldable(ctx)) key(ctx) else null

    /** A friendly name for the screen the given context is on. */
    fun label(ctx: Context): String {
        if (!foldable(ctx)) return "This screen"
        val b = bounds(ctx)
        return if (min(b.width(), b.height()) >= 1500) "Inner screen" else "Cover screen"
    }
}

/**
 * Preferences with per-screen overrides: for the keys in [SCOPED], a value saved for this screen wins, then the
 * shared value (so existing settings carry over), then the default. Writes go to the screen's own key.
 */
class ScreenPrefs(private val prefs: SharedPreferences, private val scope: () -> String?) {

    companion object {
        /** Settings that describe how the island looks and where it sits on a particular screen. */
        val SCOPED = setOf(
            Prefs.OFFSET, Prefs.XOFFSET, Prefs.WIDTH, Prefs.PILL_HEIGHT, Prefs.CORNER, Prefs.BG_COLOR, Prefs.BG_ALPHA,
            Prefs.OUTLINE, Prefs.GLOW, Prefs.WALLPAPER_ACCENT, Prefs.DOUBLE_PILL, Prefs.DOUBLE_SIDE, Prefs.DOUBLE_GAP,
            Prefs.AUTO_CAMERA,
        )

        /** "pill_height_dp@1848x2448" -> "pill_height_dp" */
        fun baseKey(key: String?): String? = key?.substringBefore('@')
    }

    private var cachedScope: String? = null
    private val keys = HashMap<String, String>()

    /** The key a value for [base] is stored under on the current screen (cached: this runs per animation frame). */
    fun key(base: String): String {
        val s = scope()
        if (base !in SCOPED || s == null) return base
        if (s != cachedScope) { keys.clear(); cachedScope = s }
        return keys.getOrPut(base) { "$base@$s" }
    }

    private fun readKey(base: String): String {
        val k = key(base)
        return if (k != base && !prefs.contains(k)) base else k
    }

    fun int(base: String, def: Int) = prefs.getInt(readKey(base), def)
    fun bool(base: String, def: Boolean) = prefs.getBoolean(readKey(base), def)
    fun str(base: String, def: String?) = prefs.getString(readKey(base), def)
}
