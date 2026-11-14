package app.orbit.cameraisland.settings

import android.content.Context
import android.content.SharedPreferences

/** All preference keys and defaults in one place. */
object Prefs {
    const val NAME = "island_prefs"

    // ---- Core ----
    const val ENABLED = "enabled"
    const val PEEK = "notification_peek"
    const val OFFSET = "y_offset_dp"
    const val XOFFSET = "x_offset_dp"
    const val WIDTH = "collapsed_width_dp"
    const val DEFAULT_OFFSET = 10
    const val DEFAULT_WIDTH = 190

    // ---- Music ----
    const val PAUSE_TIMEOUT = "pause_timeout_ms"   // -1 = never
    const val DEFAULT_PAUSE_TIMEOUT = 60_000L

    // ---- Appearance ----
    const val PILL_HEIGHT = "pill_height_dp"
    const val CORNER = "corner_pct"
    const val BG_COLOR = "bg_color"
    const val BG_ALPHA = "bg_alpha_pct"
    const val OUTLINE = "outline_mode"             // none | accent
    const val GLOW = "glow"
    const val WALLPAPER_ACCENT = "wallpaper_accent"
    const val DOUBLE_PILL = "double_pill"
    const val DOUBLE_SIDE = "double_side"          // left | right
    const val DOUBLE_GAP = "double_gap_dp"
    const val AUTO_CAMERA = "auto_camera"          // align the island to the detected camera position

    // ---- Everyday ----
    const val SHOW_MODE = "show_mode"              // activity | always
    const val SCHEDULE_ON = "schedule_on"
    const val SCHEDULE_START = "schedule_start_min"
    const val SCHEDULE_END = "schedule_end_min"
    const val HIDE_LOCK = "hide_on_lock"
    const val HIDE_APPS = "hide_apps"              // string set
    const val HIDE_LANDSCAPE = "hide_landscape"
    const val SHOW_IN_FULLSCREEN = "show_in_fullscreen"   // default off: the island hides while an app is full screen

    // ---- Live activities ----
    const val LIVE_CALLS = "live_calls"
    const val LIVE_TIMERS = "live_timers"
    const val LIVE_NAV = "live_nav"
    const val LIVE_PROGRESS = "live_progress"
    const val LIVE_ALARM = "live_alarm"
    const val LIVE_OTHER = "live_other"
    const val APP_MODE_PREFIX = "appmode_"         // + package -> auto | peek | live | off

    // ---- Battery ----
    const val CHARGE_ANIM = "charge_anim"
    const val CHARGE_INDICATOR = "charge_indicator"
    const val LOW_ALERT = "low_alert"
    const val LOW_LEVEL = "low_level"
    const val CRIT_ALERT = "crit_alert"
    const val CRIT_LEVEL = "crit_level"
    const val FULL_ALERT = "full_alert"
    const val FULL_TARGET = "full_target"

    const val THEME = "app_theme"   // "light" / "dark"; unset = follow the system

    // ---- System alerts ----
    const val VOLUME_REPLACE = "volume_replace_panel"
    fun alertKey(type: String, field: String) = "alert_${type}_$field"

    // ---- Gestures ----
    const val TAP_ON_TOUCH = "tap_on_touch"        // run the Tap action on touch-down instead of on release
    fun gestureKey(g: String) = "gesture_$g"
    fun gestureAppKey(g: String) = "gesture_${g}_app"

    fun get(c: Context): SharedPreferences = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
