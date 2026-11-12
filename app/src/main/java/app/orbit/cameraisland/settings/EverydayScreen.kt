package app.orbit.cameraisland.settings

object EverydayScreen {
    fun build(ui: UiKit) {
        ui.section("When the island appears")
        ui.choice("Show", Prefs.SHOW_MODE, "activity", listOf(
            "activity" to "Only when there's an activity", "always" to "Always (idle pill when nothing is happening)"))
        ui.toggle("Only during active hours", Prefs.SCHEDULE_ON, false)
        ui.time("From", Prefs.SCHEDULE_START, 7 * 60)
        ui.time("Until", Prefs.SCHEDULE_END, 23 * 60)
        ui.toggle("Hide on the lock screen", Prefs.HIDE_LOCK, false)

        ui.section("Hide automatically")
        ui.toggle("Show in full screen", Prefs.SHOW_IN_FULLSCREEN, false,
            "Off (default): the island hides while an app is full screen, e.g. a video or a game, and returns when you leave it. Needs the accessibility service.")
        ui.toggle("Hide in landscape", Prefs.HIDE_LANDSCAPE, false)
        ui.valueRow("Hide in selected apps", "Video players, games…",
            { val n = ui.prefs.getStringSet(Prefs.HIDE_APPS, emptySet())?.size ?: 0; if (n == 0) "None" else "$n apps" }) {
            AppListActivity.open(ui.act, "multi", Prefs.HIDE_APPS, "Hide island in these apps",
                "Add video players, games and other full-screen apps here. Tap to toggle. Needs the accessibility service to know which app is in front.")
        }

        ui.section("Quick Settings tile")
        ui.note("Add the \"Orbit\" tile from the Quick Settings editor: tap it to switch the island on/off, long-press it to open these settings.")

        ui.section("Always-on display")
        ui.note("Android doesn't let apps draw windows while the screen is in always-on / doze mode, so the island can't appear on the AOD. It does show on the lock screen (unless hidden above) when the accessibility service is on.")
    }
}
