package com.ian.island.alerts

import android.content.Context
import android.graphics.Color
import com.ian.island.island.ActivityType
import com.ian.island.island.AnimStyle
import com.ian.island.island.BLUE
import com.ian.island.island.GREEN
import com.ian.island.island.Icon
import com.ian.island.island.IconDrawable
import com.ian.island.island.IslandActivity
import com.ian.island.island.IslandController
import com.ian.island.island.ORANGE
import com.ian.island.island.PURPLE
import com.ian.island.island.Presentation
import com.ian.island.island.RED
import com.ian.island.island.Trailing
import com.ian.island.island.YELLOW
import com.ian.island.settings.Prefs

/** Every system alert: default icon / colour / animation, all overridable in settings. */
enum class AlertType(
    val key: String, val label: String, val defIcon: Icon, val defColor: Int, val defAnim: AnimStyle,
    val defEnabled: Boolean = true,
) {
    WIFI("wifi", "Wi-Fi connected / disconnected", Icon.WIFI, BLUE, AnimStyle.POP),
    BLUETOOTH("bluetooth", "Bluetooth device connected / disconnected", Icon.BLUETOOTH, BLUE, AnimStyle.POP),
    HOTSPOT("hotspot", "Hotspot on / off", Icon.HOTSPOT, GREEN, AnimStyle.PULSE),
    VPN("vpn", "VPN connected / disconnected", Icon.VPN, GREEN, AnimStyle.SLIDE),
    ALARM_SET("alarm_set", "Next alarm set / changed", Icon.ALARM, YELLOW, AnimStyle.POP),
    VOLUME("volume", "Volume slider", Icon.VOLUME, Color.WHITE, AnimStyle.SLIDE),
    RINGER("ringer", "Ringer mode (ring / vibrate / silent)", Icon.BELL, ORANGE, AnimStyle.POP),
    DND("dnd", "Do Not Disturb on / off", Icon.DND, PURPLE, AnimStyle.POP),
    SCREEN_REC("screen_rec", "Screen recording (red dot + timer)", Icon.RECORD, RED, AnimStyle.PULSE),
    MIC("mic", "Microphone in use (orange dot)", Icon.MIC, ORANGE, AnimStyle.PULSE),
    CAMERA("camera", "Camera in use (green dot)", Icon.CAMERA, GREEN, AnimStyle.PULSE),
    AIRPLANE("airplane", "Airplane mode", Icon.AIRPLANE, Color.WHITE, AnimStyle.SLIDE, defEnabled = false),
    NFC("nfc", "NFC on / off", Icon.NFC, BLUE, AnimStyle.POP, defEnabled = false),
    MOBILE_DATA("mobile_data", "Mobile data connected / lost", Icon.DATA, GREEN, AnimStyle.POP, defEnabled = false),
    FLASHLIGHT("flashlight", "Flashlight on / off", Icon.FLASHLIGHT, YELLOW, AnimStyle.POP, defEnabled = false);
}

/** Builds and posts alert / indicator activities using the user's per-alert style. */
object Alerts {

    fun enabled(ctx: Context, t: AlertType) = Prefs.get(ctx).getBoolean(Prefs.alertKey(t.key, "enabled"), t.defEnabled)

    fun icon(ctx: Context, t: AlertType, smart: Icon? = null): Icon {
        val chosen = Prefs.get(ctx).getString(Prefs.alertKey(t.key, "icon"), null)
        return if (chosen != null) Icon.from(chosen, t.defIcon) else smart ?: t.defIcon
    }

    fun color(ctx: Context, t: AlertType) = Prefs.get(ctx).getInt(Prefs.alertKey(t.key, "color"), t.defColor)

    fun anim(ctx: Context, t: AlertType) = AnimStyle.from(Prefs.get(ctx).getString(Prefs.alertKey(t.key, "anim"), t.defAnim.name))

    /** A short banner. [force] bypasses the enable toggle (settings preview). */
    fun flash(
        ctx: Context, t: AlertType, title: String, subtitle: String = "", smartIcon: Icon? = null,
        id: String = "alert:${t.key}", ms: Long = 2800, force: Boolean = false,
    ) {
        if (!force && !enabled(ctx, t)) return
        val ic = icon(ctx, t, smartIcon)
        val col = color(ctx, t)
        val a = IslandActivity(id, ActivityType.ALERT)
        a.title = title
        a.subtitle = subtitle
        a.accent = col
        a.icon = { IconDrawable(ic, col) }
        a.transientMs = ms
        a.presentation = Presentation.BANNER
        a.anim = anim(ctx, t)
        a.expandable = false
        IslandController.post(a, announce = true)
    }

    /** A persistent indicator (recording / mic / camera) shown as a coloured dot and, when alone, a pill. */
    fun indicator(
        ctx: Context, t: AlertType, active: Boolean, title: String = "", subtitle: String = "",
        timerBase: Long = 0,
    ) {
        val id = "ind:${t.key}"
        if (!active || !enabled(ctx, t)) { IslandController.remove(id); return }
        val ic = icon(ctx, t)
        val col = color(ctx, t)
        val a = IslandActivity(id, ActivityType.INDICATOR)
        a.title = title
        a.subtitle = subtitle
        a.accent = col
        a.icon = { IconDrawable(ic, col) }
        a.trailing = if (timerBase > 0) Trailing.Timer(timerBase, false) else Trailing.Dot(col)
        a.anim = anim(ctx, t)
        val first = IslandController.get(id) == null
        a.presentation = Presentation.BANNER
        IslandController.post(a, announce = first)
    }
}
