package com.ian.island.settings

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ian.island.IslandAccessibilityService

/**
 * Every permission Island can use, with live status and a button to grant each one.
 * Shown as one collapsible card: expanded while something needs attention, collapsed once all is well.
 */
class SetupChecklist(private val ui: UiKit) {
    private val act: Activity = ui.act

    private class Item(
        val label: String,
        val why: String,
        val required: Boolean,
        val granted: () -> Boolean,
        val grant: () -> Unit,
        val show: () -> Boolean = { true },
    ) {
        lateinit var row: View
        lateinit var dot: View
        lateinit var grantBtn: TextView
    }

    companion object {
        const val REQ_RUNTIME = 41
        fun listenerEnabled(a: Activity): Boolean {
            val flat = Settings.Secure.getString(a.contentResolver, "enabled_notification_listeners") ?: return false
            return flat.split(":").any { ComponentName.unflattenFromString(it)?.packageName == a.packageName }
        }
        fun accessibilityEnabled(a: Activity): Boolean {
            if (IslandAccessibilityService.instance != null) return true
            val flat = Settings.Secure.getString(a.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return flat.split(":").any { ComponentName.unflattenFromString(it)?.packageName == a.packageName }
        }
    }

    private val items = ArrayList<Item>()
    private lateinit var headerDot: View
    private lateinit var headerTitle: TextView
    private lateinit var headerSummary: TextView
    private lateinit var headerChevron: ImageView
    private lateinit var list: LinearLayout
    private lateinit var restricted: View
    private var expanded = true
    private var userToggled = false

    private fun runtime(vararg perms: String) = { act.requestPermissions(perms, REQ_RUNTIME) }
    private fun has(p: String) = act.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private fun dot(color: Int, sizeDp: Int = 10) = View(act).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        layoutParams = LinearLayout.LayoutParams(ui.dpi(sizeDp), ui.dpi(sizeDp))
    }

