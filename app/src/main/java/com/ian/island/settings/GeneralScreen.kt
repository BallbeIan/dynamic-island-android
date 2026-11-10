package com.ian.island.settings

object GeneralScreen {
    fun build(ui: UiKit) {
        ui.section("Notifications")
        ui.toggle("Show notifications in the island", Prefs.PEEK, true)
        ui.note("Position, size and colours are under Appearance. Per-app behaviour is under Live activities.")
    }
}
