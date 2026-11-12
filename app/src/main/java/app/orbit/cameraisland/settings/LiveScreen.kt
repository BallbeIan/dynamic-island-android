package app.orbit.cameraisland.settings

object LiveScreen {
    fun build(ui: UiKit) {
        ui.section("Live activities")
        ui.note("Ongoing and important notifications become live activities with working buttons. Tap to expand and use them; tap the header to open the app.")
        ui.toggle("Calls", Prefs.LIVE_CALLS, true, "Caller, Answer / Decline, call timer, mute, speaker, hang up.")
        ui.toggle("Timers & stopwatches", Prefs.LIVE_TIMERS, true, "Live-counting timer in the pill, with pause / stop buttons.")
        ui.toggle("Navigation", Prefs.LIVE_NAV, true, "Next turn, distance and instruction from Maps and similar apps.")
        ui.toggle("Progress, rides, deliveries, downloads", Prefs.LIVE_PROGRESS, true, "Progress ring in the pill; Android 16 Live Updates are used when present.")
        ui.toggle("Alarm ringing", Prefs.LIVE_ALARM, true, "Snooze and Dismiss straight from the island.")
        ui.toggle("Other ongoing notifications (apps set to Live)", Prefs.LIVE_OTHER, true)
        ui.section("Notification peeks")
        ui.toggle("Show notifications in the island", Prefs.PEEK, true, "Messages can be answered inline when the app supports quick reply.")
        ui.section("Per app")
        ui.valueRow("Per-app behaviour", "Auto, peek only, live activity or off",
            { val n = ui.prefs.all.keys.count { it.startsWith(Prefs.APP_MODE_PREFIX) }; if (n == 0) "All auto" else "$n customised" }) {
            AppListActivity.open(ui.act, "modes", "", "Apps",
                "Auto: smart · Peek: short banner only · Live: stays interactive · Off: never shown")
        }
    }
}
