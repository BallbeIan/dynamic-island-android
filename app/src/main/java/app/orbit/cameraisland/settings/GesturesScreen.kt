package app.orbit.cameraisland.settings

import app.orbit.cameraisland.gestures.Gesture
import app.orbit.cameraisland.gestures.GestureAction

object GesturesScreen {
    private fun appLabel(ui: UiKit, pkg: String?): String {
        if (pkg == null) return "Not chosen"
        return try { ui.act.packageManager.getApplicationLabel(ui.act.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { "Not installed" }
    }

    fun build(ui: UiKit) {
        ui.note("Assign an action to every gesture on the island. Gestures never interfere with the buttons and sliders inside the expanded island. Actions marked * need the accessibility service. Note: giving double tap an action makes single taps wait ~0.2 s to tell them apart, so it is off by default.")
        ui.section("Responsiveness")
        ui.toggle("Expand on touch", Prefs.TAP_ON_TOUCH, false,
            "Runs the Tap action the moment you touch the island instead of when you lift your finger, so it feels faster. Only applies when Tap is \"Expand / collapse\" and double tap is off. A swipe or long press that starts on the island will expand it too.")
        val options = GestureAction.values().map { it.id to (it.label + if (it.needsAccessibility) " *" else "") }
        for (g in Gesture.values()) {
            ui.section(g.label)
            if (g == Gesture.CAMERA_TAP) {
                ui.note("When the island has nothing to show, an invisible target sits over your camera. Tap it to run this action. While an action is set, that spot on the screen no longer passes touches through (for example a swipe down from there). Off by default.")
            }
            // expand / collapse / open-app mean nothing when there is no activity on screen
            val actions = if (g == Gesture.CAMERA_TAP) GestureAction.values().filter { !it.needsActivity }
                .map { it.id to (it.label + if (it.needsAccessibility) " *" else "") } else options
            ui.choice("Action", Prefs.gestureKey(g.key), g.default.id, actions) { chosen ->
                if (chosen == GestureAction.LAUNCH_APP.id) {
                    AppListActivity.open(ui.act, "single", Prefs.gestureAppKey(g.key), "App for ${g.label.lowercase()}")
                }
            }
            ui.valueRow("App to launch", "Used when the action is \"Launch an app\"",
                { appLabel(ui, Prefs.get(ui.act).getString(Prefs.gestureAppKey(g.key), null)) }) {
                AppListActivity.open(ui.act, "single", Prefs.gestureAppKey(g.key), "App for ${g.label.lowercase()}")
            }
        }
        ui.spacer()
        ui.note("Defaults: tap = expand, double tap = nothing, long press = open app, swipe left/right = previous/next track, swipe down = notification shade, swipe up = collapse/dismiss, tap on the camera = nothing. Tap outside the expanded island to collapse it.")
    }
}
