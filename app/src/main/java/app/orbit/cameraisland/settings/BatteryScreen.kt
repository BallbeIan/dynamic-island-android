package app.orbit.cameraisland.settings

object BatteryScreen {
    fun build(ui: UiKit) {
        ui.section("Charging")
        ui.toggle("Charging animation", Prefs.CHARGE_ANIM, true, "Battery fill animation with percentage, charging speed (fast / wireless) and time to full when you plug in; a short \"unplugged\" animation when you unplug.")
        ui.toggle("Keep a battery indicator while charging", Prefs.CHARGE_INDICATOR, false, "A small pill stays on the island; tap to expand for percentage and time to full.")
        ui.section("Alerts")
        ui.toggle("Low battery alert", Prefs.LOW_ALERT, true, "Includes a button to open battery saver settings.")
        ui.slider("Low battery at", Prefs.LOW_LEVEL, 20, 11, 50, "%")
        ui.toggle("Critical battery alert", Prefs.CRIT_ALERT, true)
        ui.slider("Critical battery at", Prefs.CRIT_LEVEL, 10, 3, 15, "%")
        ui.toggle("Full charge alert", Prefs.FULL_ALERT, true)
        ui.slider("Charged target", Prefs.FULL_TARGET, 100, 50, 100, "%")
    }
}
