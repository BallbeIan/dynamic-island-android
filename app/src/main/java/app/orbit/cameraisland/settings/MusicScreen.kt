package app.orbit.cameraisland.settings

object MusicScreen {
    fun build(ui: UiKit) {
        ui.section("Pause behaviour")
        ui.longChoice("Close island after pause", Prefs.PAUSE_TIMEOUT, Prefs.DEFAULT_PAUSE_TIMEOUT, listOf(
            30_000L to "30 seconds", 60_000L to "1 minute", 120_000L to "2 minutes",
            300_000L to "5 minutes", 600_000L to "10 minutes", -1L to "Never",
        ))
        ui.note("When music is paused the island stays for this long (even if the player app drops its media session), then animates away. Playing again brings it back.")
        ui.section("Controls")
        ui.note("Expand the island and drag the progress bar to seek. Tap the artwork or title to open the player app.")
    }
}
