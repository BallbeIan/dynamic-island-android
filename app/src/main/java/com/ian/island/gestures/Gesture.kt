package com.ian.island.gestures

import android.content.Context
import com.ian.island.settings.Prefs

enum class GestureAction(val id: String, val label: String) {
    NONE("none", "Do nothing"),
    EXPAND("expand", "Expand / collapse"),
    DISMISS("dismiss", "Collapse or dismiss"),
    OPEN_APP("open_app", "Open the activity's app"),
    PLAY_PAUSE("play_pause", "Play / pause"),
    NEXT("next", "Next track"),
    PREVIOUS("previous", "Previous track"),
    HOME("home", "Go home"),
    RECENTS("recents", "Recent apps"),
    BACK("back", "Back"),
    SCREENSHOT("screenshot", "Take a screenshot"),
    LOCK("lock", "Lock screen"),
    NOTIFICATIONS("notifications", "Open notification shade"),
    QUICK_SETTINGS("quick_settings", "Open Quick Settings"),
    TORCH("torch", "Toggle flashlight"),
    LAUNCH_APP("launch_app", "Launch an app…");

    /** Does nothing without an activity on screen, so it is not offered for the idle camera tap. */
    val needsActivity get() = this == EXPAND || this == DISMISS || this == OPEN_APP

    /** Needs the accessibility service. */
    val needsAccessibility get() = this == HOME || this == RECENTS || this == BACK || this == SCREENSHOT || this == LOCK

    companion object {
        fun from(id: String?) = values().firstOrNull { it.id == id }
    }
}

enum class Gesture(val key: String, val label: String, val default: GestureAction) {
    TAP("tap", "Tap", GestureAction.EXPAND),
    DOUBLE_TAP("double_tap", "Double tap", GestureAction.NONE),
    LONG_PRESS("long_press", "Long press", GestureAction.OPEN_APP),
    SWIPE_LEFT("swipe_left", "Swipe left", GestureAction.PREVIOUS),
    SWIPE_RIGHT("swipe_right", "Swipe right", GestureAction.NEXT),
    SWIPE_DOWN("swipe_down", "Swipe down", GestureAction.NOTIFICATIONS),
    SWIPE_UP("swipe_up", "Swipe up", GestureAction.DISMISS),
    /** Tapping where the camera is while the island has nothing to show (an invisible target sits over the camera). */
    CAMERA_TAP("camera_tap", "Tap the camera (island idle)", GestureAction.NONE);

    fun action(ctx: Context): GestureAction =
        GestureAction.from(Prefs.get(ctx).getString(Prefs.gestureKey(key), null)) ?: default
}
