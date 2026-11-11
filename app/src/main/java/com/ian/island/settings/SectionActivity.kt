package com.ian.island.settings

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import com.ian.island.island.Icon

/** One settings screen per section; each section's rows live in its own file. */
class SectionActivity : Activity() {

    enum class Section(val title: String, val subtitle: String, val icon: Icon, val color: Int) {
        APPEARANCE("Appearance", "Shape, colours, position, double pill", Icon.PALETTE, Color.rgb(176, 96, 230)),
        MUSIC("Music", "Pause behaviour and player", Icon.MUSIC, Color.rgb(240, 80, 110)),
        LIVE("Live activities", "Calls, timers, navigation, per-app rules", Icon.BOLT, Color.rgb(240, 150, 20)),
        BATTERY("Battery & charging", "Charging animation and alerts", Icon.BATTERY, Color.rgb(46, 190, 100)),
        ALERTS("System alerts", "Wi-Fi, Bluetooth, volume, privacy dots", Icon.BELL, Color.rgb(20, 125, 240)),
        GESTURES("Gestures", "Tap, swipe and long-press actions", Icon.TOUCH, Color.rgb(60, 180, 210)),
        EVERYDAY("Visibility", "Schedule, lock screen, hide in apps", Icon.ALARM, Color.rgb(98, 96, 220)),
    }

    private lateinit var ui: UiKit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val section = Section.valueOf(intent.getStringExtra("section") ?: Section.APPEARANCE.name)
        ui = UiKit(this, section.title)
        when (section) {
            Section.APPEARANCE -> AppearanceScreen.build(ui)
            Section.MUSIC -> MusicScreen.build(ui)
            Section.LIVE -> LiveScreen.build(ui)
            Section.BATTERY -> BatteryScreen.build(ui)
            Section.ALERTS -> AlertsScreen.build(ui)
            Section.GESTURES -> GesturesScreen.build(ui)
            Section.EVERYDAY -> EverydayScreen.build(ui)
        }
        setContentView(ui.root)
    }

    override fun onResume() {
        super.onResume()
        ui.refresh()
    }
}