    fun build() {
        items += Item("Notification access", "Media controls, notifications, calls, timers.", true,
            { listenerEnabled(act) }, { act.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        items += Item("Accessibility service", "Draws over the status bar, runs gestures, hides in apps and while the notification panel is open.", false,
            { accessibilityEnabled(act) }, { act.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        items += Item("Display over other apps", "Only needed if the accessibility service is off.", false,
            { Settings.canDrawOverlays(act) },
            { act.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${act.packageName}"))) })
        items += Item("Unrestricted battery", "Stops Android from killing Orbit in the background.", false,
            { act.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(act.packageName) },
            {
                try {
                    act.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${act.packageName}")))
                } catch (_: Exception) {
                    act.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            })
        items += Item("Bluetooth", "Show which Bluetooth device connected.", false,
            { has(Manifest.permission.BLUETOOTH_CONNECT) }, runtime(Manifest.permission.BLUETOOTH_CONNECT),
            show = { Build.VERSION.SDK_INT >= 31 })
        items += Item("Location", "Android only reveals the Wi-Fi name with it. Orbit never reads your location.", false,
            { has(Manifest.permission.ACCESS_FINE_LOCATION) },
            runtime(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        items += Item("Phone", "Answer or hang up through Android if a call notification has no button.", false,
            { has(Manifest.permission.ANSWER_PHONE_CALLS) }, runtime(Manifest.permission.ANSWER_PHONE_CALLS))

        // header row (tap to expand / collapse)
        headerDot = dot(Ui.WARN, 12)
        headerTitle = Ui.text(act, 16f, Ui.TEXT, medium = true).apply { text = "Permissions" }
        headerSummary = Ui.text(act, 13f, Ui.TEXT2)
        headerChevron = Ui.chevron(act)
        val texts = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerTitle); addView(headerSummary)
        }
        val header = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dpi(64)
            setPadding(ui.dpi(16), ui.dpi(10), ui.dpi(12), ui.dpi(10))
            background = Ui.ripple(null)
            isClickable = true
            addView(headerDot)
            addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dpi(14) })
            addView(headerChevron)
            setOnClickListener { userToggled = true; expanded = !expanded; applyExpanded() }
        }
        ui.addRow(header)

        list = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        for (it in items) {
            if (!it.show()) continue
            list.addView(View(act).apply { setBackgroundColor(Ui.DIVIDER) },
                LinearLayout.LayoutParams(-1, 1).apply { marginStart = ui.dpi(16) })
            it.dot = dot(Ui.TEXT2)
            val label = Ui.text(act, 15f, Ui.TEXT).apply { text = it.label + if (it.required) "" else "  ·  optional" }
            val why = Ui.text(act, 12f, Ui.TEXT2).apply { text = it.why; setPadding(0, ui.dpi(2), 0, 0) }
            it.grantBtn = Ui.text(act, 13f, Ui.ACCENT, medium = true).apply {
                text = "Grant"
                gravity = Gravity.CENTER
                setPadding(ui.dpi(14), ui.dpi(7), ui.dpi(14), ui.dpi(7))
                background = GradientDrawable().apply {
                    cornerRadius = Ui.dp(act, 20f); setColor(Color.argb(30, 124, 140, 255)); setStroke(1, Ui.ACCENT)
                }
                setOnClickListener { _ -> it.grant() }
            }
            val texts2 = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; addView(label); addView(why) }
            it.row = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dpi(16), ui.dpi(10), ui.dpi(14), ui.dpi(10))
                addView(it.dot)
                addView(texts2, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dpi(14); marginEnd = ui.dpi(10) })
                addView(it.grantBtn)
            }
            list.addView(it.row)
        }

        restricted = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dpi(16), ui.dpi(12), ui.dpi(16), ui.dpi(14))
            setBackgroundColor(Color.argb(24, 251, 191, 36))
            addView(Ui.text(act, 13f, Ui.WARN).apply {
                text = "Notification access or Accessibility greyed out (\"Restricted setting\")? Open App info → ⋮ menu → Allow restricted settings, then try again."
                setLineSpacing(0f, 1.15f)
            })
            addView(Ui.text(act, 14f, Ui.ACCENT, medium = true).apply {
                text = "Open app info"
                setPadding(0, ui.dpi(10), 0, 0)
                setOnClickListener {
                    act.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${act.packageName}")))
                }
            })
        }
        list.addView(restricted)
        ui.addRow(list, divider = true)
    }

    private fun applyExpanded() {
        list.visibility = if (expanded) View.VISIBLE else View.GONE
        headerChevron.rotation = if (expanded) 90f else 0f
    }

    fun refresh() {
        var requiredOk = true
        var allOk = true
        for (it in items) {
            if (!it.show()) continue
            val g = it.granted()
            (it.dot.background as GradientDrawable).setColor(if (g) Ui.OK else if (it.required) Ui.BAD else Ui.TEXT2)
            it.grantBtn.visibility = if (g) View.GONE else View.VISIBLE
            if (it.required && !g) requiredOk = false
        }
        val a11y = accessibilityEnabled(act)
        val overlayOk = a11y || Settings.canDrawOverlays(act)
        allOk = requiredOk && overlayOk
        val needHelp = !listenerEnabled(act) || !a11y
        restricted.visibility = if (needHelp) View.VISIBLE else View.GONE

        val (title, color, summary) = when {
            !requiredOk -> Triple("Setup needed", Ui.BAD, "Grant notification access to start")
            !overlayOk -> Triple("Almost there", Ui.WARN, "Enable the accessibility service so the island can appear")
            else -> Triple("All set", Ui.OK, "Orbit is ready — play something")
        }
        headerTitle.text = title
        headerSummary.text = summary
        (headerDot.background as GradientDrawable).setColor(color)
        if (!userToggled) expanded = !allOk
        applyExpanded()
    }
}
