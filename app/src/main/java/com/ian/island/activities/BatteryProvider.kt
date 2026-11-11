package com.ian.island.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import com.ian.island.island.ActionButton
import com.ian.island.island.ActivityType
import com.ian.island.island.AnimStyle
import com.ian.island.island.BatteryDrawable
import com.ian.island.island.GREEN
import com.ian.island.island.Intents
import com.ian.island.island.IslandActivity
import com.ian.island.island.IslandController
import com.ian.island.island.IslandModule
import com.ian.island.island.ORANGE
import com.ian.island.island.Presentation
import com.ian.island.island.RED
import com.ian.island.island.Trailing
import com.ian.island.settings.Prefs
import kotlin.math.abs

/** Charging animation, unplug, low / critical battery and full-charge alerts. */
class BatteryProvider(private val ctx: Context) : IslandModule {

    private val prefs = Prefs.get(ctx)
    private val bm = ctx.getSystemService(BatteryManager::class.java)

    private var first = true
    private var plugged = false
    private var level = -1
    private var lowFired = false
    private var critFired = false
    private var fullFired = false
    private var lastPlugType = 0

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = handle(i)
    }

    override fun start() {
        val sticky = Intents.register(ctx, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) handle(sticky)
    }

    override fun stop() {
        try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {}
        IslandController.removeWhere { it.id.startsWith("battery:") }
    }

    override fun onPrefChanged(key: String?) {
        if (key == Prefs.CHARGE_INDICATOR) updateIndicator()
    }

    private fun handle(i: Intent) {
        val raw = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (raw < 0 || scale <= 0) return
        val newLevel = raw * 100 / scale
        val plugType = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val nowPlugged = plugType != 0
        val wasPlugged = plugged
        plugged = nowPlugged
        level = newLevel
        if (nowPlugged) lastPlugType = plugType

        if (!first) {
            if (nowPlugged && !wasPlugged) onPlugged()
            if (!nowPlugged && wasPlugged) onUnplugged()
        }
        checkThresholds()
        updateIndicator()
        first = false
    }

    // ---- events ----

    private fun openSaver() = Intents.start(ctx, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))

    /** "Fast charging · 25 min to full". */
    private fun chargeInfo() = listOfNotNull(speedLabel(), eta()).joinToString(" · ")

    private fun speedLabel(): String {
        if (lastPlugType == BatteryManager.BATTERY_PLUGGED_WIRELESS) return "Wireless charging"
        val ua = try { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) } catch (_: Exception) { 0 }
        return if (abs(ua) >= 2_000_000) "Fast charging" else "Charging"
    }

    private fun eta(): String? {
        if (Build.VERSION.SDK_INT < 28) return null
        val ms = try { bm.computeChargeTimeRemaining() } catch (_: Exception) { -1L }
        if (ms <= 0) return null
        val min = (ms / 60000).toInt()
        return if (min >= 60) "${min / 60} h ${min % 60} min to full" else "$min min to full"
    }

    private fun onPlugged() {
        if (!prefs.getBoolean(Prefs.CHARGE_ANIM, true)) return
        val a = IslandActivity("battery:event", ActivityType.ALERT)
        val lv = level
        a.title = "$lv%"
        a.subtitle = chargeInfo()
        a.accent = GREEN
        a.icon = { BatteryDrawable(lv, true, GREEN) }
        a.transientMs = 3800
        a.presentation = Presentation.BANNER
        a.anim = AnimStyle.POP
        a.expandable = false
        a.onOpen = { openSaver() }
        IslandController.post(a, announce = true)
    }

    private fun onUnplugged() {
        IslandController.remove("battery:charging")
        if (!prefs.getBoolean(Prefs.CHARGE_ANIM, true)) return
        val a = IslandActivity("battery:event", ActivityType.ALERT)
        val lv = level
        a.title = "Unplugged"
        a.subtitle = "$lv% remaining"
        a.accent = Color.WHITE
        a.icon = { BatteryDrawable(lv, false, if (lv <= 20) RED else Color.WHITE) }
        a.transientMs = 2600
        a.presentation = Presentation.BANNER
        a.anim = AnimStyle.SLIDE
        a.expandable = false
        IslandController.post(a, announce = true)
    }

    private fun checkThresholds() {
        val lv = level
        val low = prefs.getInt(Prefs.LOW_LEVEL, 20)
        val crit = prefs.getInt(Prefs.CRIT_LEVEL, 10)
        if (plugged || lv > low) lowFired = false
        if (plugged || lv > crit) critFired = false

        if (!plugged) {
            if (prefs.getBoolean(Prefs.CRIT_ALERT, true) && lv <= crit && !critFired) {
                critFired = true; lowFired = true
                lowAlert("Battery critical", RED, 10_000)
            } else if (prefs.getBoolean(Prefs.LOW_ALERT, true) && lv <= low && !lowFired && lv > crit) {
                lowFired = true
                lowAlert("Battery low", ORANGE, 8_000)
            }
        }

        val target = prefs.getInt(Prefs.FULL_TARGET, 100)
        if (!plugged || lv < target - 2) fullFired = false
        if (plugged && prefs.getBoolean(Prefs.FULL_ALERT, true) && lv >= target && !fullFired) {
            fullFired = true
            val a = IslandActivity("battery:full", ActivityType.ALERT)
            a.title = if (target >= 100) "Fully charged" else "Charged to $target%"
            a.subtitle = "You can unplug"
            a.accent = GREEN
            a.icon = { BatteryDrawable(lv, false, GREEN) }
            a.transientMs = 5000
            a.presentation = Presentation.BANNER
            a.anim = AnimStyle.POP
            a.expandable = false
            IslandController.post(a, announce = true)
        }
    }

    private fun lowAlert(title: String, color: Int, ms: Long) {
        val lv = level
        val a = IslandActivity("battery:low", ActivityType.BATTERY_ALERT)
        a.title = title
        a.subtitle = "$lv% remaining"
        a.accent = color
        a.icon = { BatteryDrawable(lv, false, color) }
        a.trailing = Trailing.Label("$lv%")
        a.transientMs = ms
        a.presentation = Presentation.EXPANDED
        a.anim = AnimStyle.PULSE
        a.buttons = listOf(ActionButton("Battery saver", color) { openSaver() })
        a.onOpen = { openSaver() }
        IslandController.post(a, announce = true)
    }

    // ---- persistent charging indicator ----

    private fun updateIndicator() {
        if (!plugged || !prefs.getBoolean(Prefs.CHARGE_INDICATOR, false)) {
            IslandController.remove("battery:charging"); return
        }
        val lv = level
        val a = IslandActivity("battery:charging", ActivityType.CHARGING)
        a.title = "$lv%"
        a.subtitle = chargeInfo()
        a.accent = GREEN
        a.icon = { BatteryDrawable(lv, true, GREEN) }
        a.trailing = Trailing.Label("$lv%")
        a.progress = lv / 100f
        a.onOpen = { openSaver() }
        IslandController.post(a, announce = false)
    }
}
