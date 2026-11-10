package com.ian.island.settings

import com.ian.island.alerts.Alerts
import com.ian.island.alerts.AlertType
import com.ian.island.alerts.SystemAlerts
import com.ian.island.island.AnimStyle
import com.ian.island.island.Icon

object AlertsScreen {
    fun build(ui: UiKit) {
        ui.note("Each alert appears briefly on the island. Choose which ones to show and how they look. Tap Preview to see one.")
        ui.section("Volume")
        ui.toggle("Replace the system volume panel", Prefs.VOLUME_REPLACE, false,
            "Volume keys adjust the volume silently and the island shows the slider. Needs the accessibility service; off = the island slider shows alongside the system panel.")

        val icons = Icon.values().filter { it.inPicker }.map { it.key to it.label }
        val anims = AnimStyle.values().map { it.name to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
        for (t in AlertType.values()) {
            ui.section(t.label)
            ui.toggle("Enabled", Prefs.alertKey(t.key, "enabled"), t.defEnabled)
            ui.choice("Icon", Prefs.alertKey(t.key, "icon"), t.defIcon.key, icons)
            ui.colors("Colour", Prefs.alertKey(t.key, "color"), t.defColor, Palette.ACCENT)
            ui.choice("Animation", Prefs.alertKey(t.key, "anim"), t.defAnim.name, anims)
            ui.button("Preview") { SystemAlerts.preview(ui.act, t) }
        }
    }
}
